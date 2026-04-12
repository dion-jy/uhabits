import { chromium } from "playwright";

const URL = process.argv[2] || "http://localhost:5174";
const OUTPUT = process.argv[3] || "/tmp/web-screenshot.png";

const HABITS = [
  "Wake up early",
  "Cook healthy dinner",
  "Write journal",
  "Track time",
  "Meditate",
  "Read books",
  "Learn French",
  "Play chess",
  "Practice guitar",
  "Call a friend",
];

async function run() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 400, height: 800 },
    deviceScaleFactor: 2,
  });

  // Log browser console messages
  page.on("console", (msg) => console.log(`[browser ${msg.type()}] ${msg.text()}`));
  page.on("pageerror", (err) => console.log(`[browser error] ${err.stack || err.message}`));

  // Clear IndexedDB to start fresh
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

  // Create habits one by one
  for (const name of HABITS) {
    // Click the add button (force: true to bypass overlay issues)
    await page.click('button[title="Add habit"]', { force: true });
    await page.waitForTimeout(50);

    const input = await page.waitForSelector('input[placeholder="Habit name"]', { timeout: 2000 });
    await input.click();
    await input.fill(name);

    await page.keyboard.press("Enter");
    await page.waitForTimeout(100);
  }

  await page.waitForTimeout(200);

  // Take screenshot
  await page.screenshot({ path: OUTPUT, fullPage: false });
  console.log(`Screenshot saved to ${OUTPUT}`);
  await browser.close();
}

run().catch(console.error);
