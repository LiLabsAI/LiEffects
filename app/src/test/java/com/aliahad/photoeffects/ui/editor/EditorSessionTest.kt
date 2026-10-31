package com.aliahad.photoeffects.ui.editor

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {
    @Test
    fun committingFirstUndoStepInvalidatesItsSnapshotObserver() {
        val history = EditHistory()
        val observer = SnapshotStateObserver { it() }
        var invalidations = 0
        observer.start()
        try {
            observer.observeReads(Unit, { _: Unit -> invalidations++ }) {
                assertFalse(history.canUndo)
            }
            // This models release after the last preview frame: no pixel/control state changes.
            history.record(EditSnapshot(null, null, AdjustParams()))
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations)
            assertTrue(history.canUndo)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun undoStackStaysBoundedAndPopsTheMostRecentCompleteComposition() {
        val history = EditHistory()
        repeat(30) { index ->
            history.record(EditSnapshot(
                EffectSelection("dusty_1", index),
                FilterSelection("insomnia_1", index, 0),
                AdjustParams().with(AdjustControl.Exposure, index),
            ))
        }
        assertEquals(EditHistory.MAX_ENTRIES, history.entries.size)
        assertEquals(6, history.entries.first().effect?.intensity)
        val previous = requireNotNull(history.pop())
        assertEquals(29, previous.effect?.intensity)
        assertEquals(29, previous.filter?.strength)
        assertEquals(29, previous.adjust.value(AdjustControl.Exposure))
        history.clear()
        assertFalse(history.canUndo)
        assertNull(history.pop())
    }

    @Test
    fun processRestorationRoundTripsPhotoAllControlsUndoNavigationAndExport() {
        var adjust = AdjustParams()
        AdjustControl.entries.forEachIndexed { index, control ->
            adjust = adjust.with(control, if (index % 2 == 0) control.range.first else control.range.last)
        }
        val edit = EditSnapshot(
            EffectSelection("yesterday_2", 43),
            FilterSelection("chicago_2", 73, 12, 64, 26, -47, 36, 18),
            adjust,
        )
        val session = EditorSession(
            sourceUri = "content://media/picker/0/a%20photo:1?x=one&y=two",
            edit = edit,
            history = List(24) { edit.copy(effect = edit.effect?.copy(intensity = it)) },
            tool = EditorTool.Adjust,
            panel = PanelStage.Edit,
            effectShelf = "Yesterday",
            filterShelf = "chicago",
            filterControl = FilterControl.Aberration,
            adjustCategory = AdjustCategory.Hsl,
            adjustControl = AdjustControl.HslPurpleLightness,
            hslBand = HslBand.Purple,
            hslComponent = HslComponent.Lightness,
            exportOptions = ExportOptions(
                ExportResolution.HD, ExportQuality.MEDIUM, ExportMetadataMode.KEEP,
            ),
            exportSheet = true,
        )
        val encoded = EditorSessionCodec.encode(session)
        assertTrue("Session must fit comfortably in saved instance state", encoded.length < 100_000)
        assertEquals(session, EditorSessionCodec.decode(encoded))
    }

    @Test
    fun restorationRetainsMountedSpatialLayersEvenWhenTheirControlsAreNeutral() {
        val session = EditorSession(edit = EditSnapshot(
            null, null, AdjustParams(layers = AdjustLayers(detail = true, blur = true)),
        ))
        assertEquals(session, EditorSessionCodec.decode(EditorSessionCodec.encode(session)))
    }

    @Test
    fun restorationDefaultsUnknownNavigationAndClampsControlValues() {
        val restored = requireNotNull(EditorSessionCodec.decode("""
            version=1
            tool=FutureTool
            panel=FuturePanel
            edit.Exposure=99999
            edit.FocusPoint=-999
            history=999
        """.trimIndent()))
        assertEquals(EditorTool.Effects, restored.tool)
        assertEquals(PanelStage.Categories, restored.panel)
        assertEquals(100, restored.edit.adjust.value(AdjustControl.Exposure))
        assertEquals(0, restored.edit.adjust.value(AdjustControl.FocusPoint))
        assertEquals(EditHistory.MAX_ENTRIES, restored.history.size)
    }

    @Test
    fun unsupportedOrMalformedSavedSessionsAreIgnored() {
        assertNull(EditorSessionCodec.decode(null))
        assertNull(EditorSessionCodec.decode("version=999"))
        assertNull(EditorSessionCodec.decode("version=1\nbad=\\uZZZZ"))
        assertNull(EditorSessionCodec.decode("x".repeat(256_001)))
    }
}
