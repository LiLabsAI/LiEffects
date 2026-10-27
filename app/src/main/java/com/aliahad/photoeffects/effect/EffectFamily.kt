package com.aliahad.photoeffects.effect

/**
 * One titled presets family rendered by a single pipeline.
 *
 * The app is organised as families (e.g. DUSTY with 5 presets, HALATION with 3 presets); each
 * family is one chip in the Effects panel and its presets are the card row. Families are
 * additive: adding a family does not touch the dusty pipeline.
 */
data class EffectFamily(
    /** Stable id; also used for the chip content-description: `category_<id>`. */
    val id: String,
    /** Human title shown on the chip ("DUSTY", "HALATION"). */
    val title: String,
    val effects: List<PhotoEffect>,
)

/**
 * One chip in the Effects panel: either a [EffectFamily] shelf or a PREQUEL-style category shelf
 * (FOR YOU / TRENDS / NEW / FILMS) aggregating cross-listed presets from all families.
 */
data class EffectShelf(
    val id: String,
    val title: String,
    val effects: List<PhotoEffect>,
    val isFamily: Boolean = false,
)
