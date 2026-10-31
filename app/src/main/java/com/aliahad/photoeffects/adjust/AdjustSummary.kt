package com.aliahad.photoeffects.adjust

/** Short names for the Adjust panel/composition summary. */
object AdjustSummary {
    fun describe(params: AdjustParams): String {
        val edited = AdjustControl.entries.filter { params.value(it) != it.neutral }
        if (edited.isEmpty()) return "no adjustments"
        val parts = edited.take(3).map { control ->
            val band = AdjustControl.hslBandOf(control)
            val component = AdjustControl.hslComponentOf(control)
            val label = if (band != null && component != null) {
                "${band.label.lowercase()} ${component.label.lowercase()}"
            } else {
                control.label.lowercase()
            }
            "$label ${format(params.value(control), control)}"
        }.toMutableList()
        if (edited.size > 3) parts += "+${edited.size - 3}"
        return parts.joinToString(" · ")
    }

    private fun format(value: Int, control: AdjustControl): String = when {
        value > 0 && control.signed -> "+$value"
        else -> value.toString()
    }
}
