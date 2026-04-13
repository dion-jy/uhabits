/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.core.ui.views

import org.isoron.platform.gui.Canvas
import org.isoron.platform.gui.Color
import org.isoron.platform.gui.Font
import org.isoron.platform.gui.FontAwesome
import org.isoron.platform.gui.TextAlign
import org.isoron.platform.gui.TextStyle
import org.isoron.platform.gui.View
import org.isoron.platform.io.format
import org.isoron.platform.io.formatLocaleDecimal
import org.isoron.uhabits.core.models.Entry
import kotlin.js.JsExport

fun Double.toShortString(): String = when {
    this >= 1e9 -> format("%.1f", this / 1e9) + "G"
    this >= 1e8 -> format("%.0f", this / 1e6) + "M"
    this >= 1e6 -> format("%.1f", this / 1e6) + "M"
    this >= 1e5 -> format("%.0f", this / 1e3) + "k"
    this >= 1e3 -> format("%.1f", this / 1e3) + "k"
    this >= 1e2 -> formatLocaleDecimal(this, 0)
    this >= 1e1 -> formatLocaleDecimal(this, 1)
    else -> formatLocaleDecimal(this, 2)
}

@JsExport
data class NumberButtonState(
    val value: Double,
    val color: Color,
    val threshold: Double,
    val targetType: Int,
    val units: String,
    val theme: Theme,
    val showQuestionMark: Boolean = false,
    val notes: String = ""
)

@JsExport
class NumberButton(
    val state: NumberButtonState
) : View {

    override fun draw(canvas: Canvas) {
        val value = state.value
        val color = state.color
        val threshold = state.threshold
        val targetType = state.targetType
        val units = state.units
        val theme = state.theme
        val showQuestionMark = state.showQuestionMark
        val notes = state.notes

        val width = canvas.getWidth()
        val height = canvas.getHeight()

        val numberFontSize = theme.smallTextSize * 1.4
        val unitFontSize = theme.smallTextSize * 1.2
        val questionFontSize = unitFontSize

        canvas.setTextAlign(TextAlign.CENTER)
        canvas.setStrokeWidth(0.0)
        canvas.setTextStyle(TextStyle.FILL)

        val activeColor = when {
            value < 0.0 -> theme.lowContrastTextColor
            targetType == 0 && value >= threshold -> color // AT_LEAST
            targetType == 1 && value <= threshold -> color // AT_MOST
            else -> theme.mediumContrastTextColor
        }
        canvas.setColor(activeColor)

        val skipValue = Entry.SKIP.toDouble() / 1000

        val isSkip = value == skipValue
        val isUnknown = value < 0.0

        val label: String
        val font: Font
        val fontSize: Double

        when {
            isSkip -> {
                label = FontAwesome.SKIP
                font = Font.FONT_AWESOME
                fontSize = numberFontSize
            }
            value >= 0 -> {
                label = value.toShortString()
                font = Font.BOLD_CONDENSED
                fontSize = numberFontSize
            }
            isUnknown && showQuestionMark -> {
                label = FontAwesome.QUESTION
                font = Font.FONT_AWESOME
                fontSize = questionFontSize
            }
            else -> {
                label = "0"
                font = Font.BOLD_CONDENSED
                fontSize = numberFontSize
            }
        }

        canvas.setFont(Font.BOLD_CONDENSED)
        canvas.setFontSize(numberFontSize)
        val em = canvas.measureText("m")

        canvas.setFont(font)
        canvas.setFontSize(fontSize)

        if (units.isBlank()) {
            canvas.drawText(label, width / 2, height / 2 + 0.1 * em)
        } else {
            canvas.drawText(label, width / 2, height / 2 - 0.4 * em)

            canvas.setFont(Font.CONDENSED)
            canvas.setFontSize(unitFontSize)
            val emUnit = canvas.measureText("m")
            var trimmedUnits = units
            while (trimmedUnits.length > 2 && canvas.measureText(trimmedUnits) > width * 0.9) {
                trimmedUnits = trimmedUnits.dropLast(2) + "\u2026"
            }
            canvas.drawText(trimmedUnits, width / 2, height / 2 + 1.3 * em - 0.4 * emUnit)
        }

        if (notes.isNotBlank()) {
            val cy = 0.8 * em
            canvas.setColor(color)
            canvas.fillCircle(width - cy, cy, 3.0)
        }
    }
}
