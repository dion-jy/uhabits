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

import kotlinx.coroutines.test.runTest
import org.isoron.platform.gui.assertRenders
import org.isoron.uhabits.core.models.Entry
import kotlin.test.Test

class CheckmarkButtonTest {
    val base = "views/CheckmarkButton"
    val theme = LightTheme()

    private fun createButton(value: Int) = CheckmarkButton(
        CheckmarkButtonState(
            value = value,
            color = theme.color(5),
            theme = theme
        )
    )

    @Test
    fun testRender_explicitCheck() = runTest {
        assertRenders(48, 48, "$base/explicit.png", createButton(Entry.YES_MANUAL))
    }

    @Test
    fun testRender_implicitCheck() = runTest {
        assertRenders(48, 48, "$base/implicit.png", createButton(Entry.YES_AUTO))
    }

    @Test
    fun testRender_unchecked() = runTest {
        assertRenders(48, 48, "$base/unchecked.png", createButton(Entry.NO))
    }
}
