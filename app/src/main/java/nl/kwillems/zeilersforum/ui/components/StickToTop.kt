package nl.kwillems.zeilersforum.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Een lijst met sleutels houdt het item in beeld dat bovenaan stond. Komen er bij verversen
 * nieuwe items boven (nieuwe onderwerpen, nieuwere berichten), dan zou je die niet zien.
 * Stond je bovenaan, dan schuift de lijst daarom mee naar boven; ben je verder gescrold, dan blijf je staan.
 */
@Composable
fun StickToTop(listState: LazyListState, firstKey: Any?, enabled: Boolean = true, isItem: (Any) -> Boolean = { true }) {
    val previous = remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(firstKey) {
        val old = previous.value
        previous.value = firstKey
        if (!enabled || old == null || firstKey == null || old == firstKey) return@LaunchedEffect
        val topItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { isItem(it.key) }?.key
        if (topItem == old) listState.scrollToItem(0)
    }
}
