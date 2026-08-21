package com.github.arapy.groundstation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.InputStream

fun InputStream.asByteFlow(chunkSize: Int = 4096): Flow<ByteArray> = flow {
    val buffer = ByteArray(chunkSize)
    while (currentCoroutineContext().isActive) {
        val bytesRead = read(buffer)
        if (bytesRead == -1) break
        emit(buffer.copyOf(bytesRead))
    }
}.flowOn(Dispatchers.IO)