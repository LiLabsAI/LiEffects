package com.aliahad.photoeffects.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EffectShelf
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlan
import com.aliahad.photoeffects.photo.ExportPlanner
import com.aliahad.photoeffects.photo.ExportResolution
import com.aliahad.photoeffects.photo.ExportSettings
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.photo.PhotoLoader
import com.aliahad.photoeffects.photo.PhotoSaver
import com.aliahad.photoeffects.photo.ResolutionOption

/** Bottom tool tabs (only Effects is functional in this foundation). */
enum class EditorTool(val label: String) {
    Effects("Effects"),
    Filters("Filters"),
    Adjust("Adjust"),
    Canvas("Canvas"),
    Text("Text"),
    Overlay("Overlay"),
}

/** One applied effect + its slider position. */
data class EffectSelection(val effectId: String, val intensity: Int)

/**
 * Editor state holder: photo, selected effect, intensity, undo history, save flow.
 *
 * Plain Compose state (no ViewModel) so it stays simple; all bitmap work happens off the main
 * thread in [PhotoLoader] / [PhotoSaver] / [EffectBitmapRenderer].
 */
class EditorState(private val context: Context) {

    var photo by mutableStateOf<LoadedPhoto?>(null)
        private set
    var isLoadingPhoto by mutableStateOf(true)
        private set
    var selection by mutableStateOf<EffectSelection?>(null)
        private set
    /** Currently selected Effects-panel shelf (family or category); see [EffectRegistry.shelves]. */
    var activeShelfId by mutableStateOf(EffectRegistry.defaultShelf.id)
    var activeTool by mutableStateOf(EditorTool.Effects)
    var showOriginal by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    /** Resolution / quality / metadata picked in the export sheet. */
    var exportOptions by mutableStateOf(ExportSettings.load(context))
        private set
    var showExportSheet by mutableStateOf(false)
        private set

    val resolutionOptions: List<ResolutionOption>
        get() = photo?.let {
            ExportPlanner.resolutionOptions(it.sourceWidth, it.sourceHeight, selectedEffect)
        } ?: emptyList()

    val exportPlan: ExportPlan?
        get() = photo?.let {
            ExportPlanner.plan(it.sourceWidth, it.sourceHeight, exportOptions, selectedEffect)
        }

    /** Snapshot stack for undo (null = no effect applied). */
    private val history = ArrayDeque<EffectSelection?>()
    private var dragSnapshot: EffectSelection? = null

    val selectedEffect: PhotoEffect?
        get() = selection?.let { EffectRegistry.byId(it.effectId) }

    val intensity: Int
        get() = selection?.intensity ?: selectedEffect?.defaultIntensity ?: 100

    val canUndo: Boolean
        get() = history.isNotEmpty()

    val activeShelf: EffectShelf
        get() = EffectRegistry.shelfById(activeShelfId) ?: EffectRegistry.defaultShelf

    val effects: List<PhotoEffect>
        get() = activeShelf.effects

    suspend fun bootstrap() {
        isLoadingPhoto = true
        runCatching { PhotoLoader.loadDemo(context) }
            .onSuccess { photo = it }
            .onFailure { message = "Could not load demo photo" }
        isLoadingPhoto = false
    }

    suspend fun loadUserPhoto(uri: Uri) {
        isLoadingPhoto = true
        runCatching { PhotoLoader.loadFromUri(context, uri) }
            .onSuccess {
                photo = it
                selection = null
                history.clear()
                activeShelfId = EffectRegistry.defaultShelf.id
                resetExportResolution()
            }
            .onFailure { message = "Could not load that photo" }
        isLoadingPhoto = false
    }

    suspend fun reloadDemo() {
        isLoadingPhoto = true
        runCatching { PhotoLoader.loadDemo(context) }
            .onSuccess {
                photo = it
                selection = null
                history.clear()
                resetExportResolution()
            }
            .onFailure { message = "Could not load demo photo" }
        isLoadingPhoto = false
    }

    fun toggleEffect(effect: PhotoEffect) {
        pushHistory()
        selection = if (selection?.effectId == effect.id) {
            null
        } else {
            EffectSelection(effect.id, effect.defaultIntensity)
        }
    }

    fun setIntensity(value: Int) {
        val current = selection ?: return
        selection = current.copy(intensity = value.coerceIn(0, 100))
    }

    fun beginIntensityDrag() {
        dragSnapshot = selection
    }

    fun commitIntensityDrag() {
        val before = dragSnapshot
        dragSnapshot = null
        if (before != selection) history.addLast(before)
    }

    fun undo() {
        if (history.isEmpty()) return
        selection = history.removeLast()
    }

    fun resetAll() {
        if (selection != null) {
            pushHistory()
            selection = null
            message = "Edits reset"
        } else {
            message = "Nothing to reset"
        }
    }

    fun holdOriginal(holding: Boolean) {
        showOriginal = holding
    }

    /**
     * Opens the PREQUEL-style export sheet. If the current resolution pick cannot be filled by
     * this photo (e.g. 4K on a 1440×1920 shot), the selection snaps to the effective tier so
     * the highlighted option matches what Save will write.
     */
    fun openExportSheet() {
        val current = photo ?: return
        val effective = ExportPlanner.effectiveResolution(
            current.sourceWidth,
            current.sourceHeight,
            exportOptions.resolution,
            selectedEffect,
        )
        if (effective != exportOptions.resolution) {
            exportOptions = exportOptions.copy(resolution = effective)
        }
        showExportSheet = true
    }

    fun dismissExportSheet() {
        showExportSheet = false
    }

    fun updateExportOptions(options: ExportOptions) {
        if (options == exportOptions) return
        exportOptions = options
        ExportSettings.save(context, options)
    }

    suspend fun save() {
        val currentPhoto = photo ?: return
        if (isSaving) return
        showExportSheet = false
        isSaving = true
        val effect = selectedEffect
        runCatching { PhotoSaver.save(context, currentPhoto, effect, intensity, exportOptions) }
            .onSuccess { result ->
                message = if (result.success) {
                    "Saved ${result.width}×${result.height} to ${result.displayPath}"
                } else {
                    "Save failed: ${result.error ?: "unknown error"}"
                }
            }
            .onFailure { message = "Save failed: ${it.message ?: "unknown error"}" }
        isSaving = false
    }

    fun showMessage(text: String) {
        message = text
    }

    fun consumeMessage() {
        message = null
    }

    private fun pushHistory() {
        history.addLast(selection)
        // Keep the stack bounded; the editor only needs a few steps of history.
        while (history.size > 24) history.removeFirst()
    }

    /** New photo, fresh export: back to the source-size default (PREQUEL's "large as source"). */
    private fun resetExportResolution() {
        exportOptions = exportOptions.copy(resolution = ExportResolution.FOUR_K)
    }

    fun selectShelf(shelfId: String) {
        if (EffectRegistry.shelfById(shelfId) != null) activeShelfId = shelfId
    }
}
