package com.shyan.dreamin.ui.components

import com.shyan.dreamin.ui.theme.LocalDreaminColors

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shyan.dreamin.viewmodel.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun DreaminSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = LocalDreaminColors.current
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = colors.surfaceContainer,
            contentColor = Color.White,
            actionColor = colors.primary
        )
    }
}

@Composable
fun CollectUiMessages(messages: Flow<UiMessage>, hostState: SnackbarHostState) {
    LaunchedEffect(messages, hostState) {
        messages.collect { message ->
            launch {
                hostState.currentSnackbarData?.dismiss()
                val result = hostState.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }
}
