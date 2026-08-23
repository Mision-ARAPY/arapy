package com.github.arapy.tui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Text

/**
 * Mosaic no tiene TextField ni concepto de foco - las teclas llegan a cualquier
 * Modifier.onKeyEvent que haya en el árbol, sin un widget "activo" implícito. Con un solo campo
 * esto no importa; con varios, hay que decidir vos mismo cuál está activo (p. ej. un enum de
 * estado) y solo actualizar ese en el handler.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        value = "$value█",
        modifier = modifier.onKeyEvent { event ->
            when {
                event.key == "Backspace" -> {
                    onValueChange(value.dropLast(1))
                    true
                }
                event.key.length == 1 && !event.ctrl && !event.alt -> {
                    onValueChange(value + event.key)
                    true
                }
                else -> false
            }
        },
    )
}
