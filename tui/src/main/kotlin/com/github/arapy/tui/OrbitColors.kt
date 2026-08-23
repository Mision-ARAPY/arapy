package com.github.arapy.tui

import com.jakewharton.mosaic.ui.Color

/**
 * Port de OrbitColors (orbital-system/app/shared/.../App.kt) a Mosaic. El Color de Compose usa
 * 0xAARRGGBB con canal alpha; el de Mosaic es RGB puro (0-255 por canal, sin alpha - no existe
 * transparencia en una terminal truecolor), así que el byte de alpha se descarta acá.
 */
private fun hex(rgb: Int): Color = Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

object OrbitColors {
    // superficies (azul muy oscuro)
    val bg = hex(0x070B14)
    val surface = hex(0x080D18)
    val card = hex(0x0A101C)
    val cardInset = hex(0x0C1424)
    val cardDeep = hex(0x05080F)
    val border = hex(0x16202F)
    val borderSoft = hex(0x141D2E)
    val borderStrong = hex(0x26324A)
    val hoverBorder = hex(0x3A4560)

    // texto
    val text = hex(0xE6EDF7)
    val textStrong = hex(0xDFE7F3)
    val textMuted = hex(0xA9B8D0)
    val label = hex(0x6B7B98)
    val labelDim = hex(0x55637F)
    val dim = hex(0x4B5B78)

    // semánticos de estado
    val green = hex(0x22C55E)
    val greenText = hex(0x4ADE80)
    val greenBorder = hex(0x1F4433)
    val greenBg = hex(0x0D1C17)

    val amber = hex(0xFBBF24)
    val amberBorder = hex(0x4A3A18)
    val amberBg = hex(0x1A1610)

    val red = hex(0xEF4444)
    val redText = hex(0xF87171)
    val redBorder = hex(0x4A1F22)
    val redBg = hex(0x1A1114)

    val cyan = hex(0x38BDF8)
    val cyanText = hex(0x7DD3FC)
    val cyanBorder = hex(0x1C3450)
    val cyanBg = hex(0x0C1926)

    val neutralDot = hex(0x3D4A63)
}
