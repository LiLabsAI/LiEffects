package com.aliahad.photoeffects.ui.editor

import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.filter.FilterRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CatalogueMigrationTest {
    @Test
    fun bothSessionVersionsRestoreEveryRenamedEffectAndItsUndoEntry() {
        val families = listOf(
            Triple("Dusty", "Motes", 5), Triple("Halation", "Lumen", 3),
            Triple("Yesterday", "Daymark", 1), Triple("Sensual", "Velour", 5),
            Triple("Kuji", "Paperlight", 5), Triple("Focal", "Prism", 6),
        )
        for (version in 1..2) for ((old, current, count) in families) for (variant in 1..count) {
            val id = "${old.lowercase()}_$variant"
            val expected = EffectSelection("${current.lowercase()}_$variant", 63)
            val restored = requireNotNull(EditorSessionCodec.decode("""
                version=$version
                tool=Effects
                panel=Edit
                effectShelf=$old
                edit.effect=$id
                edit.effectIntensity=63
                history=1
                undo.0.effect=$id
                undo.0.effectIntensity=63
            """.trimIndent()))
            assertEquals(expected, restored.edit.effect)
            assertEquals(expected, restored.history.single().effect)
            assertEquals(current, restored.effectShelf)
            assertNotNull(EffectRegistry.byId(expected.effectId))
            assertNotNull(EffectRegistry.shelfById(restored.effectShelf!!))
            assertEquals(restored, EditorSessionCodec.decode(EditorSessionCodec.encode(restored)))
        }
    }

    @Test
    fun bothSessionVersionsRestoreEveryRenamedFilterWithAllIndependentControls() {
        val families = listOf(
            Triple("G7X", "Lucent", 3), Triple("Dim", "Nightfall", 3),
            Triple("Vanilla", "Marzipan", 3), Triple("Insomnia", "Driftglass", 6),
            Triple("Cine Film", "Lantern", 3), Triple("Chicago", "Graphite", 8),
            Triple("Teal Tone", "Estuary", 3), Triple("Burgundy", "Mulberry", 3),
        )
        for (version in 1..2) for ((old, current, count) in families) for (variant in 1..count) {
            val id = "${old.lowercase().replace(' ', '_')}_$variant"
            val expected = FilterSelection("${current.lowercase()}_$variant", 73, 12, 64, 26, -47, 36, 18)
            val restored = requireNotNull(EditorSessionCodec.decode("""
                version=$version
                tool=Filters
                panel=Edit
                filterShelf=$old
                filterControl=Aberration
                edit.filter=$id
                edit.filterValues=73,12,64,26,-47,36,18
                history=1
                undo.0.filter=$id
                undo.0.filterValues=73,12,64,26,-47,36,18
            """.trimIndent()))
            assertEquals(expected, restored.edit.filter)
            assertEquals(expected, restored.history.single().filter)
            assertEquals(current, restored.filterShelf)
            assertNotNull(FilterRegistry.byId(expected.filterId))
            assertNotNull(FilterRegistry.shelfById(restored.filterShelf!!))
            assertEquals(restored, EditorSessionCodec.decode(EditorSessionCodec.encode(restored)))
        }
    }

    @Test
    fun currentAndUnknownIdentifiersRemainReadableWithoutGuessing() {
        val session = EditorSession(
            edit = EditSnapshot(EffectSelection("future_look_1", 42), FilterSelection("lucent_2", 70, 5),
                com.aliahad.photoeffects.adjust.AdjustParams()),
            effectShelf = "ForYou", filterShelf = "Lucent",
        )
        assertEquals(session, EditorSessionCodec.decode(EditorSessionCodec.encode(session)))
    }
}
