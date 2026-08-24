const { spawnSync } = require("child_process");
const crypto = require("crypto");
const path = require("path");
const fs = require("fs");

const debug = process.argv[2] === "debug";
const gradleTasks = debug ? ["assembleDebug"] : ["assembleRelease", "bundleRelease"];
const androidDir = path.join(__dirname, "..", "android");
const appDir = path.join(androidDir, "app");
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

if (process.platform !== "win32") {
  try {
    fs.chmodSync(path.join(androidDir, "gradlew"), 0o755);
  } catch {
    /* best effort; git file mode is the real fix */
  }
}

if (!debug) {
  restoreReleaseKeystoreFromEnv();
  ensureReleaseKeystore();
  patchReleaseSigning();
}

const result = spawnSync(gradlew, gradleTasks, {
  cwd: androidDir,
  stdio: "inherit",
  shell: process.platform === "win32",
});

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

const apkDir = path.join(androidDir, "app", "build", "outputs", "apk");
const releaseApk = path.join(apkDir, "release", "app-release.apk");
const debugApk = path.join(apkDir, "debug", "app-debug.apk");
const releaseAab = path.join(androidDir, "app", "build", "outputs", "bundle", "release", "app-release.aab");
const built = fs.existsSync(releaseApk) ? releaseApk : debugApk;
console.log(`APK output is under ${apkDir}`);
if (built) {
  console.log(`Built ${built}`);
}
if (!debug) {
  if (!fs.existsSync(releaseAab)) {
    console.error("Release AAB was not produced.");
    process.exit(1);
  }
  console.log(`Built ${releaseAab}`);
}

function writeGradleKeystoreProps(propsFile, { password, alias, keyPassword }) {
  fs.writeFileSync(
    propsFile,
    [
      "storeFile=mobistack-release.keystore",
      `storePassword=${password}`,
      `keyAlias=${alias}`,
      `keyPassword=${keyPassword}`,
      "",
    ].join("\n"),
    { mode: 0o600 },
  );
}

function loadProps(file) {
  const out = {};
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const cut = line.indexOf("=");
    if (cut > 0) {
      out[line.slice(0, cut).trim()] = line.slice(cut + 1);
    }
  }
  return out;
}

function restoreReleaseKeystoreFromEnv() {
  const b64 = process.env.ANDROID_RELEASE_KEYSTORE_BASE64;
  if (!b64) {
    return false;
  }
  const password = process.env.ANDROID_KEYSTORE_PASSWORD;
  const keyPassword = process.env.ANDROID_KEY_PASSWORD || password;
  const alias = process.env.ANDROID_KEY_ALIAS || "mobistack";
  if (!password || !keyPassword) {
    console.error("ANDROID_KEYSTORE_PASSWORD is required when ANDROID_RELEASE_KEYSTORE_BASE64 is set.");
    process.exit(1);
  }
  const storeFile = path.join(appDir, "mobistack-release.keystore");
  const propsFile = path.join(androidDir, "keystore.properties");
  fs.writeFileSync(storeFile, Buffer.from(b64.replace(/\s/g, ""), "base64"));
  writeGradleKeystoreProps(propsFile, { password, alias, keyPassword });
  console.log("Restored release keystore from CI secrets.");
  return true;
}

