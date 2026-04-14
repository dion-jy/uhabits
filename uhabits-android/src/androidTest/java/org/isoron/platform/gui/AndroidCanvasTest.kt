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

package org.isoron.platform.gui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.isoron.uhabits.BaseViewTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class AndroidCanvasTest : BaseViewTest() {
    @Test
    fun testDrawTestImage() {
//        similarityCutoff = 0.002
        val density = 2.0
        val widthPx = (500 * density).toInt()
        val heightPx = (400 * density).toInt()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas()
        canvas.context = targetContext
        canvas.innerDensity = density
        canvas.innerWidth = widthPx
        canvas.innerHeight = heightPx
        canvas.innerCanvas = android.graphics.Canvas(bmp)
        canvas.innerBitmap = bmp
        canvas.drawTestImage()
        assertRenders(bmp, "CanvasTest.png")
    }

    @Test
    fun testDrawTestImage2() {
        val density = 2.0
        val widthPx = (500 * density).toInt()
        val heightPx = (640 * density).toInt()
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas()
        canvas.context = targetContext
        canvas.innerDensity = density
        canvas.innerWidth = widthPx
        canvas.innerHeight = heightPx
        canvas.innerCanvas = android.graphics.Canvas(bmp)
        canvas.innerBitmap = bmp
        canvas.drawTestImage2()
        assertRenders(bmp, "CanvasTest2.png")
    }
}
