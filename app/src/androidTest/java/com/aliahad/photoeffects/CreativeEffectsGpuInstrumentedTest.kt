package com.aliahad.photoeffects

import android.graphics.HardwareRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.diagnostics.CreativeEffectsDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Opaque/transparent endpoints, larger rasters, thin images, and Filter → Adjust → Effect → Canvas. */
@RunWith(AndroidJUnit4::class)
class CreativeEffectsGpuInstrumentedTest {
    @Test fun allSixVariantsMatchTheirCpuMirrorsIncludingCompositionAndCanvas() {
        val drawingEnabled = HardwareRenderer.isDrawingEnabled()
        HardwareRenderer.setDrawingEnabled(true)
        val result = try {
            CreativeEffectsDiagnostics.run(InstrumentationRegistry.getInstrumentation().targetContext)
        } finally { HardwareRenderer.setDrawingEnabled(drawingEnabled) }
        assertEquals(74, result.checked)
        assertEquals(result.toString(), 0, result.failed)
    }
}
