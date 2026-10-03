package com.trainnearme.ui.station

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trainnearme.BuildConfig
import com.trainnearme.R
import com.trainnearme.ui.common.BoardPullToRefresh
import com.trainnearme.ui.common.LineChips
import com.trainnearme.ui.common.ScreenPadding
import com.trainnearme.ui.common.boardItems
import com.trainnearme.ui.train.TrainSheet
import com.trainnearme.ui.train.TrainSheetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    onBack: () -> Unit,
    viewModel: StationDetailViewModel = hiltViewModel(),
    trainSheet: TrainSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainSheet(trainSheet)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.station?.name.orEmpty(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = viewModel::testAlert) {
                            Text(stringResource(R.string.action_test_alert))
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        BoardPullToRefresh(
            board = state.board,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                state.station?.let { station ->
                    item(key = "lines") {
                        LineChips(station, Modifier.padding(horizontal = ScreenPadding).padding(bottom = 8.dp))
                    }
                }
                boardItems(state.board, trainSheet::open)
            }
        }
    }
}
