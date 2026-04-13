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

package org.isoron.uhabits.activities.habits.list.views

import android.content.Context
import me.tatarka.inject.annotations.Inject
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Entry.Companion.UNKNOWN
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.views.CheckmarkButtonState
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.utils.currentTheme

@Inject
class CheckmarkPanelViewFactory(
    @ActivityContext val context: Context,
    val preferences: Preferences,
    private val buttonFactory: CheckmarkButtonViewFactory
) {
    fun create() = CheckmarkPanelView(context, preferences, buttonFactory)
}

class CheckmarkPanelView(
    context: Context,
    preferences: Preferences,
    private val buttonFactory: CheckmarkButtonViewFactory
) : ButtonPanelView<CheckmarkButtonView>(context, preferences) {

    var values = IntArray(0)
        set(values) {
            field = values
            setupButtons()
        }

    var color: PaletteColor = PaletteColor(0)
        set(value) {
            field = value
            setupButtons()
        }

    var isArchived: Boolean = false
        set(value) {
            field = value
            setupButtons()
        }

    var notes = arrayOf<String>()
        set(values) {
            field = values
            setupButtons()
        }

    var onToggle: (LocalDate, Int, String) -> Unit = { _, _, _ -> }
        set(value) {
            field = value
            setupButtons()
        }

    var onEdit: (LocalDate) -> Unit = { _ -> }
        set(value) {
            field = value
            setupButtons()
        }

    override fun createButton(): CheckmarkButtonView = buttonFactory.create()

    @Synchronized
    override fun setupButtons() {
        val today = getToday()
        val theme = currentTheme()
        val actualColor = if (isArchived) {
            theme.mediumContrastTextColor
        } else {
            theme.color(color.paletteIndex)
        }

        buttons.forEachIndexed { index, button ->
            val date = today.minus(index + dataOffset)
            val offset = index + dataOffset

            button.state = CheckmarkButtonState(
                value = if (offset < values.size) values[offset] else UNKNOWN,
                color = actualColor,
                theme = theme,
                showQuestionMark = preferences.areQuestionMarksEnabled,
                notes = if (offset < notes.size) notes[offset] else ""
            )
            button.onToggle = { value, notes -> onToggle(date, value, notes) }
            button.onEdit = { onEdit(date) }
        }
    }
}
