package com.aliahad.photoeffects.effect

import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Actual CPU outputs for visual review, using the same bundled photo as the editor. */
class CreativeEffectsPreviewTest {
    @Test fun renderBothFamiliesAtDefaultIntensity() {
        val source = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("demo_photo.jpg"))
        val w = 427
        val h = (w.toDouble() * source.height / source.width).toInt()
        val photo = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        photo.createGraphics().apply { drawImage(source, 0, 0, w, h, null); dispose() }
        val pixels = photo.getRGB(0, 0, w, h, null, 0, w)
        val out = File("build/effect-preview/creative").apply { mkdirs() }
        val sheet = BufferedImage(w * 3, (h + 42) * 2, BufferedImage.TYPE_INT_RGB)
        val graphics = sheet.createGraphics()
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
        try {
            for ((index, effect) in (SolsticeFamily.effects + ScreenprintFamily.effects).withIndex()) {
                val rendered = effect.cpu.apply(pixels, w, h, effect.defaultIntensity / 100f, 123456789L)
                val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                image.setRGB(0, 0, w, h, rendered, 0, w)
                ImageIO.write(image, "png", File(out, "${effect.id}.png"))
                val x = index % 3 * w
                val y = index / 3 * (h + 42)
                graphics.color = Color(24, 24, 28)
                graphics.fillRect(x, y, w, h + 42)
                graphics.drawImage(image, x, y + 42, null)
                graphics.color = Color.WHITE
                graphics.drawString("${effect.spec.familyId} · ${effect.displayName}", x + 12, y + 27)
            }
        } finally { graphics.dispose() }
        ImageIO.write(sheet, "png", File(out, "families.png"))
    }
}
