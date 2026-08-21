package com.github.arapy.groundstation.controller

import com.github.arapy.groundstation.demodulator.Ax25Demodulator
import com.github.arapy.groundstation.demodulator.FmDemodulator
import com.github.arapy.groundstation.demodulator.RadioDemodulator
import com.github.arapy.groundstation.hardware.Rotator
import com.github.arapy.groundstation.identity.GroundStationLocationStore
import com.parodison.orbit.core.groundstation.dto.GroundStationStatus
import com.parodison.orbit.core.satellite.ObserverCoordinates
import com.parodison.orbit.core.satellite.Satellite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class GroundStationController(
    val rotator: Rotator,
    private val radio: RadioController,
    private val hardwareDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private val _groundStationLocation = MutableStateFlow(GroundStationLocationStore.load())
    val groundStationLocation: StateFlow<ObserverCoordinates?> = _groundStationLocation.asStateFlow()

    private val _status = MutableStateFlow<GroundStationStatus>(GroundStationStatus.Idle)
    val status = _status.asStateFlow()

    val signals = radio.signals

    private var trackingJob: Job? = null

    /** Escucha activa (pedida vía [listenFrequency]) y la última frecuencia realmente sintonizada tras Doppler. */
    private data class ListeningRequest(val nominalFrequencyHz: Long, val mode: String, val tunedFrequencyHz: Long)
    private var listeningRequest: ListeningRequest? = null

    fun updateLocation(coordinates: ObserverCoordinates) {
        _groundStationLocation.value = coordinates
        GroundStationLocationStore.save(coordinates)
    }

    /** Called by [com.github.arapy.groundstation.connection.RelayConnection] right after the handshake succeeds. */
    fun markConnected() {
        if (_status.value !is GroundStationStatus.Connected) {
            _status.value = GroundStationStatus.Connected.Ready
        }
    }

    fun startTracking(satellite: Satellite) {
        val observer = groundStationLocation.value
        if (observer == null) {
            _status.value = GroundStationStatus.Connected.Error(
                message = "Ubicación de la estación no configurada",
                recoverable = true
            )
            return
        }

        trackingJob?.cancel()
        trackingJob = scope.launch(hardwareDispatcher) {
            val now = Clock.System.now()

            val passes = satellite.nextPassesFrom(
                observer,
                from = now,
                searchWindow = 24.hours,
                minElevationDeg = 10.0
            )
            val nextPass = passes.firstOrNull() ?: run {
                _status.value = GroundStationStatus.Connected.Ready
                return@launch
            }
            val aosLookAngles = satellite.lookAnglesFrom(observer, nextPass.aos)

            _status.value = GroundStationStatus.Connected.PreparingForPass(
                omm = satellite.omm,
                aosAzimuth = aosLookAngles.azimuthDeg,
                aosTime = nextPass.aos
            )
            rotator.moveTo(aosLookAngles.azimuthDeg, aosLookAngles.elevationDeg)
            val waitMs = (nextPass.aos - Clock.System.now())
            if (waitMs > 0.milliseconds) delay(waitMs)

            trackLoop(satellite, observer, nextPass.los)
        }
    }

    private suspend fun trackLoop(satellite: Satellite, observer: ObserverCoordinates, until: Instant) {
        while (currentCoroutineContext().isActive && Clock.System.now() < until) {
            val look = satellite.lookAnglesFrom(observer, Clock.System.now())
            if (look.elevationDeg <= 0) break

            rotator.moveTo(look.azimuthDeg, look.elevationDeg)
            _status.value = GroundStationStatus.Connected.Tracking(
                omm = satellite.omm,
                azimuth = look.azimuthDeg,
                elevation = look.elevationDeg,
            )
            applyDopplerCorrection(look.rangeRateKmPerSec)

            delay(600.milliseconds)
        }
        _status.value = GroundStationStatus.Connected.PassComplete
        rotator.resetPosition()
    }

    fun stopTracking() {
        val job = trackingJob
        trackingJob = null
        _status.value = GroundStationStatus.Connected.Ready
        scope.launch(hardwareDispatcher) {
            job?.cancelAndJoin()
            rotator.resetPosition()
        }
    }

    fun listenFrequency(frequencyHz: Long, mode: String) {
        val demodulator = demodulatorFor(mode) ?: run {
            _status.value = GroundStationStatus.Connected.Error(
                message = "Modo de escucha no implementado: $mode",
                recoverable = true
            )
            return
        }
        listeningRequest = ListeningRequest(nominalFrequencyHz = frequencyHz, mode = mode, tunedFrequencyHz = frequencyHz)
        radio.listen(frequency = frequencyHz, demodulator = demodulator)
    }

    fun stopListening() {
        listeningRequest = null
        radio.stop()
    }

    // Modos de https://db.satnogs.org/api/modes/ - solo se soportan los que tienen
    // demodulador implementado; el resto reporta error en vez de decodificar mal.
    private fun demodulatorFor(mode: String): RadioDemodulator? = when (mode) {
        "FM" -> FmDemodulator()
        "AFSK" -> Ax25Demodulator()
        else -> null
    }

    /**
     * rtl_fm no soporta re-sintonizar en caliente: la única forma de corregir el corrimiento
     * Doppler es reiniciar el proceso a la frecuencia correcta, lo que corta la captura un
     * instante. Por eso solo se retunea si el corrimiento supera [RETUNE_THRESHOLD_HZ], en vez
     * de en cada tick del trackLoop.
     */
    private fun applyDopplerCorrection(rangeRateKmPerSec: Double) {
        val request = listeningRequest ?: return
        val correctedFrequencyHz = dopplerCorrectedFrequency(request.nominalFrequencyHz, rangeRateKmPerSec)
        if (abs(correctedFrequencyHz - request.tunedFrequencyHz) < RETUNE_THRESHOLD_HZ) return

        val demodulator = demodulatorFor(request.mode) ?: return
        listeningRequest = request.copy(tunedFrequencyHz = correctedFrequencyHz)
        radio.listen(frequency = correctedFrequencyHz, demodulator = demodulator)
    }

    private companion object {
        const val RETUNE_THRESHOLD_HZ = 500L
        const val SPEED_OF_LIGHT_M_PER_S = 299_792_458.0

        fun dopplerCorrectedFrequency(nominalFrequencyHz: Long, rangeRateKmPerSec: Double): Long {
            val rangeRateMPerS = rangeRateKmPerSec * 1000.0
            return (nominalFrequencyHz * (1.0 - rangeRateMPerS / SPEED_OF_LIGHT_M_PER_S)).toLong()
        }
    }

}