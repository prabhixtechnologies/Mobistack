const { spawnSync } = require("child_process");
const path = require("path");
const fs = require("fs");

const iosDir = path.join(__dirname, "..", "ios");
const workspace = path.join(iosDir, "FixFlow.xcworkspace");
const project = path.join(iosDir, "FixFlow.xcodeproj");

const help = `
iOS cannot be compiled on Windows. Apple only ships the iOS SDK with Xcode on macOS.

The native Xcode project is already in this repo:
  ${iosDir}

On a Mac (simulator):
  cd mobile
  npm install
  cd ios && pod install && cd ..
  npx expo run:ios

On a Mac (your iPhone, USB):
  cd mobile
  npm install
  cd ios && pod install && cd ..
  npx expo run:ios --device

Or open ios/FixFlow.xcworkspace in Xcode (never the .xcodeproj after pods),
select your Apple Team under Signing & Capabilities, pick a simulator or
your iPhone, and press Run.

Point the app at the API on this PC (replace the LAN IP):
  EXPO_PUBLIC_API_URL=http://192.168.1.20:8080 npx expo run:ios --device

Cloud build from this Windows machine (needs Expo login):
  npx eas-cli login
  npm run ios:eas            # simulator .app
  npm run ios:eas-device     # installable device build (Apple account)
`;

if (process.platform !== "darwin") {
  console.log(help);
  process.exit(process.argv.includes("--require-binary") ? 1 : 0);
}

if (!fs.existsSync(project)) {
  console.error("ios/FixFlow.xcodeproj is missing.");
  process.exit(1);
}

const pod = spawnSync("pod", ["install"], { cwd: iosDir, stdio: "inherit", shell: true });
if (pod.status !== 0) {
  process.exit(pod.status ?? 1);
}

const destination = process.argv.includes("device")
  ? "generic/platform=iOS"
  : "platform=iOS Simulator,name=iPhone 16";

const args = [
  "-workspace",
  workspace,
  "-scheme",
  "FixFlow",
  "-configuration",
  "Debug",
  "-destination",
  destination,
  "-derivedDataPath",
  path.join(iosDir, "build"),
  "build",
];

const result = spawnSync("xcodebuild", args, { cwd: iosDir, stdio: "inherit" });
process.exit(result.status ?? 1);
