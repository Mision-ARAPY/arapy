package com.github.arapy.groundstation.identity

import com.parodison.orbit.core.groundstation.dto.RotatorPosition
import java.io.File
import java.util.Properties

/**
 * El rotor no tiene encoder absoluto — arranca "creyendo" la última posición que se guardó acá,
 * asumiendo que nadie movió la antena a mano mientras el proceso estaba apagado. Sin esto, cada
 * reinicio del jar hace que el software vuelva a pensar que está en (0°, 0°) aunque la antena
 * físicamente haya quedado apuntando a otro lado.
 */
object RotatorPositionStore {
    private val file = File(System.getProperty("user.home"), ".config/groundstation/rotator.properties")

    fun load(): RotatorPosition? {
        if (!file.exists()) return null
        val props = Properties().apply { load(file.inputStream()) }
        val az = props.getProperty("azimuthDegrees")?.toDoubleOrNull() ?: return null
        val el = props.getProperty("elevationDegrees")?.toDoubleOrNull() ?: return null
        return RotatorPosition(azimuthDegrees = az, elevationDegrees = el)
    }

    fun save(position: RotatorPosition) {
        file.parentFile.mkdirs()
        Properties().apply {
            setProperty("azimuthDegrees", position.azimuthDegrees.toString())
            setProperty("elevationDegrees", position.elevationDegrees.toString())
        }.store(file.outputStream(), "Rotator Position")
    }
}
