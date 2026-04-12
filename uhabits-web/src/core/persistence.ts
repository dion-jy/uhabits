import type { Database as SqlJsDb } from "sql.js";

const DB_NAME = "uhabits";
const STORE_NAME = "database";
const KEY = "main";

export async function saveToIndexedDB(db: SqlJsDb): Promise<void> {
  const data = db.export();
  const idb = await openIDB();
  const tx = idb.transaction(STORE_NAME, "readwrite");
  tx.objectStore(STORE_NAME).put(data, KEY);
  await new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function loadFromIndexedDB(): Promise<Uint8Array | null> {
  const idb = await openIDB();
  const tx = idb.transaction(STORE_NAME, "readonly");
  const req = tx.objectStore(STORE_NAME).get(KEY);
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result ?? null);
    req.onerror = () => reject(req.error);
  });
}

function openIDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      req.result.createObjectStore(STORE_NAME);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
