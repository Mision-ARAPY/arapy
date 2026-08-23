package com.github.arapy.tui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.runMosaic
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    runMosaic {
        var count by remember { mutableIntStateOf(0) }
        var textValue by remember { mutableStateOf("") }
        Box(
            modifier = Modifier.fillMaxSize().border(),
        ) {
            Text("The count is: $count", color = OrbitColors.green )
        }

        LaunchedEffect(Unit) {
            for (i in 1..20) {
                delay(250.milliseconds)
                count = i
            }
        }
    }
}