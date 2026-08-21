package com.github.arapy.groundstation.demodulator

import com.github.arapy.groundstation.asByteFlow
import com.parodison.orbit.core.groundstation.dto.Ax25Content
import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class Ax25Demodulator : RadioDemodulator {

    override fun demodulate(input: Flow<ByteArray>): Flow<DecodedSignal> = flow {
        val direwolf = ProcessBuilder(
            "direwolf", "-c", writeDirewolfConfig().absolutePath, "-n", "1", "-r", "48000", "-b", "16", "-"
        )
            // Direwolf tira el log humano-legible ("N0CALL>APRS:...") por su stdout — los frames
            // decodificados de verdad los sirve por el puerto KISS TCP que configuramos abajo, no
            // por acá. Lo dejamos INHERIT solo para poder verlo en consola mientras se debuggea.
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        val feedJob = CoroutineScope(currentCoroutineContext()).launch {
            direwolf.outputStream.use { stdin ->
                input.collect { chunk -> stdin.write(chunk) }
            }
        }

        val socket = connectKissSocket(KISS_PORT)
        try {
            val parser = KissFrameParser()
            socket.getInputStream().asByteFlow().collect { chunk ->
                parser.feed(chunk).forEach { frame ->
                    emit(parseAx25ToSignal(frame))
                }
            }
        } finally {
            feedJob.cancel()
            socket.close()
            direwolf.destroy()
        }
    }.flowOn(Dispatchers.IO)

    /** Direwolf tarda un instante en levantar el socket KISS después de arrancar — reintenta en vez de asumir que ya está listo. */
    private suspend fun connectKissSocket(port: Int): Socket {
        var lastError: Exception? = null
        repeat(40) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 500)
                return socket
            } catch (e: Exception) {
                lastError = e
                delay(250)
            }
        }
        throw IllegalStateException("No se pudo conectar al puerto KISS de direwolf (127.0.0.1:$port)", lastError)
    }

    /**
     * Config propia de la app (no depende de lo que haya en `~/direwolf.conf`, que es del
     * usuario y puede no tener `KISSPORT` seteado — direwolf igual sirve KISS por default en
     * ese caso, pero mejor no depender de un default no explícito).
     */
    private fun writeDirewolfConfig(): File {
        val file = File(System.getProperty("user.home"), ".config/groundstation/direwolf-rx.conf")
        file.parentFile.mkdirs()
        file.writeText(
            """
            CHANNEL 0
            MYCALL N0CALL
            KISSPORT $KISS_PORT
            """.trimIndent()
        )
        return file
    }

    private companion object {
        const val KISS_PORT = 8001
    }
}

private fun parseAx25ToSignal(rawFrame: ByteArray): DecodedSignal.Textual.Ax25 {
    val destinationCallsign = extractCallsign(rawFrame, offset = 0)
    val sourceCallsign = extractCallsign(rawFrame, offset = 7)
    val payload = extractPayload(rawFrame)

    return DecodedSignal.Textual.Ax25(
        sourceCallsign = sourceCallsign,
        destinationCallsign = destinationCallsign,
        content = Ax25Content.Raw(payload),
    )
}

private fun extractCallsign(raw: ByteArray, offset: Int): String {
    if (offset + 7 > raw.size) return "UNKNOWN"
    val chars = (0 until 6)
        .map { i -> ((raw[offset + i].toInt() and 0xFF) shr 1).toChar() }
        .joinToString("")
        .trim()
    val ssid = (raw[offset + 6].toInt() shr 1) and 0x0F
    return if (ssid > 0) "$chars-$ssid" else chars
}

private fun extractPayload(raw: ByteArray): ByteArray {
    val headerEnd = 16
    return if (raw.size > headerEnd) raw.copyOfRange(headerEnd, raw.size) else ByteArray(0)
}