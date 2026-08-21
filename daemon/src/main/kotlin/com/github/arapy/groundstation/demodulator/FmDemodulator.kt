package com.github.arapy.groundstation.demodulator

import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FmDemodulator : RadioDemodulator {
    override fun demodulate(input: Flow<ByteArray>): Flow<DecodedSignal> =
        input.map { chunk ->
            DecodedSignal.Audio(
                pcmBytes = chunk,
                sampleRate = 48000,
            )
        }
}