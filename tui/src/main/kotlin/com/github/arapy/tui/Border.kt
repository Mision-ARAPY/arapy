package com.github.arapy.tui

import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier

/**
 * Mosaic no trae Modifier.border() propio - se dibuja a mano con drawBehind. padding() solo
 * empuja el contenido hacia adentro en los lados que realmente tienen borde (como el box-model
 * de CSS: el ancho del borde solo cuenta del lado en que existe).
 */
fun Modifier.border(
    top: Boolean = true,
    bottom: Boolean = true,
    left: Boolean = true,
    right: Boolean = true,
    thickness: Int = 1,
    horizontalChar: Char = '─',
    verticalChar: Char = '│',
): Modifier = this
    .drawBehind {
        if (top) {
            for (row in 0 until thickness) {
                drawText(row, 0, horizontalChar.toString().repeat(width))
            }
        }
        if (bottom) {
            for (row in height - thickness until height) {
                drawText(row, 0, horizontalChar.toString().repeat(width))
            }
        }

        val verticalStart = if (top) thickness else 0
        val verticalEnd = height - if (bottom) thickness else 0
        for (row in verticalStart until verticalEnd) {
            if (left) drawText(row, 0, verticalChar.toString().repeat(thickness))
            if (right) drawText(row, width - thickness, verticalChar.toString().repeat(thickness))
        }

        // Los glifos de esquina (┌┐└┘) son de un solo carácter - con thickness > 1 la esquina
        // queda como un bloque sólido donde se pisan la línea horizontal y la vertical, que es
        // lo esperable para un borde "grueso".
        if (thickness == 1) {
            if (top && left) drawText(0, 0, "┌")
            if (top && right) drawText(0, width - 1, "┐")
            if (bottom && left) drawText(height - 1, 0, "└")
            if (bottom && right) drawText(height - 1, width - 1, "┘")
        }
    }
    .padding(
        top = if (top) thickness else 0,
        bottom = if (bottom) thickness else 0,
        left = if (left) thickness else 0,
        right = if (right) thickness else 0,
    )
