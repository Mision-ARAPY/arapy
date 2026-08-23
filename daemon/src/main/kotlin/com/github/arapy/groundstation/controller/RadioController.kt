package com.github.arapy.groundstation.controller

import com.github.arapy.groundstation.asByteFlow
import com.github.arapy.groundstation.demodulator.RadioDemodulator
import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Esquema de demodulación de rtl_fm y el sample rate que necesita para no aliasear. */
enum class TunerMode(val rtlFmMode: String, val sampleRate: Int) {
    /** FM angosta: repetidores de voz/paquetes de radioaficionado (lo que usan los modos "FM"/"AFSK" de SatNOGS). */
    FM("fm", 48000),
    /** FM ancha: radio comercial (88-108MHz, ~200kHz de desviación). */
    WBFM("wbfm", 200000),
}

class RadioController(
    private val scope: CoroutineScope,
) {
    // Serializa listen()/stop(): sin esto, un listen() mientras el anterior todavía está
    // liberando el dongle (p. ej. el retune automático por corrección Doppler) hace que el
    // proceso rtl_fm nuevo intente abrir el RTL-SDR antes de que el viejo lo suelte de verdad —
    // "usb_claim_interface error -6" / "Failed to open rtlsdr device".
    private val mutex = Mutex()
    private var demodJob: Job? = null
    private var currentProcess: Process? = null

    private val _signals = MutableSharedFlow<DecodedSignal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signals = _signals.asSharedFlow()

    fun listen(
        frequency: Long,
        demodulator: RadioDemodulator,
        tunerMode: TunerMode = TunerMode.FM,
    ) {
        scope.launch {
            mutex.withLock {
                stopLocked()

                val rtlFm = ProcessBuilder(
                    "rtl_fm",
                    "-f", frequency.toString(),
                    "-M", tunerMode.rtlFmMode,
                    "-s", tunerMode.sampleRate.toString(),
                    "-r", "48000",
                )
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                currentProcess = rtlFm

                demodJob = launch {
                    val samples = rtlFm.inputStream.asByteFlow()
                    demodulator
                        .demodulate(samples)
                        .collect { signal ->
                            _signals.emit(signal)
                        }
                }
            }
        }
    }

    fun stop() {
        scope.launch { mutex.withLock { stopLocked() } }
    }

    /** Debe llamarse con [mutex] tomado. Corta el demodulador y espera a que rtl_fm suelte el dongle de verdad. */
    private suspend fun stopLocked() {
        demodJob?.cancelAndJoin()
        demodJob = null

        currentProcess?.let { process ->
            process.destroy()
            // destroy() solo manda la señal; sin este waitFor el próximo listen() puede
            // arrancar mientras el dongle todavía está siendo liberado por el proceso viejo.
            withContext(Dispatchers.IO) { process.waitFor() }
        }
        currentProcess = null
    }
}