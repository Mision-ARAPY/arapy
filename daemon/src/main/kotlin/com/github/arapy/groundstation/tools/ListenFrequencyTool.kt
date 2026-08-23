package com.github.arapy.groundstation.tools

import com.github.arapy.groundstation.connection.client
import com.github.arapy.groundstation.connection.sendPayload
import com.github.arapy.groundstation.controller.RadioController
import com.github.arapy.groundstation.controller.TunerMode
import com.github.arapy.groundstation.demodulator.FmDemodulator
import com.github.arapy.groundstation.identity.StationIdentityStore
import com.parodison.orbit.core.groundstation.dto.DecodedSignal
import com.parodison.orbit.core.groundstation.dto.GroundStation
import com.parodison.orbit.core.groundstation.dto.GroundStationStatus
import com.parodison.orbit.core.protocol.WebsocketPayload
import com.parodison.orbit.core.satellite.ObserverCoordinates
import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Puerto a `main()` de `RadioControllerTest."sintonizar una frecuencia FM y enviarla por
 * websocket"` — los tests de JUnit no viajan en el shadowJar que arma `deployToPi`/`runOnPi`
 * (solo empaquetan src/main), así que esta es la forma de correr esa misma prueba en la Pi vía
 * `runListenToolOnPi`.
 */
suspend fun main() {
    val identity = StationIdentityStore.getOrCreate()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    client.webSocket("ws://10.154.42.197:8081/ws/stations") {
        sendPayload(WebsocketPayload.StationHandshake(identity.id, identity.name))
        sendPayload(WebsocketPayload.UpdateStationLocation(
            identity.id,
            ObserverCoordinates(
                0.0, 0.0, 0.0,
            )
        ))
        sendPayload(WebsocketPayload.StatusUpdate(stationId = identity.id, status = GroundStationStatus.Connected.Ready))

        val controller = RadioController(scope = scope)
        controller.listen(100_900_000, demodulator = FmDemodulator(), tunerMode = TunerMode.WBFM)

        withContext(Dispatchers.Default.limitedParallelism(3)) {
            withTimeoutOrNull(1.minutes) {
                controller.signals
                    .filterIsInstance<DecodedSignal.Audio>()
                    .collect { signal ->
                        sendPayload(WebsocketPayload.DecodedSignalReceived(stationId = identity.id, signal = signal))
                    }
            }
        }

        controller.stop()
        // RadioController.stop() dispara la limpieza (destruir rtl_fm) en su propio scope sin
        // devolver un Job para esperar — esta pausa le da tiempo a correr antes de cerrar todo.
        delay(500.milliseconds)
    }

    client.close()
}
