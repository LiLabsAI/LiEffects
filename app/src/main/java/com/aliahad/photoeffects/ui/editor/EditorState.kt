package com.aliahad.photoeffects.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EffectShelf
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.filter.FilterRegistry
import com.aliahad.photoeffects.filter.FilterControl
import kotlin.math.roundToInt
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlan
import com.aliahad.photoeffects.photo.ExportPlanner
import com.aliahad.photoeffects.photo.ExportResolution
import com.aliahad.photoeffects.photo.ExportSettings
import com.aliahad.photoeffects.photo.LoadedPhoto
import com.aliahad.photoeffects.photo.PhotoLoader
import com.aliahad.photoeffects.photo.PhotoSaver
import com.aliahad.photoeffects.photo.ResolutionOption

/** Bottom tool tabs. Effects and Filters are independent, composable selections. */
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

data class FilterSelection(
    val filterId: String, val strength: Int, val sharpen: Int,
    val intensity: Int = 100, val grain: Int = 0,
    val highlights: Int = 0, val shadows: Int = 0,
    val aberration: Int = 0,
)
private data class EditSnapshot(val effect: EffectSelection?, val filter: FilterSelection?)

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
    var filterSelection by mutableStateOf<FilterSelection?>(null)
        private set
    var filterControl by mutableStateOf(FilterControl.Strength)
    var activeFilterShelfId by mutableStateOf(FilterRegistry.defaultShelf.id)
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
            ExportPlanner.resolutionOptions(it.sourceWidth, it.sourceHeight, renderEffect)
        } ?: emptyList()

    val exportPlan: ExportPlan?
        get() = photo?.let {
            ExportPlanner.plan(it.sourceWidth, it.sourceHeight, exportOptions, renderEffect)
        }

    /** Undo stores both selections, so switching tools never drops the other edit. */
    private val history = ArrayDeque<EditSnapshot>()
    private var dragSnapshot: EditSnapshot? = null

    val selectedEffect: PhotoEffect?
        get() = selection?.let { EffectRegistry.byId(it.effectId) }

    val selectedFilter: PhotoEffect? by derivedStateOf {
        filterSelection?.let {
            FilterRegistry.withControls(it.filterId, it.strength, it.sharpen, it.intensity, it.grain, it.highlights, it.shadows, it.aberration)
        }
    }

    /** One immutable render snapshot shared by preview, export planning and saving. */
    val renderEffect: PhotoEffect? by derivedStateOf {
        EditComposition.compose(
            selectedFilter?.let { EditStage(it, 100) },
            selectedEffect?.let { EditStage(it, selection?.intensity ?: it.defaultIntensity) },
        )
    }

    val activePreset: PhotoEffect?
        get() = if (activeTool == EditorTool.Filters) selectedFilter else selectedEffect

    val strengthLabel: String
        get() = listOfNotNull(
            filterSelection?.let {
                "Filter ${it.strength}%" + if (FilterControl.Intensity in filterControls) " · Intensity ${it.intensity}%" else ""
            },
            selection?.let { "Effect ${it.intensity}%" },
        ).joinToString(" · ")

    val intensity: Int
        get() = if (activeTool == EditorTool.Filters) {
            when (activeFilterControl) {
                FilterControl.Strength -> filterSelection?.strength ?: 100
                FilterControl.Sharpen -> filterSelection?.sharpen ?: 0
                FilterControl.Intensity -> filterSelection?.intensity ?: 100
                FilterControl.Grain -> filterSelection?.grain ?: 0
                FilterControl.Highlights -> filterSelection?.highlights ?: 0
                FilterControl.Shadows -> filterSelection?.shadows ?: 0
                FilterControl.Aberration -> filterSelection?.aberration ?: 0
            }
        } else selection?.intensity ?: selectedEffect?.defaultIntensity ?: 100

    val filterControls: List<FilterControl>
        get() = filterSelection?.let { FilterRegistry.paramsById(it.filterId)?.controls }
            ?: listOf(FilterControl.Strength, FilterControl.Sharpen)

    val activeFilterControl: FilterControl
        get() = filterControl.takeIf { it in filterControls } ?: filterControls.first()

    val canUndo: Boolean
        get() = history.isNotEmpty()

    val activeShelf: EffectShelf
        get() = if (activeTool == EditorTool.Filters) {
            FilterRegistry.shelfById(activeFilterShelfId) ?: FilterRegistry.defaultShelf
        } else EffectRegistry.shelfById(activeShelfId) ?: EffectRegistry.defaultShelf

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
                filterSelection = null
                dragSnapshot = null
                history.clear()
                activeShelfId = EffectRegistry.defaultShelf.id
                activeFilterShelfId = FilterRegistry.defaultShelf.id
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
                filterSelection = null
                dragSnapshot = null
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
        if (activeTool == EditorTool.Filters) {
            val current = filterSelection ?: return
            val clamped = value.coerceIn(activeFilterControl.range)
            filterSelection = when (activeFilterControl) {
                FilterControl.Strength -> current.copy(strength = clamped)
                FilterControl.Sharpen -> current.copy(sharpen = clamped)
                FilterControl.Intensity -> current.copy(intensity = clamped)
                FilterControl.Grain -> current.copy(grain = clamped)
                FilterControl.Highlights -> current.copy(highlights = clamped)
                FilterControl.Shadows -> current.copy(shadows = clamped)
                FilterControl.Aberration -> current.copy(aberration = clamped)
            }
            return
        }
        val current = selection ?: return
        selection = current.copy(intensity = value.coerceIn(0, 100))
    }

    fun beginIntensityDrag() {
        dragSnapshot = snapshot()
    }

    fun commitIntensityDrag() {
        val before = dragSnapshot ?: return
        dragSnapshot = null
        if (before != snapshot()) recordHistory(before)
    }

    fun undo() {
        if (history.isEmpty()) return
        val previous = history.removeLast()
        selection = previous.effect
        filterSelection = previous.filter
    }

    fun resetAll() {
        if (selection != null || filterSelection != null) {
            pushHistory()
            selection = null
            filterSelection = null
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
            renderEffect,
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
        val effect = renderEffect
        runCatching { PhotoSaver.save(context, currentPhoto, effect, 100, exportOptions) }
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
        recordHistory(snapshot())
    }

    private fun snapshot() = EditSnapshot(selection, filterSelection)

    private fun recordHistory(snapshot: EditSnapshot) {
        history.addLast(snapshot)
        // Keep the stack bounded; the editor only needs a few steps of history.
        while (history.size > 24) history.removeFirst()
    }

    /** New photo, fresh export: back to the source-size default (PREQUEL's "large as source"). */
    private fun resetExportResolution() {
        exportOptions = exportOptions.copy(resolution = ExportResolution.FOUR_K)
    }

    fun selectShelf(shelfId: String) {
        if (activeTool == EditorTool.Filters) {
            if (FilterRegistry.shelfById(shelfId) != null) activeFilterShelfId = shelfId
        } else if (EffectRegistry.shelfById(shelfId) != null) activeShelfId = shelfId
    }

    fun toggleFilter(filter: PhotoEffect) {
        val params = FilterRegistry.paramsById(filter.id) ?: return
        pushHistory()
        filterSelection = if (filterSelection?.filterId == filter.id) null
            else FilterSelection(
                filter.id, (params.strength * 100).roundToInt(), params.sharpen, params.intensity,
                params.grain, params.highlights, params.shadows, params.aberration,
            )
        filterControl = params.controls.first()
    }

    fun clearActivePreset() {
        val current = activePreset ?: return
        if (activeTool == EditorTool.Filters) toggleFilter(current) else toggleEffect(current)
    }
}
