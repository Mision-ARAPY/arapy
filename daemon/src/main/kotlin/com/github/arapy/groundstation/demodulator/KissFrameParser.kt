package com.github.arapy.groundstation.demodulator

class KissFrameParser {
    private val buffer = mutableListOf<Byte>()

    fun feed(chunk: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        for (byte in chunk) {
            when (byte) {
                KISS_FEND -> {
                    // Un KISS frame completo es [cmd/puerto][payload AX.25] — sin descartar el
                    // primer byte (comando), todo lo que Ax25Demodulator lee después (offsets 0
                    // para destino, 7 para origen, 16 para el header completo) queda corrido un
                    // byte: callsigns y payload salían mal aunque el frame llegara entero.
                    if (buffer.size > 1) {
                        frames.add(buffer.drop(1).toByteArray())
                    }
                    buffer.clear()
                }
                KISS_FESC -> pendingEscape = true
                else -> {
                    if (pendingEscape) {
                        pendingEscape = false
                        buffer.add(if (byte == KISS_TFEND) KISS_FEND else if (byte == KISS_TFESC) KISS_FESC else byte)
                    } else {
                        buffer.add(byte)
                    }
                }
            }
        }
        return frames
    }

    private var pendingEscape = false

    companion object {
        private const val KISS_FEND: Byte = 0xC0.toByte()
        private const val KISS_FESC: Byte = 0xDB.toByte()
        private const val KISS_TFEND: Byte = 0xDC.toByte()
        private const val KISS_TFESC: Byte = 0xDD.toByte()
    }
}