package com.aliahad.photoeffects.effect

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Host-side (JVM) preview sheet renderer.
 *
 * Decodes the bundled demo photo, renders all five presets at intensity 100 (plus Dusty #1 at
 * 50) through the deterministic CPU pipeline and writes PNGs to `build/effect-preview/`.
 *
 * This is the fast iteration loop for tuning the 5 parameter rows: on-device GPU screenshots
 * mirror the same math but require install + manual taps.
 */
class PreviewSheetTest {

    @Test
    fun renderPreviewSheet() {
        val stream = javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg")
            ?: error("demo_photo.jpg missing from test resources")
        val source = ImageIO.read(stream)

        val maxDim = 1080
        val scale = maxDim.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            drawImage(source, 0, 0, w, h, null)
            dispose()
        }
        val pixels = IntArray(w * h)
        scaled.getRGB(0, 0, w, h, pixels, 0, w)
        val photoSeed = 123456789L

        val outDir = File("build/effect-preview").apply { mkdirs() }
        val tiles = mutableListOf<Pair<String, BufferedImage>>()
        for (effect in DustyFamily.effects) {
            val rendered = effect.cpu.apply(
                pixels, w, h, effect.defaultIntensity / 100f, photoSeed,
            )
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            tiles.add(effect.id to image)
        }
        val first = DustyFamily.effects.first()
        val half = first.cpu.apply(pixels, w, h, 0.5f, photoSeed)
        val halfImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        halfImage.setRGB(0, 0, w, h, half, 0, w)
        ImageIO.write(halfImage, "png", File(outDir, "${first.id}_50.png"))
        tiles.add("${first.id}_50" to halfImage)

        // Contact sheet: 100% renders of all 5 DUSTY presets in card order.
        writeSheet(tiles.filterNot { it.first.endsWith("_50") }.map { it.second }, outDir, "sheet.png")
        // Second sheet with the intensity-50 render included.
        writeSheet(tiles.map { it.second }, outDir, "sheet_with_50.png")

        // HALATION family (final measured rows): local sanity renders, written to
        // build/effect-preview/ so they are never mistaken for final device evidence.
        val halationTiles = mutableListOf<BufferedImage>()
        for (effect in HalationFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            halationTiles.add(image)
        }
        writeSheet(halationTiles, outDir, "sheet_halation.png")

        // YESTERDAY (single rewritten preset, with dot-matrix date stamp).
        val yesterdayTiles = mutableListOf<BufferedImage>()
        for (effect in YesterdayFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            yesterdayTiles.add(image)
        }
        writeSheet(yesterdayTiles, outDir, "sheet_yesterday.png")

        // SENSUAL (5 rewritten variants) — needs the bundled PREQUEL adj-Blur mask.
        val maskFile = File("src/main/assets/blurmask.jpg")
        if (maskFile.exists()) {
            val maskImg = ImageIO.read(maskFile)
            val maskSize = 128
            val maskValues = FloatArray(maskSize * maskSize)
            for (my in 0 until maskSize) {
                for (mx in 0 until maskSize) {
                    val sx = (mx.toFloat() / maskSize * maskImg.width).toInt()
                        .coerceAtMost(maskImg.width - 1)
                    val sy = (my.toFloat() / maskSize * maskImg.height).toInt()
                        .coerceAtMost(maskImg.height - 1)
                    maskValues[my * maskSize + mx] = ((maskImg.getRGB(sx, sy) shr 16) and 0xFF) / 255f
                }
            }
            com.aliahad.photoeffects.effect.cpu.BlurMaskStore.current =
                com.aliahad.photoeffects.effect.cpu.BlurMaskData(maskValues, maskSize, maskSize)
        }
        val sensualTiles = mutableListOf<BufferedImage>()
        for (effect in SensualFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            sensualTiles.add(image)
        }
        writeSheet(sensualTiles, outDir, "sheet_sensual.png")

        // CPU export-resolution renders (960x1440 max-dim 1440) for numeric comparison with the
        // PREQUEL full-res exports, matching what Save writes to the gallery.
        val exportDir = File("build/effect-preview/export-960").apply { mkdirs() }
        val exportScale = 1440f / maxOf(source.width, source.height).toFloat()
        val ew = (source.width * exportScale).toInt()
        val eh = (source.height * exportScale).toInt()
        val exportImage = BufferedImage(ew, eh, BufferedImage.TYPE_INT_RGB)
        exportImage.createGraphics().apply {
            drawImage(source, 0, 0, ew, eh, null)
            dispose()
        }
        val exportPixels = IntArray(ew * eh)
        exportImage.getRGB(0, 0, ew, eh, exportPixels, 0, ew)
        for (effect in YesterdayFamily.effects + SensualFamily.effects) {
            val rendered = effect.cpu.apply(
                exportPixels, ew, eh, effect.defaultIntensity / 100f, photoSeed,
            )
            val image = BufferedImage(ew, eh, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, ew, eh, rendered, 0, ew)
            ImageIO.write(image, "png", File(exportDir, "${effect.id}.png"))
        }
    }

    private fun writeSheet(tiles: List<BufferedImage>, outDir: File, name: String) {
        if (tiles.isEmpty()) return
        val w = tiles.first().width
        val h = tiles.first().height
        val sheet = BufferedImage(w * tiles.size, h, BufferedImage.TYPE_INT_RGB)
        sheet.createGraphics().apply {
            tiles.forEachIndexed { index, tile -> drawImage(tile, index * w, 0, null) }
            dispose()
        }
        ImageIO.write(sheet, "png", File(outDir, name))
    }
}
