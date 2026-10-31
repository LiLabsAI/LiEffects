package com.aliahad.photoeffects

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliahad.photoeffects.adjust.AdjustCategory
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.ui.editor.EditorTool
import com.aliahad.photoeffects.ui.editor.EditorViewModel
import com.aliahad.photoeffects.ui.editor.PanelStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorLifecycleInstrumentedTest {
    @Test
    fun activityRecreationRetainsEditsNavigationAndUndo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var before: EditorViewModel
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[EditorViewModel::class.java]
                before.state.apply {
                    selectTool(EditorTool.Adjust)
                    selectAdjustCategory(AdjustCategory.Light)
                    selectAdjustControl(AdjustControl.Exposure)
                    beginAdjustDrag()
                    setAdjustValue(37)
                    commitAdjustDrag()
                }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[EditorViewModel::class.java]
                assertSame(before, after)
                assertEquals(37, after.state.adjustValue)
                assertEquals(EditorTool.Adjust, after.state.activeTool)
                assertEquals(PanelStage.Edit, after.state.panelStage)
                assertTrue(after.state.canUndo)
                after.state.undo()
                assertEquals(0, after.state.adjustValue)
            }
        }
    }
}
