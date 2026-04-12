package org.isoron.platform.io

import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.database.SQLParser
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import kotlin.js.JsExport
import kotlin.js.Promise

@JsModule("sql.js")
@JsNonModule
external fun initSqlJs(config: dynamic = definedExternally): Promise<dynamic>

private fun sqlJsNewDatabase(sqlJs: dynamic): dynamic {
    return js("new sqlJs.Database()")
}

private fun sqlJsOpenDatabase(sqlJs: dynamic, data: dynamic): dynamic {
    return js("new sqlJs.Database(data)")
}

class JsPreparedStatement(
    private val db: dynamic,
    sql: String
) : PreparedStatement {
    private val stmt: dynamic = db.prepare(sql)
    private var currentRow: dynamic = null
    private var bindings: dynamic = js("[]")
    private var needsBind: Boolean = false

    override fun step(): StepResult {
        if (needsBind) {
            stmt.bind(bindings)
            needsBind = false
        }
        val hasRow = stmt.step() as Boolean
        currentRow = if (hasRow) stmt.get() else null
        return if (hasRow) StepResult.ROW else StepResult.DONE
    }

    override fun getInt(index: Int): Int = (currentRow[index] as Number).toInt()
    override fun getLong(index: Int): Long = (currentRow[index] as Number).toLong()
    override fun getReal(index: Int): Double = (currentRow[index] as Number).toDouble()
    override fun getText(index: Int): String = currentRow[index] as String

    override fun getIntOrNull(index: Int): Int? {
        val v = currentRow[index] ?: return null
        return (v as Number).toInt()
    }

    override fun getLongOrNull(index: Int): Long? {
        val v = currentRow[index] ?: return null
        return (v as Number).toLong()
    }

    override fun getRealOrNull(index: Int): Double? {
        val v = currentRow[index] ?: return null
        return (v as Number).toDouble()
    }

    override fun getTextOrNull(index: Int): String? {
        val v = currentRow[index] ?: return null
        return v as String
    }

    override fun bindInt(index: Int, value: Int) {
        bindings[index - 1] = value
        needsBind = true
    }

    override fun bindLong(index: Int, value: Long) {
        require(value in -(9e15.toLong())..9e15.toLong()) {
            "Long value $value exceeds JS safe integer range"
        }
        bindings[index - 1] = value.toDouble()
        needsBind = true
    }

    override fun bindReal(index: Int, value: Double) {
        bindings[index - 1] = value
        needsBind = true
    }

    override fun bindText(index: Int, value: String) {
        bindings[index - 1] = value
        needsBind = true
    }

    override fun bindNull(index: Int) {
        bindings[index - 1] = null
        needsBind = true
    }

    override fun reset() {
        stmt.reset()
        currentRow = null
        bindings = js("[]")
        needsBind = false
    }

    override fun finalize() {
        stmt.free()
    }
}

@JsExport
class JsDatabase(val db: dynamic) : Database {
    override fun prepareStatement(sql: String): PreparedStatement {
        return JsPreparedStatement(db, sql)
    }

    override fun close() {
        db.close()
    }
}

/**
 * Mirrors HabitsDatabaseOpener.onUpgrade() from uhabits-android.
 * Applies migrations using core's SQLParser so the web app has zero
 * schema logic of its own. [migrationSQLs] is indexed from 0 where
 * index 0 = migration 9, index 1 = migration 10, etc.
 */
@JsExport
fun migrateDatabase(db: Database, migrationSQLs: Array<String>) {
    if (db.getVersion() < 8) db.setVersion(8)
    val currentVersion = db.getVersion()
    if (currentVersion >= DATABASE_VERSION) return
    for (v in (currentVersion + 1)..DATABASE_VERSION) {
        val sql = migrationSQLs[v - 9]
        val commands = SQLParser.parse(sql)
        for (cmd in commands) db.run(cmd)
        db.setVersion(v)
    }
}

@JsExport
class JsDatabaseOpener(
    private val sqlJs: dynamic,
    private val storage: JsFileStorage? = null
) : DatabaseOpener {
    override fun open(path: String): Database {
        if (path == ":memory:" || storage == null) {
            val db = sqlJsNewDatabase(sqlJs)
            return JsDatabase(db)
        }
        val bytes = storage.read(path)
            ?: error("File not found in storage: $path")
        val data = Uint8Array(bytes.size)
        for (i in bytes.indices) {
            data[i] = bytes[i]
        }
        val db = sqlJsOpenDatabase(sqlJs, data)
        return JsDatabase(db)
    }
}
