package com.github.arapy.groundstation.demodulator

import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import kotlinx.coroutines.flow.Flow

interface RadioDemodulator {
    fun demodulate(
        input: Flow<ByteArray>
    ): Flow<DecodedSignal>
}