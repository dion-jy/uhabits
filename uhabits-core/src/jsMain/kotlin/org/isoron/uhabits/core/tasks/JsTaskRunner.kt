package org.isoron.uhabits.core.tasks

import kotlinx.coroutines.Dispatchers
import kotlin.js.JsExport

/**
 * Factory that creates a [CoroutineTaskRunner] with JS-appropriate dispatchers.
 * Needed because [CoroutineDispatcher] cannot be constructed from TypeScript.
 */
@JsExport
fun createTaskRunner(): TaskRunner {
    return CoroutineTaskRunner(
        mainDispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )
}
