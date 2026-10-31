package com.aliahad.photoeffects.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aliahad.photoeffects.adjust.AdjustCategory
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustLayers
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.adjust.HslBand
import com.aliahad.photoeffects.adjust.HslComponent
import com.aliahad.photoeffects.filter.FilterControl
import com.aliahad.photoeffects.photo.ExportMetadataMode
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportQuality
import com.aliahad.photoeffects.photo.ExportResolution
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

internal data class EditSnapshot(
    val effect: EffectSelection?,
    val filter: FilterSelection?,
    val adjust: AdjustParams,
)

/** The bounded undo stack is observable even when committing a drag changes no pixels. */
internal class EditHistory(initial: List<EditSnapshot> = emptyList()) {
    var entries by mutableStateOf(initial.takeLast(MAX_ENTRIES))
        private set

    val canUndo: Boolean get() = entries.isNotEmpty()

    fun record(snapshot: EditSnapshot) {
        entries = (entries + snapshot).takeLast(MAX_ENTRIES)
    }

    fun pop(): EditSnapshot? {
        val previous = entries.lastOrNull() ?: return null
        entries = entries.dropLast(1)
        return previous
    }

    fun clear() {
        entries = emptyList()
    }

    companion object {
        const val MAX_ENTRIES = 24
    }
}

/** Only small edit/navigation values are saved; decoded bitmaps stay in the retained model. */
internal data class EditorSession(
    val sourceUri: String? = null,
    val edit: EditSnapshot = EditSnapshot(null, null, AdjustParams()),
    val history: List<EditSnapshot> = emptyList(),
    val tool: EditorTool = EditorTool.Effects,
    val panel: PanelStage = PanelStage.Categories,
    val effectShelf: String? = null,
    val filterShelf: String? = null,
    val filterControl: FilterControl = FilterControl.Strength,
    val adjustCategory: AdjustCategory = AdjustCategory.Light,
    val adjustControl: AdjustControl = AdjustControl.Exposure,
    val hslBand: HslBand = HslBand.Red,
    val hslComponent: HslComponent = HslComponent.Hue,
    val exportOptions: ExportOptions? = null,
    val exportSheet: Boolean = false,
)

/** Versioned, Android-independent format, also exercised by the JVM restoration tests. */
internal object EditorSessionCodec {
    fun encode(session: EditorSession): String {
        val values = Properties()
        fun put(key: String, value: Any?) {
            if (value != null) values.setProperty(key, value.toString())
        }
        put("version", 1)
        put("source", session.sourceUri)
        writeEdit(values, "edit", session.edit)
        val history = session.history.takeLast(EditHistory.MAX_ENTRIES)
        put("history", history.size)
        history.forEachIndexed { index, edit -> writeEdit(values, "undo.$index", edit) }
        put("tool", session.tool.name)
        put("panel", session.panel.name)
        put("effectShelf", session.effectShelf)
        put("filterShelf", session.filterShelf)
        put("filterControl", session.filterControl.name)
        put("adjustCategory", session.adjustCategory.name)
        put("adjustControl", session.adjustControl.name)
        put("hslBand", session.hslBand.name)
        put("hslComponent", session.hslComponent.name)
        session.exportOptions?.let {
            put("resolution", it.resolution.name)
            put("quality", it.quality.name)
            put("metadata", it.metadata.name)
        }
        put("exportSheet", session.exportSheet)
        return StringWriter().also { values.store(it, null) }.toString()
    }

    fun decode(encoded: String?): EditorSession? {
        if (encoded == null || encoded.length > 256_000) return null
        return runCatching {
            val values = Properties().apply { load(StringReader(encoded)) }
            if (values.getProperty("version") != "1") return null
            EditorSession(
                sourceUri = values.getProperty("source"),
                edit = readEdit(values, "edit"),
                history = List(values.int("history", 0).coerceIn(0, EditHistory.MAX_ENTRIES)) {
                    readEdit(values, "undo.$it")
                },
                tool = values.enumValue("tool", EditorTool.Effects),
                panel = values.enumValue("panel", PanelStage.Categories),
                effectShelf = values.getProperty("effectShelf"),
                filterShelf = values.getProperty("filterShelf"),
                filterControl = values.enumValue("filterControl", FilterControl.Strength),
                adjustCategory = values.enumValue("adjustCategory", AdjustCategory.Light),
                adjustControl = values.enumValue("adjustControl", AdjustControl.Exposure),
                hslBand = values.enumValue("hslBand", HslBand.Red),
                hslComponent = values.enumValue("hslComponent", HslComponent.Hue),
                exportOptions = if (values.containsKey("resolution")) ExportOptions(
                    resolution = values.enumValue("resolution", ExportResolution.FOUR_K),
                    quality = values.enumValue("quality", ExportQuality.HIGH),
                    metadata = values.enumValue("metadata", ExportMetadataMode.OVERRIDE),
                ) else null,
                exportSheet = values.getProperty("exportSheet").toBoolean(),
            )
        }.getOrNull()
    }

    private fun writeEdit(values: Properties, prefix: String, edit: EditSnapshot) {
        edit.effect?.let {
            values.setProperty("$prefix.effect", it.effectId)
            values.setProperty("$prefix.effectIntensity", it.intensity.toString())
        }
        edit.filter?.let {
            values.setProperty("$prefix.filter", it.filterId)
            values.setProperty("$prefix.filterValues", listOf(
                it.strength, it.sharpen, it.intensity, it.grain,
                it.highlights, it.shadows, it.aberration,
            ).joinToString(","))
        }
        for (control in AdjustControl.entries) {
            values.setProperty("$prefix.${control.name}", edit.adjust.value(control).toString())
        }
        // Retain neutral-but-mounted spatial layers so restored topology does not jump.
        values.setProperty("$prefix.detailLayer", edit.adjust.layers.detail.toString())
        values.setProperty("$prefix.blurLayer", edit.adjust.layers.blur.toString())
    }

    private fun readEdit(values: Properties, prefix: String): EditSnapshot {
        var adjust = AdjustParams()
        for (control in AdjustControl.entries) {
            adjust = adjust.with(control, values.int("$prefix.${control.name}", control.neutral))
        }
        adjust = adjust.copy(layers = AdjustLayers(
            detail = adjust.layers.detail || values.getProperty("$prefix.detailLayer").toBoolean(),
            blur = adjust.layers.blur || values.getProperty("$prefix.blurLayer").toBoolean(),
        ))
        val controls = values.getProperty("$prefix.filterValues")?.split(',')
            ?.map { it.toIntOrNull() ?: 0 }.orEmpty()
        fun filterValue(index: Int, default: Int = 0, range: IntRange = 0..100) =
            controls.getOrElse(index) { default }.coerceIn(range)
        return EditSnapshot(
            effect = values.getProperty("$prefix.effect")?.let {
                EffectSelection(it, values.int("$prefix.effectIntensity", 100).coerceIn(0, 100))
            },
            filter = values.getProperty("$prefix.filter")?.let {
                FilterSelection(
                    it, filterValue(0, 100), filterValue(1), filterValue(2, 100),
                    filterValue(3), filterValue(4, range = -100..100),
                    filterValue(5, range = -100..100), filterValue(6),
                )
            },
            adjust = adjust,
        )
    }

    private fun Properties.int(key: String, default: Int): Int =
        getProperty(key)?.toIntOrNull() ?: default

    private inline fun <reified T : Enum<T>> Properties.enumValue(key: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == getProperty(key) } ?: default
}
