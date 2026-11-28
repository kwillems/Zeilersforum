package nl.kwillems.zeilersforum.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Roept [onHold] aan als je vinger even stil op het element blijft (een tik in wording), niet bij
 * scrollen. Gebruikt om een onderwerp alvast op te halen; de tik zelf wordt niet onderschept.
 */
fun Modifier.prefetchOnHold(key: Any?, onHold: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var ended = false
        withTimeoutOrNull(HOLD_MS) {
            waitForUpOrCancellation()
            ended = true
        }
        // Na 70 ms nog steeds ingedrukt en niet weggescrold: waarschijnlijk wordt het een tik.
        if (!ended) onHold()
    }
}

private const val HOLD_MS = 70L
