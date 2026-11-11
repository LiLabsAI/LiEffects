package com.aliahad.photoeffects.effect

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Host-side (JVM) preview sheet renderer.
 *
 * Decodes the bundled demo photo, renders all five presets at intensity 100 (plus Motes #1 at
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
        for (effect in MotesFamily.effects) {
            val rendered = effect.cpu.apply(
                pixels, w, h, effect.defaultIntensity / 100f, photoSeed,
            )
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            tiles.add(effect.id to image)
        }
        val first = MotesFamily.effects.first()
        val half = first.cpu.apply(pixels, w, h, 0.5f, photoSeed)
        val halfImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        halfImage.setRGB(0, 0, w, h, half, 0, w)
        ImageIO.write(halfImage, "png", File(outDir, "${first.id}_50.png"))
        tiles.add("${first.id}_50" to halfImage)

        // Contact sheet: 100% renders of all 5 MOTES presets in card order.
        writeSheet(tiles.filterNot { it.first.endsWith("_50") }.map { it.second }, outDir, "sheet.png")
        // Second sheet with the intensity-50 render included.
        writeSheet(tiles.map { it.second }, outDir, "sheet_with_50.png")

        // LUMEN family (final measured rows): local sanity renders, written to
        // build/effect-preview/ so they are never mistaken for final device evidence.
        val lumenTiles = mutableListOf<BufferedImage>()
        for (effect in LumenFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            lumenTiles.add(image)
        }
        writeSheet(lumenTiles, outDir, "sheet_halation.png")

        // DAYMARK (single rewritten preset, with dot-matrix date stamp).
        val daymarkTiles = mutableListOf<BufferedImage>()
        for (effect in DaymarkFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            daymarkTiles.add(image)
        }
        writeSheet(daymarkTiles, outDir, "sheet_yesterday.png")

        // VELOUR (5 rewritten variants) — needs the bundled radial blur mask.
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
        val velourTiles = mutableListOf<BufferedImage>()
        for (effect in VelourFamily.effects) {
            val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, photoSeed)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, rendered, 0, w)
            ImageIO.write(image, "png", File(outDir, "${effect.id}_100.png"))
            velourTiles.add(image)
        }
        writeSheet(velourTiles, outDir, "sheet_sensual.png")

        // CPU export-resolution renders (960x1440 max-dim 1440) for numeric comparison with the
        // Full-resolution renders, matching what Save writes to the gallery.
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
        for (effect in DaymarkFamily.effects + VelourFamily.effects) {
            val rendered = effect.cpu.apply(
                exportPixels, ew, eh, effect.defaultIntensity / 100f, photoSeed,
            )
            val image = BufferedImage(ew, eh, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, ew, eh, rendered, 0, ew)
            ImageIO.write(image, "png", File(exportDir, "${effect.id}.png"))
        }
    }

    @Test
    fun renderLuxuryPreviewSheet() {
        val source = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg"))
        val scale = 720f / max(source.width, source.height)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            drawImage(source, 0, 0, w, h, null)
            dispose()
        }
        val pixels = image.getRGB(0, 0, w, h, null, 0, w)
        val outDir = File("build/effect-preview").apply { mkdirs() }
        val looks = NocturneFamily.effects + listOf("black_tie_1", "old_money_1").map {
            requireNotNull(com.aliahad.photoeffects.filter.FilterRegistry.byId(it))
        }
        val tiles = listOf(image) + looks.map { effect ->
            val out = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, 42L)
            BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
                setRGB(0, 0, w, h, out, 0, w)
                ImageIO.write(this, "png", File(outDir, "${effect.id}.png"))
            }
        }
        writeSheet(tiles, outDir, "sheet_luxury.png")
    }

    @Test
    fun renderMarqueePreviewSheet() {
        val source = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg"))
        val scale = 720f / max(source.width, source.height)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            drawImage(source, 0, 0, w, h, null)
            dispose()
        }
        val pixels = image.getRGB(0, 0, w, h, null, 0, w)
        val outDir = File("build/effect-preview").apply { mkdirs() }
        // Side by side with the closest existing cool looks, to check the grade is distinct.
        val looks = MarqueeFamily.effects +
            listOf("nocturne_afterhours").map { requireNotNull(EffectRegistry.byId(it)) } +
            listOf("nightfall_2", "estuary_2").map {
                requireNotNull(com.aliahad.photoeffects.filter.FilterRegistry.byId(it))
            }
        val tiles = listOf(image) + looks.map { effect ->
            val out = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, 42L)
            BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
                setRGB(0, 0, w, h, out, 0, w)
                ImageIO.write(this, "png", File(outDir, "${effect.id}.png"))
            }
        }
        writeSheet(tiles, outDir, "sheet_marquee.png")
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
