import initSqlJs, { type Database as SqlJsDb } from "sql.js";
import sqlWasmUrl from "sql.js/dist/sql-wasm.wasm?url";
import { JsDatabase, migrateDatabase } from "./bridge";

// Migration SQL files from uhabits-core, bundled as raw strings by Vite.
import m09 from "../../../uhabits-core/assets/main/migrations/09.sql?raw";
import m10 from "../../../uhabits-core/assets/main/migrations/10.sql?raw";
import m11 from "../../../uhabits-core/assets/main/migrations/11.sql?raw";
import m12 from "../../../uhabits-core/assets/main/migrations/12.sql?raw";
import m13 from "../../../uhabits-core/assets/main/migrations/13.sql?raw";
import m14 from "../../../uhabits-core/assets/main/migrations/14.sql?raw";
import m15 from "../../../uhabits-core/assets/main/migrations/15.sql?raw";
import m16 from "../../../uhabits-core/assets/main/migrations/16.sql?raw";
import m17 from "../../../uhabits-core/assets/main/migrations/17.sql?raw";
import m18 from "../../../uhabits-core/assets/main/migrations/18.sql?raw";
import m19 from "../../../uhabits-core/assets/main/migrations/19.sql?raw";
import m20 from "../../../uhabits-core/assets/main/migrations/20.sql?raw";
import m21 from "../../../uhabits-core/assets/main/migrations/21.sql?raw";
import m22 from "../../../uhabits-core/assets/main/migrations/22.sql?raw";
import m23 from "../../../uhabits-core/assets/main/migrations/23.sql?raw";
import m24 from "../../../uhabits-core/assets/main/migrations/24.sql?raw";
import m25 from "../../../uhabits-core/assets/main/migrations/25.sql?raw";

const MIGRATIONS = [m09, m10, m11, m12, m13, m14, m15, m16, m17, m18, m19, m20, m21, m22, m23, m24, m25];

export async function createSqlJsDatabase(
  existingData?: Uint8Array,
): Promise<SqlJsDb> {
  const SQL = await initSqlJs({
    locateFile: () => sqlWasmUrl,
  });

  const sqlDb = existingData ? new SQL.Database(existingData) : new SQL.Database();
  migrateDatabase(new JsDatabase(sqlDb), MIGRATIONS);
  return sqlDb;
}
