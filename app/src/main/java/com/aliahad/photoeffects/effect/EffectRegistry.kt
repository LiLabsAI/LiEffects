/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliahad.photoeffects.effect

/**
 * Effect catalogue, organised as shelves.
 *
 * Families own a render pipeline and appear first:
 *   MOTES (5 presets) · LUMEN (3 presets) · DAYMARK (1 preset) · VELOUR (5 presets) ·
 *   PAPERLIGHT (5 presets) · PRISM (6 presets) · NOCTURNE (2 presets) · MARQUEE (2 presets) ·
 *   SOLSTICE (3 presets) · SCREENPRINT (3 presets)
 * followed by photo-editor category shelves cross-listing presets from all families:
 *   FOR YOU · TRENDS · NEW · FILMS
 *
 * RETUNING: edit the parameter rows in [MotesFamily] / [LumenFamily] / [DaymarkFamily] /
 * [VelourFamily] / [PaperlightFamily] / [PrismFamily] / [NocturneFamily] / [MarqueeFamily] /
 * [SolsticeFamily] / [ScreenprintFamily] only.
 */
object EffectRegistry {

    /** Family shelves, in chip order. */
    val families: List<EffectFamily> = listOf(
        EffectFamily(MotesFamily.ID, "MOTES", MotesFamily.effects),
        EffectFamily(LumenFamily.ID, "LUMEN", LumenFamily.effects),
        EffectFamily(DaymarkFamily.ID, "DAYMARK", DaymarkFamily.effects),
        EffectFamily(VelourFamily.ID, "VELOUR", VelourFamily.effects),
        EffectFamily(PaperlightFamily.ID, "PAPERLIGHT", PaperlightFamily.effects),
        EffectFamily(PrismFamily.ID, "PRISM", PrismFamily.effects),
        EffectFamily(NocturneFamily.ID, "NOCTURNE", NocturneFamily.effects),
        EffectFamily(MarqueeFamily.ID, "MARQUEE", MarqueeFamily.effects),
        EffectFamily(SolsticeFamily.ID, "SOLSTICE", SolsticeFamily.effects),
        EffectFamily(ScreenprintFamily.ID, "SCREENPRINT", ScreenprintFamily.effects),
    )

    /** Every effect across all families (card world + thumbnails + lookups). */
    val all: List<PhotoEffect> = families.flatMap { it.effects }

    /** Category shelves aggregating cross-listed presets from every family. */
    val categoryShelves: List<EffectShelf> = EffectCategory.entries.map { category ->
        EffectShelf(
            id = category.name,
            title = category.label,
            effects = all.filter { category in it.spec.categories },
            isFamily = false,
        )
    }

    /** Chip order: family shelves first, then category shelves. */
    val shelves: List<EffectShelf> = families.map { family ->
        EffectShelf(
            id = family.id,
            title = family.title,
            effects = family.effects,
            isFamily = true,
        )
    } + categoryShelves

    /** Shelf shown when the Effects panel opens (MOTES). */
    val defaultShelf: EffectShelf = shelves.first()

    fun shelfById(id: String): EffectShelf? = shelves.firstOrNull { it.id == id }

    fun byId(id: String): PhotoEffect? = all.firstOrNull { it.id == id }

    fun forCategory(category: EffectCategory): List<PhotoEffect> =
        all.filter { category in it.spec.categories }

    /** Number of MOTES pack presets (5). */
    val motesPresetsCount: Int get() = MotesFamily.presets.size

    /** Number of LUMEN variants (3). */
    val lumenPresetsCount: Int get() = LumenFamily.presets.size

    /** Number of DAYMARK variants (1). */
    val daymarkPresetsCount: Int get() = DaymarkFamily.presets.size

    /** Number of VELOUR variants (5). */
    val velourPresetsCount: Int get() = VelourFamily.presets.size

    /** Number of PAPERLIGHT variants (5). */
    val paperlightPresetsCount: Int get() = PaperlightFamily.presets.size

    /** Number of PRISM variants (6). */
    val prismPresetsCount: Int get() = PrismFamily.presets.size

    /** Preset applied when the user opens the MOTES shelf. */
    val defaultEffect: PhotoEffect = MotesFamily.effects.first()

    /** Convenience lists used by tests/UI. */
    val effects: List<PhotoEffect> get() = all
}
