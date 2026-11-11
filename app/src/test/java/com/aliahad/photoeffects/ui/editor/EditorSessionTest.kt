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
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.canvas.CanvasControl
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
                EffectSelection("motes_1", index),
                FilterSelection("driftglass_1", index, 0),
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
            EffectSelection("daymark_2", 43),
            FilterSelection("graphite_2", 73, 12, 64, 26, -47, 36, 18),
            adjust,
            CanvasParams(CanvasRatio.POST_4_5, 0.4, 0.6, 2.25, 3, -12.5, true),
        )
        val session = EditorSession(
            sourceUri = "content://media/picker/0/a%20photo:1?x=one&y=two",
            edit = edit,
            history = List(24) { edit.copy(effect = edit.effect?.copy(intensity = it)) },
            tool = EditorTool.Adjust,
            panel = PanelStage.Edit,
            effectShelf = "Daymark",
            filterShelf = "graphite",
            filterControl = FilterControl.Aberration,
            adjustCategory = AdjustCategory.Hsl,
            adjustControl = AdjustControl.HslPurpleLightness,
            hslBand = HslBand.Purple,
            hslComponent = HslComponent.Lightness,
            exportOptions = ExportOptions(
                ExportResolution.HD, ExportQuality.MEDIUM, ExportMetadataMode.KEEP,
            ),
            exportSheet = true,
            canvasControl = CanvasControl.Rotate,
        )
        val encoded = EditorSessionCodec.encode(session)
        assertTrue("Session must fit comfortably in saved instance state", encoded.length < 100_000)
        assertEquals(session, EditorSessionCodec.decode(encoded))
    }

    @Test
    fun luxuryLooksRoundTripWithNavigationAndUndo() {
        for ((effectId, filterId, filterShelf) in listOf(
            Triple("nocturne_gilded", "old_money_1", "Old Money"),
            Triple("nocturne_afterhours", "black_tie_1", "Black Tie"),
        )) {
            val edit = EditSnapshot(EffectSelection(effectId, 63), FilterSelection(filterId, 78, 0), AdjustParams())
            val session = EditorSession(
                edit = edit,
                history = listOf(edit, edit.copy(effect = null)),
                tool = EditorTool.Effects,
                panel = PanelStage.Edit,
                effectShelf = "Nocturne",
                filterShelf = filterShelf,
            )
            assertEquals(session, EditorSessionCodec.decode(EditorSessionCodec.encode(session)))
            assertEquals(effectId, com.aliahad.photoeffects.effect.EffectRegistry.byId(effectId)?.id)
            assertEquals(filterId, com.aliahad.photoeffects.filter.FilterRegistry.byId(filterId)?.id)
        }
    }

    @Test
    fun marqueeLooksRoundTripWithNavigationAndUndo() {
        for ((effectId, filterId, filterShelf) in listOf(
            Triple("marquee_premiere", "estuary_2", "Estuary"),
            Triple("marquee_midnight", "nightfall_2", "Nightfall"),
        )) {
            val edit = EditSnapshot(EffectSelection(effectId, 72), FilterSelection(filterId, 55, 0), AdjustParams())
            val session = EditorSession(
                edit = edit,
                history = listOf(edit, edit.copy(effect = null)),
                tool = EditorTool.Effects,
                panel = PanelStage.Edit,
                effectShelf = "Marquee",
                filterShelf = filterShelf,
            )
            assertEquals(session, EditorSessionCodec.decode(EditorSessionCodec.encode(session)))
            assertEquals(effectId, com.aliahad.photoeffects.effect.EffectRegistry.byId(effectId)?.id)
            assertEquals(filterId, com.aliahad.photoeffects.filter.FilterRegistry.byId(filterId)?.id)
        }
    }

    @Test
    fun whitesAndColorDenoiseRoundTripCurrentEditAndUndo() {
        val current = AdjustParams()
            .with(AdjustControl.Whites, 63)
            .with(AdjustControl.ChromaDenoise, 72)
        val previous = AdjustParams()
            .with(AdjustControl.Whites, -41)
            .with(AdjustControl.ChromaDenoise, 28)
        val session = EditorSession(
            edit = EditSnapshot(null, null, current),
            history = listOf(EditSnapshot(null, null, previous)),
            tool = EditorTool.Adjust,
            panel = PanelStage.Edit,
            adjustCategory = AdjustCategory.Details,
            adjustControl = AdjustControl.ChromaDenoise,
        )
        val restored = requireNotNull(EditorSessionCodec.decode(EditorSessionCodec.encode(session)))
        assertEquals(session, restored)
        assertEquals(63, restored.edit.adjust.light.whites)
        assertEquals(72, restored.edit.adjust.details.chromaDenoise)
        assertTrue(restored.edit.adjust.layers.detail)
        val undo = requireNotNull(EditHistory(restored.history).pop())
        assertEquals(previous, undo.adjust)
        assertEquals(-41, undo.adjust.value(AdjustControl.Whites))
        assertEquals(28, undo.adjust.value(AdjustControl.ChromaDenoise))
        assertTrue(undo.adjust.layers.detail)
    }

    @Test
    fun versionOneSessionsWithoutWhitesOrColorDenoiseRestoreNeutralCurrentEditAndUndo() {
        val restored = requireNotNull(EditorSessionCodec.decode("""
            version=1
            edit.Exposure=24
            history=1
            undo.0.Contrast=-17
        """.trimIndent()))
        assertEquals(24, restored.edit.adjust.value(AdjustControl.Exposure))
        assertEquals(1, restored.history.size)
        assertEquals(-17, restored.history.single().adjust.value(AdjustControl.Contrast))
        for (edit in listOf(restored.edit, restored.history.single())) {
            assertEquals(0, edit.adjust.light.whites)
            assertEquals(0, edit.adjust.details.chromaDenoise)
            assertEquals(0, edit.adjust.value(AdjustControl.Whites))
            assertEquals(0, edit.adjust.value(AdjustControl.ChromaDenoise))
            assertFalse(edit.adjust.layers.detail)
        }
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

    @Test fun olderSessionsRestoreWithAnUnchangedCanvas() {
        val session = requireNotNull(EditorSessionCodec.decode("version=1\nedit.Exposure=24"))
        assertTrue(session.edit.canvas.isIdentity)
        assertEquals(CanvasControl.Ratio, session.canvasControl)
        assertEquals(24, session.edit.adjust.value(AdjustControl.Exposure))
    }
}
