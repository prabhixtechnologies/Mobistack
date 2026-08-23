const { spawnSync } = require("child_process");
const path = require("path");
const fs = require("fs");

const variant = process.argv[2] === "debug" ? "assembleDebug" : "assembleRelease";
const androidDir = path.join(__dirname, "..", "android");
const gradlew = process.platform === "win32" ? "gradlew.bat" : "./gradlew";

if (!fs.existsSync(androidDir)) {
  console.error("android/ is missing. Run npm run prebuild first.");
  process.exit(1);
}

const sdk =
  process.env.ANDROID_HOME ||
  process.env.ANDROID_SDK_ROOT ||
  (process.platform === "win32"
    ? path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk")
    : path.join(process.env.HOME || "", "Android", "Sdk"));
if (fs.existsSync(sdk)) {
  const sdkDir = sdk.replace(/\\/g, "/");
  fs.writeFileSync(path.join(androidDir, "local.properties"), `sdk.dir=${sdkDir.replace(/:/g, "\\:")}\n`);
}

const result = spawnSync(gradlew, [variant], {
  cwd: androidDir,
  stdio: "inherit",
  shell: process.platform === "win32",
});

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

const apkDir = path.join(androidDir, "app", "build", "outputs", "apk");
console.log(`APK output is under ${apkDir}`);
