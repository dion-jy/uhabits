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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.isoron.uhabits.BaseViewTest
import org.isoron.uhabits.core.ui.views.LightTheme
import org.isoron.uhabits.core.ui.views.NumberButtonState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class NumberButtonViewTest : BaseViewTest() {

    private lateinit var view: NumberButtonView
    var edited = false

    private val theme = LightTheme()

    @Before
    override fun setUp() {
        super.setUp()
        view = component.getNumberButtonViewFactory().create().apply {
            state = NumberButtonState(
                value = 0.0,
                color = theme.color(8),
                threshold = 100.0,
                targetType = 0,
                units = "steps",
                theme = theme,
                showQuestionMark = false,
                notes = ""
            )
            onEdit = { edited = true }
        }
        measureView(view, dpToPixels(48), dpToPixels(48))
    }

    @Test
    fun testClick() {
        view.performClick()
        assertTrue(edited)
    }

    @Test
    fun testLongClick() {
        view.performLongClick()
        assertTrue(edited)
    }
}
