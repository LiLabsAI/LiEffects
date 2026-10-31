package com.aliahad.photoeffects.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Wires the retained editor to the Android photo picker and surfaces state messages as toasts.
 */
@Composable
fun EditorRoute(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val state = viewModel.state

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
            viewModel.loadPhoto(uri)
        }
    }

    EditorScreen(
        state = state,
        onSave = viewModel::save,
        onPickPhoto = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}
