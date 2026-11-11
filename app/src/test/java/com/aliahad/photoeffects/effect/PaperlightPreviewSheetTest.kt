package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.PaperlightFrameData
import com.aliahad.photoeffects.effect.cpu.PaperlightFrameStore
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Host-side preview sheet for the PAPERLIGHT family: renders the five presets on the bundled demo
 * photo at export-ish size and writes PNGs + a contact sheet to `build/effect-preview/`.
 * Fast tuning loop for the parameter rows; the on-device GPU preview mirrors the same math.
 */
class PaperlightPreviewSheetTest {

    @Test
    fun renderPaperlightPreviewSheet() {
        PaperlightFrameStore.clear()
        for (params in PaperlightFamily.presets) {
            val img = ImageIO.read(File("src/main/assets/${params.frameAsset}"))
            val pixels = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, pixels, 0, img.width)
            PaperlightFrameStore.put(params.frameAsset, PaperlightFrameData(pixels, img.width, img.height))
        }

        val source = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg"))
        val maxDim = 540
        val scale = maxDim.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt()
        val h = (source.height * scale).roundToInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            drawImage(source, 0, 0, w, h, null)
            dispose()
        }
        val pixels = IntArray(w * h)
        scaled.getRGB(0, 0, w, h, pixels, 0, w)

        val outDir = File("build/effect-preview").apply { mkdirs() }
        val tiles = mutableListOf<BufferedImage>()
        for (effect in PaperlightFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, 123456789L)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            tiles.add(image)
        }
        val sheet = BufferedImage(tiles.size * (w + 8) + 8, h + 16, BufferedImage.TYPE_INT_RGB)
        sheet.createGraphics().apply {
            color = java.awt.Color(24, 24, 26)
            fillRect(0, 0, sheet.width, sheet.height)
            tiles.forEachIndexed { i, tile -> drawImage(tile, 8 + i * (w + 8), 8, null) }
            dispose()
        }
        ImageIO.write(sheet, "png", File(outDir, "paperlight_sheet.png"))
    }
}
