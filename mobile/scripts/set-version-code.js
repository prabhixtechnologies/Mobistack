#!/usr/bin/env node
// Sets the Android version code from the VERSION_CODE environment variable, for
// the Play release workflow's override input.
//
// Writes both app.json and android/app/build.gradle. android/ is committed and
// the build runs Gradle against it directly -- nothing regenerates it from
// app.json -- so build.gradle is the number that actually ships and app.json is
// the one humans read. They have to move together.
//
// Play remembers every version code a package has ever been given and refuses a
// repeat, including numbers used on tracks that were later abandoned. When a
// release fails with "version code N has already been used", the number has to
// go up -- there is no way to free it.

const fs = require("fs");
const path = require("path");

const raw = process.env.VERSION_CODE;
if (!raw) {
  console.error("VERSION_CODE is not set; nothing to do.");
  process.exit(1);
}
if (!/^[1-9][0-9]*$/.test(raw)) {
  console.error(`VERSION_CODE must be a positive integer, got "${raw}".`);
  process.exit(1);
}

const next = Number(raw);
// Play's own ceiling. Worth catching here: the upload rejects it much later.
if (next > 2100000000) {
  console.error(`VERSION_CODE ${next} exceeds the maximum Play accepts.`);
  process.exit(1);
}

const appJsonFile = path.join(__dirname, "..", "app.json");
const gradleFile = path.join(__dirname, "..", "android", "app", "build.gradle");

const config = JSON.parse(fs.readFileSync(appJsonFile, "utf8"));
const android = config.expo && config.expo.android;
if (!android) {
  console.error("app.json has no expo.android section.");
  process.exit(1);
}

const previous = android.versionCode;
if (next < previous) {
  console.error(
    `Refusing to lower versionCode from ${previous} to ${next}: Android will not ` +
      "install a build that numbers itself below the one already on the device."
  );
  process.exit(1);
}

let gradle = fs.readFileSync(gradleFile, "utf8");
const gradlePattern = /(\n\s*versionCode\s+)(\d+)/;
const found = gradle.match(gradlePattern);
if (!found) {
  console.error(`Could not find a versionCode line in ${gradleFile}.`);
  process.exit(1);
}

android.versionCode = next;
fs.writeFileSync(appJsonFile, `${JSON.stringify(config, null, 2)}\n`, "utf8");

gradle = gradle.replace(gradlePattern, `$1${next}`);
fs.writeFileSync(gradleFile, gradle, "utf8");

console.log(`versionCode ${previous} -> ${next} (app.json was ${previous}, build.gradle was ${found[2]})`);
