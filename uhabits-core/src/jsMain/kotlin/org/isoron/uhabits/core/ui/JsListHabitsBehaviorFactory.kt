package org.isoron.uhabits.core.ui

import org.isoron.platform.io.UserFile
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.tasks.TaskRunner
import org.isoron.uhabits.core.ui.callbacks.CheckMarkDialogCallback
import org.isoron.uhabits.core.ui.callbacks.NumberPickerCallback
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsBehavior
import kotlin.js.JsExport

@JsExport
fun createListHabitsBehavior(
    habitList: HabitList,
    commandRunner: CommandRunner,
    preferences: Preferences,
    taskRunner: TaskRunner
): ListHabitsBehavior {
    return ListHabitsBehavior(
        habitList = habitList,
        dirFinder = NoOpDirFinder(),
        taskRunner = taskRunner,
        screen = NoOpScreen(),
        commandRunner = commandRunner,
        prefs = preferences,
        bugReporter = NoOpBugReporter()
    )
}

private class NoOpDirFinder : ListHabitsBehavior.DirFinder {
    override fun getCSVOutputDir(): UserFile {
        throw UnsupportedOperationException("CSV export not supported on web")
    }
}

private class NoOpScreen : ListHabitsBehavior.Screen {
    override fun showHabitScreen(h: Habit) {}
    override fun showIntroScreen() {}
    override fun showMessage(m: ListHabitsBehavior.Message) {}
    override fun showNumberPopup(value: Double, notes: String, callback: NumberPickerCallback) {}
    override fun showCheckmarkPopup(
        selectedValue: Int,
        notes: String,
        color: PaletteColor,
        callback: CheckMarkDialogCallback
    ) {}
    override fun showSendBugReportToDeveloperScreen(log: String) {}
    override fun showSendFileScreen(filename: String) {}
    override fun showConfetti(color: PaletteColor, x: Float, y: Float) {}
}

private class NoOpBugReporter : ListHabitsBehavior.BugReporter {
    override fun dumpBugReportToFile() {}
    override fun getBugReport(): String = ""
}
