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
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import me.tatarka.inject.annotations.Inject
import org.isoron.platform.gui.AndroidView
import org.isoron.platform.gui.Color
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.views.CheckmarkButton
import org.isoron.uhabits.core.ui.views.CheckmarkButtonState
import org.isoron.uhabits.core.ui.views.LightTheme
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.utils.toMeasureSpec

@Inject
class CheckmarkButtonViewFactory(
    @ActivityContext val context: Context,
    val preferences: Preferences
) {
    fun create() = CheckmarkButtonView(context, preferences)
}

class CheckmarkButtonView(
    context: Context,
    val preferences: Preferences
) : AndroidView<CheckmarkButton>(context),
    View.OnClickListener,
    View.OnLongClickListener {

    var state = CheckmarkButtonState(
        value = Entry.UNKNOWN,
        color = Color(0),
        theme = LightTheme()
    )
        set(value) {
            field = value
            view = CheckmarkButton(value)
            postInvalidate()
        }

    var onToggle: (Int, String) -> Unit = { _, _ -> }
    var onEdit: () -> Unit = { }

    init {
        setOnClickListener(this)
        setOnLongClickListener(this)
    }

    fun performToggle() {
        val newValue = Entry.nextToggleValue(
            value = state.value,
            isSkipEnabled = preferences.isSkipEnabled,
            areQuestionMarksEnabled = preferences.areQuestionMarksEnabled
        )
        state = state.copy(value = newValue)
        onToggle(newValue, state.notes)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onClick(v: View) {
        if (preferences.isShortToggleEnabled) performToggle() else onEdit()
    }

    override fun onLongClick(v: View): Boolean {
        if (preferences.isShortToggleEnabled) onEdit() else performToggle()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resources.getDimensionPixelSize(R.dimen.checkmarkHeight)
        val width = resources.getDimensionPixelSize(R.dimen.checkmarkWidth)
        super.onMeasure(
            width.toMeasureSpec(EXACTLY),
            height.toMeasureSpec(EXACTLY)
        )
    }
}
