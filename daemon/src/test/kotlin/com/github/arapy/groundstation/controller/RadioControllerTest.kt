package com.github.arapy.groundstation.controller

import com.github.arapy.groundstation.demodulator.FmDemodulator
import com.parodison.orbit.core.groundstation.dto.DecodedSignal
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
import kotlin.time.Duration.Companion.seconds

class RadioControllerTest {

    @Test
    fun `escuchar 100_9 FM via RadioController y recibir DecodedSignal Audio`() = runTest {
        val controller = RadioController(scope = backgroundScope)

        controller.listen(frequency = 100_900_000, demodulator = FmDemodulator())

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
        controller.listen(frequency = 100_900_000, demodulator = FmDemodulator())

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
}