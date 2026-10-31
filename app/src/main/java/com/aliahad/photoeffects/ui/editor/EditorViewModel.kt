package com.aliahad.photoeffects.ui.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps photo/edit/save work alive while the Activity is recreated. */
class EditorViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var photoLoad: Job? = null
    private val restored = savedStateHandle.get<Bundle>(SESSION_KEY)?.getString(SESSION_VALUE)
    val state = EditorState(application, EditorSessionCodec.decode(restored))

    init {
        savedStateHandle.setSavedStateProvider(SESSION_KEY) {
            Bundle().apply { putString(SESSION_VALUE, EditorSessionCodec.encode(state.session())) }
        }
        photoLoad = scope.launch { state.bootstrap() }
    }

    fun loadPhoto(uri: Uri) {
        // Photo Picker grants are temporary unless explicitly retained. This lets a restored
        // session reopen the original for preview and full-resolution export after process death.
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some document providers only offer a transient grant; loading still works now,
            // and a later inaccessible source is surfaced as a recoverable picker action.
        }
        photoLoad?.cancel()
        photoLoad = scope.launch { state.loadUserPhoto(uri) }
    }

    fun save() {
        scope.launch { state.save() }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val SESSION_KEY = "editor_session"
        const val SESSION_VALUE = "value"
    }
}
