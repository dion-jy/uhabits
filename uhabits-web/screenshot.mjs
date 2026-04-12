import { chromium } from "playwright";

const URL = process.argv[2] || "http://localhost:5174";
const OUTPUT = process.argv[3] || "/tmp/web-screenshot.png";

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 400, height: 800 },
    deviceScaleFactor: 2,
  });

  // Log browser console messages
  page.on("console", (msg) => console.log(`[browser ${msg.type()}] ${msg.text()}`));
  page.on("pageerror", (err) => console.log(`[browser error] ${err.stack || err.message}`));

  // Clear IndexedDB to start fresh (seed habits are created automatically)
  await page.goto(URL, { waitUntil: "networkidle" });
  await page.evaluate(() => {
    return new Promise((resolve) => {
      const req = indexedDB.deleteDatabase("uhabits");
      req.onsuccess = resolve;
      req.onerror = resolve;
      req.onblocked = resolve;
    });
  });
  // Reload after clearing DB
  await page.reload({ waitUntil: "networkidle" });
  await page.waitForSelector("header", { timeout: 5000 });
  await page.waitForTimeout(200);

  // Take screenshot
  await page.screenshot({ path: OUTPUT, fullPage: false });
  console.log(`Screenshot saved to ${OUTPUT}`);
  await browser.close();
}

run().catch(console.error);