function ensureReleaseKeystore() {
  const storeFile = path.join(appDir, "mobistack-release.keystore");
  const propsFile = path.join(androidDir, "keystore.properties");
  const secretDir = path.join(__dirname, "..", ".secrets");
  const secretStore = path.join(secretDir, "mobistack-release.keystore");
  const secretProps = path.join(secretDir, "keystore.properties");
  if (!fs.existsSync(storeFile) && fs.existsSync(secretStore)) {
    fs.copyFileSync(secretStore, storeFile);
  }
  if (!fs.existsSync(propsFile) && fs.existsSync(secretProps)) {
    fs.copyFileSync(secretProps, propsFile);
  }
  if (fs.existsSync(storeFile) && fs.existsSync(propsFile)) {
    const props = loadProps(propsFile);
    const storePassword = props.storePassword || props.storePassword;
    const keyPassword = props.keyPassword || props.keyPassword || storePassword;
    const alias = props.keyAlias || props.keyAlias || "mobistack";
    if (storePassword && !props.storePassword) {
      writeGradleKeystoreProps(propsFile, { password: storePassword, alias, keyPassword });
    }
    fs.mkdirSync(secretDir, { recursive: true });
    fs.copyFileSync(storeFile, secretStore);
    fs.copyFileSync(propsFile, secretProps);
    console.log("Using existing release keystore.");
    return;
  }
  if (process.env.GITHUB_ACTIONS === "true") {
    console.error(
      "Release signing secrets are missing. Add ANDROID_RELEASE_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD.",
    );
    process.exit(1);
  }
  const password = crypto.randomBytes(18).toString("base64url");
  const keytool = findKeytool();
  if (!keytool) {
    console.warn("keytool not found. Release APK will be signed with the debug keystore.");
    return;
  }
  const args = [
    "-genkeypair",
    "-v",
    "-storetype",
    "PKCS12",
    "-keystore",
    storeFile,
    "-alias",
    "mobistack",
    "-keyalg",
    "RSA",
    "-keysize",
    "2048",
    "-validity",
    "10000",
    "-storepass",
    password,
    "-keypass",
    password,
    "-dname",
    "CN=MobiStack, OU=Mobile, O=Prabhix Technologies Pvt Ltd, L=India, C=IN",
  ];
  const created = spawnSync(keytool, args, { stdio: "inherit" });
  if (created.status !== 0) {
    console.warn("Could not create a release keystore. Falling back to debug signing.");
    return;
  }
  fs.writeFileSync(
    propsFile,
    [
      "storeFile=mobistack-release.keystore",
      `storePassword=${password}`,
      "keyAlias=mobistack",
      `keyPassword=${password}`,
      "",
    ].join("\n"),
    { mode: 0o600 },
  );
  fs.mkdirSync(path.join(__dirname, "..", ".secrets"), { recursive: true });
  fs.copyFileSync(storeFile, path.join(__dirname, "..", ".secrets", "mobistack-release.keystore"));
  fs.copyFileSync(propsFile, path.join(__dirname, "..", ".secrets", "keystore.properties"));
  console.log("Created mobile/android/app/mobistack-release.keystore (not committed).");
}

function patchReleaseSigning() {
  const gradle = path.join(appDir, "build.gradle");
  if (!fs.existsSync(gradle)) {
    return;
  }
  let src = fs.readFileSync(gradle, "utf8");
  if (src.includes("keystore.properties")) {
    return;
  }
  const load = `
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}
`;
  src = src.replace("android {", `${load}\nandroid {`);
  if (src.includes("signingConfigs {") && !src.includes("keystoreProperties['keyAlias']")) {
    src = src.replace(
      "            keyPassword 'android'\n        }\n    }",
      "            keyPassword 'android'\n        }\n        release {\n            if (keystorePropertiesFile.exists()) {\n                keyAlias keystoreProperties['keyAlias']\n                keyPassword keystoreProperties['keyPassword']\n                storeFile file(keystoreProperties['storeFile'])\n                storePassword keystoreProperties['storePassword']\n            }\n        }\n    }",
    );
  }
  src = src.replace(
    "signingConfig signingConfigs.debug\n            def enableShrinkResources",
    "signingConfig keystorePropertiesFile.exists() ? signingConfigs.release : signingConfigs.debug\n            def enableShrinkResources",
  );
  fs.writeFileSync(gradle, src);
  console.log("Patched android/app/build.gradle for release signing.");
}

function findKeytool() {
  const candidates = [
    process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "keytool.exe" : "keytool"),
    process.platform === "win32" &&
      path.join(process.env.PROGRAMFILES || "C:\\Program Files", "Android", "Android Studio", "jbr", "bin", "keytool.exe"),
    process.platform === "win32" &&
      path.join(process.env.LOCALAPPDATA || "", "Programs", "Android", "Android Studio", "jbr", "bin", "keytool.exe"),
    "keytool",
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (candidate === "keytool") {
      const check = spawnSync("keytool", ["-help"], { stdio: "ignore", shell: true });
      if (check.status === 0 || check.status === 1) {
        return "keytool";
      }
      continue;
    }
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}
