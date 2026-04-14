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

enum class TextAlign {
    LEFT, CENTER, RIGHT
}

enum class TextStyle {
    FILL, STROKE
}

enum class Font {
    REGULAR,
    BOLD,
    FONT_AWESOME,
    BOLD_CONDENSED,
    CONDENSED
}

data class ScreenLocation(
    val x: Double,
    val y: Double
)

interface Canvas {
    fun setColor(color: Color)
    fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double)
    fun drawText(text: String, x: Double, y: Double)
    fun fillRect(x: Double, y: Double, width: Double, height: Double)
    fun fillRoundRect(x: Double, y: Double, width: Double, height: Double, cornerRadius: Double)
    fun drawRect(x: Double, y: Double, width: Double, height: Double)
    fun getHeight(): Double
    fun getWidth(): Double
    fun setFont(font: Font)
    fun setFontSize(size: Double)
    fun setStrokeWidth(size: Double)
    fun fillArc(
        centerX: Double,
        centerY: Double,
        radius: Double,
        startAngle: Double,
        swipeAngle: Double
    )
    fun fillCircle(centerX: Double, centerY: Double, radius: Double)
    fun setTextAlign(align: TextAlign)
    fun setTextStyle(style: TextStyle)
    fun toImage(): Image
    fun measureText(text: String): Double

    /**
     * Fills entire canvas with the current color.
     */
    fun fill() {
        fillRect(0.0, 0.0, getWidth(), getHeight())
    }

    fun drawTestImage2() {
        // White background
        setColor(Color(0xFFFFFF))
        fill()

        setStrokeWidth(1.0)
        setTextAlign(TextAlign.LEFT)

        var y = 0.0
        val fonts = listOf(
            Font.REGULAR to "Regular",
            Font.BOLD to "Bold",
            Font.BOLD_CONDENSED to "BoldCondensed",
            Font.CONDENSED to "Condensed"
        )

        for ((font, label) in fonts) {
            // Section label
            setFont(Font.REGULAR)
            setFontSize(10.0)
            setColor(Color(0x909090))
            setTextAlign(TextAlign.LEFT)
            drawText(label, 10.0, y + 15.0)

            // Sample text at different sizes
            setFont(font)
            setColor(Color(0x303030))

            setFontSize(12.0)
            drawText("Abc 123 hello", 10.0, y + 35.0)

            setFontSize(18.0)
            drawText("Abc 123 hello", 10.0, y + 58.0)

            setFontSize(24.0)
            drawText("Abc 123 hello", 10.0, y + 85.0)

            setFontSize(36.0)
            drawText("Abc 123", 280.0, y + 60.0)

            // Separator line
            setColor(Color(0xE0E0E0))
            drawLine(10.0, y + 100.0, 490.0, y + 100.0)

            y += 105.0
        }

        // FontAwesome section
        setFont(Font.REGULAR)
        setFontSize(10.0)
        setColor(Color(0x909090))
        setTextAlign(TextAlign.LEFT)
        drawText("FontAwesome", 10.0, y + 15.0)

        setFont(Font.FONT_AWESOME)
        setColor(Color(0x303030))

        setFontSize(16.0)
        drawText("${FontAwesome.CHECK} ${FontAwesome.TIMES} ${FontAwesome.SKIP} ${FontAwesome.QUESTION}", 10.0, y + 40.0)

        setFontSize(24.0)
        drawText("${FontAwesome.CHECK} ${FontAwesome.TIMES} ${FontAwesome.SKIP} ${FontAwesome.QUESTION}", 10.0, y + 70.0)

        setFontSize(36.0)
        drawText("${FontAwesome.CHECK} ${FontAwesome.TIMES} ${FontAwesome.SKIP} ${FontAwesome.QUESTION}", 250.0, y + 60.0)

        // Text alignment section
        y += 105.0
        setFont(Font.REGULAR)
        setFontSize(10.0)
        setColor(Color(0x909090))
        setTextAlign(TextAlign.LEFT)
        drawText("Alignment", 10.0, y + 15.0)

        setColor(Color(0xE0E0E0))
        drawLine(250.0, y + 20.0, 250.0, y + 95.0)

        setFont(Font.BOLD)
        setFontSize(20.0)
        setColor(Color(0x303030))

        setTextAlign(TextAlign.LEFT)
        drawText("Left aligned", 250.0, y + 40.0)

        setTextAlign(TextAlign.CENTER)
        drawText("Center aligned", 250.0, y + 65.0)

        setTextAlign(TextAlign.RIGHT)
        drawText("Right aligned", 250.0, y + 90.0)
    }

    fun drawTestImage() {
        // Draw transparent background
        setColor(Color(0.1, 0.1, 0.1, 0.5))
        fillRect(0.0, 0.0, 500.0, 400.0)

        // Draw center rectangle
        setColor(Color(0x606060))
        setStrokeWidth(25.0)
        drawRect(100.0, 100.0, 300.0, 200.0)

        // Draw squares, circles and arcs
        setColor(Color.YELLOW)
        setStrokeWidth(1.0)
        drawRect(0.0, 0.0, 100.0, 100.0)
        fillCircle(50.0, 50.0, 30.0)
        drawRect(0.0, 100.0, 100.0, 100.0)
        fillArc(50.0, 150.0, 30.0, 90.0, 135.0)
        drawRect(0.0, 200.0, 100.0, 100.0)
        fillArc(50.0, 250.0, 30.0, 90.0, -135.0)
        drawRect(0.0, 300.0, 100.0, 100.0)
        fillArc(50.0, 350.0, 30.0, 45.0, 90.0)

        // Draw two red crossing lines
        setColor(Color.RED)
        setStrokeWidth(2.0)
        drawLine(0.0, 0.0, 500.0, 400.0)
        drawLine(500.0, 0.0, 0.0, 400.0)

        // Draw text
        setFont(Font.BOLD)
        setFontSize(50.0)
        setColor(Color.GREEN)
        setTextAlign(TextAlign.CENTER)
        drawText("HELLO", 250.0, 100.0)
        setTextAlign(TextAlign.RIGHT)
        drawText("HELLO", 250.0, 150.0)
        setTextAlign(TextAlign.LEFT)
        drawText("HELLO", 250.0, 200.0)

        // Draw FontAwesome icon
        setFont(Font.FONT_AWESOME)
        drawText(FontAwesome.CHECK, 250.0, 300.0)
    }
}
