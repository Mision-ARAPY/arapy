package com.github.arapy.groundstation.hardware

import com.github.arapy.groundstation.identity.RotatorPositionStore
import com.parodison.orbit.core.groundstation.dto.RotatorPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class Rotator(
    val azMotor: StepperMotor,
    val elMotor: StepperMotor,
) {
    init {
        // No hay encoder absoluto: arrancamos "creyendo" la última posición guardada en vez de
        // asumir (0°, 0°) en cada reinicio del proceso — ver RotatorPositionStore.
        RotatorPositionStore.load()?.let { saved ->
            azMotor.setOrigin(saved.azimuthDegrees)
            elMotor.setOrigin(saved.elevationDegrees)
        }
    }

    val position = combine(
        azMotor.currentDegrees,
        elMotor.currentDegrees,
    ) { az, el -> RotatorPosition(az, el) }
        .stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            started = SharingStarted.Eagerly,
            initialValue = RotatorPosition(0.0, 0.0),
        )

    suspend fun moveTo(azimuth: Double, elevation: Double) {
        coroutineScope {
            launch { azMotor.moveTo(azimuth) }
            launch { elMotor.moveTo(elevation) }
        }
        RotatorPositionStore.save(RotatorPosition(azMotor.currentDegrees.value, elMotor.currentDegrees.value))
    }

    suspend fun resetPosition() {
        moveTo(0.0, 0.0)
    }
}