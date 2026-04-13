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
import org.isoron.uhabits.core.models.Entry
import kotlin.js.JsExport

@JsExport
data class CheckmarkButtonState(
    val value: Int,
    val color: Color,
    val theme: Theme,
    val showQuestionMark: Boolean = false,
    val notes: String = ""
)

@JsExport
class CheckmarkButton(
    val state: CheckmarkButtonState
) : View {
    override fun draw(canvas: Canvas) {
        val value = state.value
        val color = state.color
        val theme = state.theme
        val showQuestionMark = state.showQuestionMark
        val notes = state.notes

        canvas.setFont(Font.FONT_AWESOME)
        canvas.setTextAlign(TextAlign.CENTER)

        val iconColor = when (value) {
            Entry.YES_MANUAL, Entry.YES_AUTO, Entry.SKIP -> color
            Entry.NO -> {
                if (showQuestionMark) {
                    theme.mediumContrastTextColor
                } else {
                    theme.lowContrastTextColor
                }
            }
            else -> theme.lowContrastTextColor
        }
        canvas.setColor(iconColor)

        val icon = when (value) {
            Entry.SKIP -> FontAwesome.SKIP
            Entry.NO -> FontAwesome.TIMES
            Entry.UNKNOWN -> {
                if (showQuestionMark) {
                    FontAwesome.QUESTION
                } else {
                    FontAwesome.TIMES
                }
            }
            else -> FontAwesome.CHECK
        }

        val fontSize = when {
            icon == FontAwesome.QUESTION -> theme.smallTextSize * 1.2
            value == Entry.YES_AUTO -> theme.smallTextSize * 1.3
            else -> theme.smallTextSize * 1.4
        }
        canvas.setFontSize(fontSize)

        val em = canvas.measureText("m")
        val centerX = canvas.getWidth() / 2.0
        val centerY = canvas.getHeight() / 2.0

        if (value == Entry.YES_AUTO) {
            canvas.setStrokeWidth(2.5)
            canvas.setTextStyle(TextStyle.STROKE)
            canvas.drawText(icon, centerX, centerY)

            canvas.setColor(theme.cardBackgroundColor)
            canvas.setTextStyle(TextStyle.FILL)
            canvas.drawText(icon, centerX, centerY)
        } else {
            canvas.setStrokeWidth(0.0)
            canvas.setTextStyle(TextStyle.FILL)
            canvas.drawText(icon, centerX, centerY)
        }

        if (notes.isNotBlank()) {
            val cy = 0.8 * em
            canvas.setColor(color)
            canvas.fillCircle(canvas.getWidth() - cy, cy, 3.0)
        }
    }
}
