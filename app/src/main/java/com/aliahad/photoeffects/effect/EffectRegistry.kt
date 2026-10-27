package com.aliahad.photoeffects.effect

/**
 * Effect catalogue, organised as shelves.
 *
 * Families own a render pipeline and appear first:
 *   DUSTY (5 presets) · HALATION (3 presets) · YESTERDAY (1 preset) · SENSUAL (5 presets)
 * followed by PREQUEL-style category shelves cross-listing presets from all families:
 *   FOR YOU · TRENDS · NEW · FILMS
 *
 * RETUNING: edit the parameter rows in [DustyFamily] / [HalationFamily] / [YesterdayFamily] /
 * [SensualFamily] only.
 */
object EffectRegistry {

    /** Family shelves, in chip order. */
    val families: List<EffectFamily> = listOf(
        EffectFamily(DustyFamily.ID, "DUSTY", DustyFamily.effects),
        EffectFamily(HalationFamily.ID, "HALATION", HalationFamily.effects),
        EffectFamily(YesterdayFamily.ID, "YESTERDAY", YesterdayFamily.effects),
        EffectFamily(SensualFamily.ID, "SENSUAL", SensualFamily.effects),
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

    /** Shelf shown when the Effects panel opens (DUSTY). */
    val defaultShelf: EffectShelf = shelves.first()

    fun shelfById(id: String): EffectShelf? = shelves.firstOrNull { it.id == id }

    fun byId(id: String): PhotoEffect? = all.firstOrNull { it.id == id }

    fun forCategory(category: EffectCategory): List<PhotoEffect> =
        all.filter { category in it.spec.categories }

    /** Number of DUSTY pack presets (5). */
    val dustyPresetsCount: Int get() = DustyFamily.presets.size

    /** Number of HALATION variants (3). */
    val halationPresetsCount: Int get() = HalationFamily.presets.size

    /** Number of YESTERDAY variants (3). */
    val yesterdayPresetsCount: Int get() = YesterdayFamily.presets.size

    /** Number of SENSUAL variants (5). */
    val sensualPresetsCount: Int get() = SensualFamily.presets.size

    /** Preset applied when the user opens the DUSTY shelf. */
    val defaultEffect: PhotoEffect = DustyFamily.effects.first()

    /** Convenience lists used by tests/UI. */
    val effects: List<PhotoEffect> get() = all
}
