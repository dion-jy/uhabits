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
import org.isoron.platform.gui.Font
import org.isoron.platform.gui.TextAlign
import org.isoron.platform.gui.View
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.LocalDateFormatter
import kotlin.js.JsExport

@JsExport
class HabitListHeader(
    private val today: LocalDate,
    private val nButtons: Int,
    private val theme: Theme,
    private val fmt: LocalDateFormatter
) : View {

    override fun draw(canvas: Canvas) {
        val width = canvas.getWidth()
        val height = canvas.getHeight()
        val buttonSize = theme.checkmarkButtonSize
        canvas.setColor(theme.headerTextColor)
        canvas.setFont(Font.BOLD)
        canvas.setFontSize(theme.smallTextSize)
        canvas.setTextAlign(TextAlign.CENTER)

        val em = canvas.measureText("m")

        repeat(nButtons) { index ->
            val date = today.minus(nButtons - index - 1)
            val name = fmt.shortWeekdayName(date).uppercase()
            val number = date.day.toString()

            val x = width - 3.0 - (index + 1) * buttonSize + buttonSize / 2
            val y = height / 2
            canvas.drawText(name, x, y - 0.25 * em)
            canvas.drawText(number, x, y + 1.25 * em)
        }
    }
}
