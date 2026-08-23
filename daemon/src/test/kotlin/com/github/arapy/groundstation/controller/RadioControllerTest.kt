package com.github.arapy.groundstation.controller

import com.github.arapy.groundstation.connection.RelayConnection
import com.github.arapy.groundstation.connection.client
import com.github.arapy.groundstation.connection.sendPayload
import com.github.arapy.groundstation.demodulator.FmDemodulator
import com.github.arapy.groundstation.identity.StationIdentity
import com.github.arapy.groundstation.identity.StationIdentityStore
import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import com.parodison.orbit.core.protocol.WebsocketPayload
import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RadioControllerTest {

    @Test
    fun `escuchar 100_9 FM via RadioController y recibir DecodedSignal Audio`() = runTest {
        val controller = RadioController(scope = backgroundScope)

        controller.listen(frequency = 100_900_000, demodulator = FmDemodulator(), tunerMode = TunerMode.WBFM)

        val signals = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(15.seconds) {
                controller.signals.take(5).toList()
            }
        }

        assertTrue(signals.isNotEmpty(), "Debería haber recibido al menos un DecodedSignal")
        signals.forEach { signal ->
            assertTrue(signal is DecodedSignal.Audio, "Se esperaba DecodedSignal.Audio, llegó: $signal")
        }
    }

    @Test
    fun `escuchar 100_9 FM y reproducir el audio por los parlantes durante 20 segundos`() = runTest {
        val controller = RadioController(scope = backgroundScope)
        controller.listen(frequency = 100_900_000, demodulator = FmDemodulator(), tunerMode = TunerMode.WBFM)

        val format = AudioFormat(48000f, 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeoutOrNull(20.seconds) {
                    controller.signals
                        .filterIsInstance<DecodedSignal.Audio>()
                        .collect { signal -> line.write(signal.pcmBytes, 0, signal.pcmBytes.size) }
                }
            }
        } finally {
            line.drain()
            line.stop()
            line.close()
        }
    }

    @Test
    fun `sintonizar una frecuencia FM y enviarla por websocket`() = runTest {
        val identity = StationIdentityStore.getOrCreate()
        client.webSocket("ws://10.154.42.197:8081/ws/stations") {
            sendPayload(WebsocketPayload.StationHandshake(identity.id, identity.name))

            val controller = RadioController(scope = backgroundScope)
            controller.listen(100_900_000, demodulator = FmDemodulator(), tunerMode = TunerMode.WBFM)

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeoutOrNull(1.minutes) {
                    controller.signals
                        .filterIsInstance<DecodedSignal.Audio>()
                        .collect { signal ->
                            sendPayload(WebsocketPayload.DecodedSignalReceived(stationId = identity.id, signal = signal))
                        }
                }
            }

        }
    }
}