package com.aliahad.photoeffects

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.MotesEffectFactory
import com.aliahad.photoeffects.effect.MotesFamily
import com.aliahad.photoeffects.effect.gpu.GpuRenderGraph
import com.aliahad.photoeffects.filter.FilterRegistry
import com.aliahad.photoeffects.ui.editor.EditorTool
import com.aliahad.photoeffects.ui.editor.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HoldToCompareInstrumentedTest {

    @Test
    fun renderGraphSurvivesFrameOmissionInHardwareRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = 128
        val height = 128
        val sourcePixels = IntArray(width * height) { Color.rgb(180, 140, 120) }
        val source = Bitmap.createBitmap(sourcePixels, width, height, Bitmap.Config.ARGB_8888)
        val filter = FilterRegistry.byId("halcyon_1")!!
        val programs = GpuRenderGraph.programs(filter.gpu)
        val graph = GpuRenderGraph.build(context, source, programs, width, height)!!
        val hostRoot = RenderNode("HostRoot").apply { setPosition(0, 0, width, height) }
        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            4,
            HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            renderer.setOpaque(false)
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(hostRoot)

            fun drawAndReadCenter(drawGraph: Boolean): Int {
                if (drawGraph) graph.update(programs, 1L, 1f)
                val canvas = hostRoot.beginRecording(width, height)
                try {
                    if (drawGraph) {
                        canvas.drawRenderNode(graph.root)
                    } else {
                        canvas.drawColor(Color.BLUE)
                    }
                } finally {
                    hostRoot.endRecording()
                }
                var status = HardwareRenderer.SYNC_FRAME_DROPPED
                repeat(4) {
                    status = renderer.createRenderRequest()
                        .setVsyncTime(System.nanoTime())
                        .setWaitForPresent(true)
                        .syncAndDraw()
                    if (status != HardwareRenderer.SYNC_FRAME_DROPPED) return@repeat
                    Thread.sleep(20)
                }
                assertTrue("syncAndDraw status=$status", status == HardwareRenderer.SYNC_OK)
                val img = reader.acquireNextImage()!!
                return img.use {
                    it.fence.use { fence -> check(fence.awaitForever()) }
                    val plane = it.planes.single()
                    val buf = plane.buffer
                    val off = (height / 2) * plane.rowStride + (width / 2) * plane.pixelStride
                    val r = buf.get(off).toInt() and 255
                    val g = buf.get(off + 1).toInt() and 255
                    val b = buf.get(off + 2).toInt() and 255
                    val a = buf.get(off + 3).toInt() and 255
                    Color.argb(a, r, g, b)
                }
            }

            val frame1 = drawAndReadCenter(drawGraph = true)
            val frame2 = drawAndReadCenter(drawGraph = false)
            // HWUI discards detached RenderNode display lists after frame omission on device.
            graph.root.discardDisplayList()
            assertFalse(graph.root.hasDisplayList())

            val frame3 = drawAndReadCenter(drawGraph = true)
            assertTrue("frame1 alpha=${Color.alpha(frame1)}", Color.alpha(frame1) > 200)
            assertEquals(Color.BLUE, frame2)
            assertTrue("Display list should be re-recorded by update()", graph.root.hasDisplayList())
            assertEquals("Filtered pixel must match after restoration", frame1, frame3)
        } finally {
            graph.release()
            hostRoot.discardDisplayList()
            renderer.destroy()
            reader.close()
            source.recycle()
        }
    }

    @Test
    fun mainActivityPreviewStaysVisibleAfterHoldToCompare() {
        val inst = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var state: com.aliahad.photoeffects.ui.editor.EditorState
            val halcyon1 = FilterRegistry.byId("halcyon_1")!!
            val halcyon2 = FilterRegistry.byId("halcyon_2")!!
            val motes1 = MotesEffectFactory.create(MotesFamily.presets.first())

            val deadline = System.currentTimeMillis() + 10_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    state = ViewModelProvider(activity)[EditorViewModel::class.java].state
                    ready = state.photo != null && !state.isLoadingPhoto
                }
                if (!ready) Thread.sleep(50)
            }
            assertTrue("Photo did not load", ready)

            fun sampleCenterPatch(): DoubleArray {
                lateinit var shot: Bitmap
                val latch = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    shot = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(
                        activity.window,
                        shot,
                        { result -> if (result == PixelCopy.SUCCESS) latch.countDown() },
                        Handler(Looper.getMainLooper()),
                    )
                }
                assertTrue("PixelCopy timeout", latch.await(5, TimeUnit.SECONDS))
                val cx = shot.width / 2
                val cy = (shot.height * 0.38).toInt()
                var sumLuma = 0.0
                var sumBlueMinusRed = 0.0
                var count = 0
                for (dy in -24..24 step 4) {
                    for (dx in -24..24 step 4) {
                        val c = shot.getPixel(cx + dx, cy + dy)
                        val r = Color.red(c) / 255.0
                        val g = Color.green(c) / 255.0
                        val b = Color.blue(c) / 255.0
                        sumLuma += 0.299 * r + 0.587 * g + 0.114 * b
                        sumBlueMinusRed += b - r
                        count++
                    }
                }
                shot.recycle()
                return doubleArrayOf(sumLuma / count, sumBlueMinusRed / count)
            }

            var touchX = 0f
            var touchY = 0f
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                touchX = decor.width / 2f
                touchY = decor.height * 0.38f
            }

            // Capture unedited baseline at the exact PanelStage.Items card height before testing Halcyon #1.
            scenario.onActivity {
                state.selectTool(EditorTool.Filters)
                state.selectFilter(halcyon1)
                state.clearSelectedPreset()
            }
            inst.waitForIdleSync()
            Thread.sleep(300)
            val uneditedPatch = sampleCenterPatch()

            scenario.onActivity { state.selectFilter(halcyon1) }
            inst.waitForIdleSync()
            Thread.sleep(300)
            val h1Before = sampleCenterPatch()

            val down1 = SystemClock.uptimeMillis()
            inst.sendPointerSync(MotionEvent.obtain(down1, down1, MotionEvent.ACTION_DOWN, touchX, touchY, 0))
            inst.waitForIdleSync()
            Thread.sleep(200)
            var isHolding = false
            scenario.onActivity { isHolding = state.showOriginal }
            assertTrue("Touch hold should set showOriginal=true", isHolding)
            val h1During = sampleCenterPatch()
            assertEquals("Held original luma should match unedited preview", uneditedPatch[0], h1During[0], 0.02)
            assertEquals("Held original tint should match unedited preview", uneditedPatch[1], h1During[1], 0.02)

            val up1 = SystemClock.uptimeMillis()
            inst.sendPointerSync(MotionEvent.obtain(down1, up1, MotionEvent.ACTION_UP, touchX, touchY, 0))
            inst.waitForIdleSync()
            Thread.sleep(300)
            scenario.onActivity { isHolding = state.showOriginal }
            assertFalse("Touch release should clear showOriginal", isHolding)
            val h1After = sampleCenterPatch()
            assertEquals("Released Halcyon #1 luma must match before-hold", h1Before[0], h1After[0], 0.02)
            assertEquals("Released Halcyon #1 tint must match before-hold", h1Before[1], h1After[1], 0.02)

            // Verify full Filter + Adjust + Effect + Canvas stack and same-topology switches stay intact.
            scenario.onActivity {
                state.setAdjustControlValue(AdjustControl.Exposure, 10)
                state.selectEffect(motes1)
                state.selectCanvasRatio(CanvasRatio.POST_4_5)
            }
            inst.waitForIdleSync()
            Thread.sleep(250)
            val stackBefore = sampleCenterPatch()

            scenario.onActivity { state.selectFilter(halcyon2) }
            inst.waitForIdleSync()
            Thread.sleep(250)
            val h2Before = sampleCenterPatch()
            repeat(2) {
                val d = SystemClock.uptimeMillis()
                inst.sendPointerSync(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, touchX, touchY, 0))
                inst.waitForIdleSync()
                Thread.sleep(120)
                val u = SystemClock.uptimeMillis()
                inst.sendPointerSync(MotionEvent.obtain(d, u, MotionEvent.ACTION_UP, touchX, touchY, 0))
                inst.waitForIdleSync()
                Thread.sleep(200)
            }
            val h2After = sampleCenterPatch()
            assertTrue("Combined stack preview must stay visible: ${stackBefore[0]}", stackBefore[0] > 0.25)
            assertEquals("Halcyon #2 luma after repeat holds must stay intact", h2Before[0], h2After[0], 0.02)
            assertEquals("Halcyon #2 tint after repeat holds must stay intact", h2Before[1], h2After[1], 0.02)
        }
    }
}
