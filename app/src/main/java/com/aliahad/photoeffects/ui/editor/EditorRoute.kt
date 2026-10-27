package com.aliahad.photoeffects.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Compose entry point: owns the [EditorState], loads the bundled demo photo on first launch,
 * wires the Android photo picker and surfaces state messages as toasts.
 */
@Composable
fun EditorRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { EditorState(context.applicationContext) }

    LaunchedEffect(Unit) { state.bootstrap() }

    val message = state.message
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            state.consumeMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch { state.loadUserPhoto(uri) }
        }
    }

    EditorScreen(
        state = state,
        onPickPhoto = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}
