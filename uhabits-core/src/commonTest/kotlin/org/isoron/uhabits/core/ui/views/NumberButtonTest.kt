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
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberButtonTest {
    val base = "views/NumberButton"
    val theme = LightTheme()

    private fun createButton(
        value: Double = 0.0,
        threshold: Double = 100.0,
        targetType: Int = 0,
        units: String = "steps"
    ) = NumberButton(
        NumberButtonState(
            value = value,
            color = theme.color(8),
            threshold = threshold,
            targetType = targetType,
            units = units,
            theme = theme,
            showQuestionMark = false,
            notes = ""
        )
    )

    @Test
    fun testFormatValue() {
        assertEquals("0.12", 0.1235.toShortString())
        assertEquals("0.1", 0.1000.toShortString())
        assertEquals("5", 5.0.toShortString())
        assertEquals("5.25", 5.25.toShortString())
        assertEquals("12.3", 12.3456.toShortString())
        assertEquals("123", 123.123.toShortString())
        assertEquals("321", 321.2.toShortString())
        assertEquals("4.3k", 4321.2.toShortString())
        assertEquals("54.3k", 54321.2.toShortString())
        assertEquals("654k", 654321.2.toShortString())
        assertEquals("7.7M", 7654321.2.toShortString())
        assertEquals("87.7M", 87654321.2.toShortString())
        assertEquals("988M", 987654321.2.toShortString())
        assertEquals("2.0G", 1987654321.2.toShortString())
    }

    @Test
    fun testRender_aboveThreshold() = runTest {
        assertRenders(48, 48, "$base/render_above.png", createButton(value = 500.0))
    }

    @Test
    fun testRender_atMostAboveThreshold() = runTest {
        assertRenders(48, 48, "$base/render_at_most_above.png", createButton(value = 500.0, targetType = 1))
    }

    @Test
    fun testRender_belowThreshold() = runTest {
        assertRenders(48, 48, "$base/render_below.png", createButton(value = 99.0))
    }

    @Test
    fun testRender_atMostBetweenThresholds() = runTest {
        assertRenders(48, 48, "$base/render_at_most_between.png", createButton(value = 110.0, targetType = 1))
    }

    @Test
    fun testRender_zero() = runTest {
        assertRenders(48, 48, "$base/render_zero.png", createButton(value = 0.0))
    }

    @Test
    fun testRender_atMostBelowThreshold() = runTest {
        assertRenders(48, 48, "$base/render_at_most_below.png", createButton(value = 0.0, targetType = 1))
    }

    @Test
    fun testRender_emptyUnits() = runTest {
        assertRenders(48, 48, "$base/render_unitless.png", createButton(value = 500.0, units = ""))
    }
}
