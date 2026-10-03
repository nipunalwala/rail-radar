package com.trainnearme.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Pull down to reload a board. The indicator stays until the board that the
 * pull asked for has arrived.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardPullToRefresh(
    board: BoardUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(board) {
        if (board !is BoardUiState.Loading) pulled = false
    }
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        content = content,
    )
}
