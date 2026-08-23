import { createRequire } from "module";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const brandDir = dirname(fileURLToPath(import.meta.url));
const root = join(brandDir, "..");
const require = createRequire(join(root, "web", "package.json"));
const sharp = require("sharp");

const iconSvg = readFileSync(join(brandDir, "logo-icon.svg"));
const markSvg = readFileSync(join(brandDir, "logo-mark.svg"));

const generated = "C:/Users/abhis/.cursor/projects/d-Projects-MobiStack/assets";

function ensure(dir) {
  mkdirSync(dir, { recursive: true });
}

async function raster(svg, file, size) {
  await sharp(svg, { density: 384 })
    .resize(size, size, { fit: "fill" })
    .png({ compressionLevel: 9 })
    .toFile(file);
}

async function main() {
  const webPublic = join(root, "web", "public");
  const mobileAssets = join(root, "mobile", "assets");
  const iosIcon = join(root, "mobile", "ios", "FixFlow", "Images.xcassets", "AppIcon.appiconset");
  const androidRes = join(root, "mobile", "android", "app", "src", "main", "res");

  ensure(webPublic);
  ensure(mobileAssets);
  ensure(iosIcon);
  ensure(join(androidRes, "drawable"));

  writeFileSync(join(webPublic, "favicon.svg"), markSvg);

  await raster(iconSvg, join(mobileAssets, "icon.png"), 1024);
  await raster(iconSvg, join(mobileAssets, "adaptive-icon.png"), 1024);
  await raster(iconSvg, join(mobileAssets, "splash-icon.png"), 1024);
  await raster(markSvg, join(mobileAssets, "logo.png"), 256);
  await raster(iconSvg, join(iosIcon, "icon.png"), 1024);
  await raster(markSvg, join(webPublic, "apple-touch-icon.png"), 180);
  await raster(markSvg, join(webPublic, "favicon-32.png"), 32);
  await raster(markSvg, join(webPublic, "icon-192.png"), 192);
  await raster(iconSvg, join(webPublic, "icon-512.png"), 512);

  const splashIcon = await sharp(markSvg, { density: 384 }).resize(420, 420).png().toBuffer();
  await sharp({
    create: { width: 1284, height: 2778, channels: 3, background: "#2E1065" },
  })
    .composite([{ input: splashIcon, gravity: "center" }])
    .png({ compressionLevel: 9 })
    .toFile(join(mobileAssets, "splash.png"));

  await sharp(markSvg, { density: 384 })
    .resize(288, 288)
    .png()
    .toFile(join(androidRes, "drawable", "splashscreen_logo.png"));

  const launcher = [
    ["mdpi", 48, 108],
    ["hdpi", 72, 162],
    ["xhdpi", 96, 216],
    ["xxhdpi", 144, 324],
    ["xxxhdpi", 192, 432],
  ];
  for (const [density, launcherSize, foreground] of launcher) {
    const dir = join(androidRes, `mipmap-${density}`);
    ensure(dir);
    await sharp(iconSvg, { density: 384 }).resize(launcherSize, launcherSize).webp({ quality: 92 }).toFile(join(dir, "ic_launcher.webp"));
    await sharp(iconSvg, { density: 384 }).resize(launcherSize, launcherSize).webp({ quality: 92 }).toFile(join(dir, "ic_launcher_round.webp"));
    await sharp(iconSvg, { density: 384 }).resize(foreground, foreground).webp({ quality: 92 }).toFile(join(dir, "ic_launcher_foreground.webp"));
  }

  try {
    copyFileSync(join(generated, "mobistack-logo-wordmark.png"), join(brandDir, "logo-wordmark.png"));
    copyFileSync(join(generated, "mobistack-logo-dark.png"), join(brandDir, "logo-dark.png"));
    copyFileSync(join(generated, "mobistack-app-icon.png"), join(brandDir, "logo-icon-render.png"));

    const dark = sharp(join(generated, "mobistack-logo-dark.png")).resize(1200, 630, {
      fit: "contain",
      background: "#0F1220",
    });
    await dark.png({ compressionLevel: 9 }).toFile(join(webPublic, "og.png"));
  } catch (error) {
    console.warn("Skipping generated marketing copies:", error.message);
  }

  console.log("Rendered MobiStack logo assets.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
