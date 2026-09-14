# MobiStack native app

This is the **Android / iOS counter app** for MobiStack (Prabhix Technologies Pvt Ltd). It is a standalone native binary — not Expo Go.

- Package: `app.prabhix.fixflow`
- Offline: SQLite snapshot + outbox
- Camera: barcode scan on Sales and Inventory
- Auth: Prabhix Identity only (OIDC / PKCE via Custom Tab). No in-app password / OTP / magic-link.

## Produce an APK (Windows)

You need JDK 17+, Android Studio (or command-line SDK), and `ANDROID_HOME` set.

```powershell
cd mobile
npm install
npm run apk:debug
```

The installable file is:

`mobile/android/app/build/outputs/apk/debug/app-debug.apk`

Copy it to a phone and install it. For a Play-style release APK:

```powershell
npm run apk
```

`assembleRelease` needs a keystore. For the first local build, Android Studio can generate one under **Build → Generate Signed Bundle / APK**.

GitHub Actions on `main` uploads:

- `mobistack-android-apk` — installable debug APK
- `mobistack-ios-module` — Xcode project zip, plus a simulator `.app` when the Mac runner can compile

A signed iPhone IPA still needs an Apple Developer account (EAS `preview-device` or Xcode signing).

Cloud builds (no local SDK):

```powershell
npx eas login
npx eas build -p android --profile preview
```

## Run on a device / emulator

```powershell
npx expo run:android
```

That compiles the Gradle project and installs the real app. It does **not** open Expo Go.

## Build and run on iOS (Mac)

This Windows machine cannot compile an `.ipa`. Apple only ships the iOS SDK with Xcode. The Xcode project is already in `mobile/ios/` (`FixFlow`, bundle id `app.prabhix.fixflow`).

Copy the repo to a Mac with Xcode 16+, CocoaPods, and Node 20+. Then:

```bash
cd mobile
npm install
cd ios && pod install && cd ..
npx expo run:ios
```

That installs on the Simulator. For a USB iPhone:

```bash
npx expo run:ios --device
```

In Xcode, open **`ios/FixFlow.xcworkspace`** (not the `.xcodeproj` after `pod install`), set your Team under **Signing & Capabilities**, pick a device, and press Run. First launch on a personal team lasts 7 days.

Release builds talk to `https://mobistack.prabhixtechnologies.com` and sign in through
`https://api.prabhixtechnologies.com` (`EXPO_PUBLIC_IDENTITY_ISSUER`). EAS profiles bake both env
vars. Point a local phone at the API on your Windows PC (same Wi-Fi, replace the IP):

```bash
EXPO_PUBLIC_API_URL=http://192.168.1.20:8085 \
EXPO_PUBLIC_IDENTITY_ISSUER=http://192.168.1.20:8081 \
npx expo run:ios --device
```

Identity is required for every build. In-app password login has been removed.

The backend must listen on the LAN, not only localhost. A helper script on the Mac: `bash scripts/macos-ios-build.sh` or `bash scripts/macos-ios-build.sh device`.

### Cloud iOS build from Windows

```powershell
cd mobile
npx eas-cli login
npm run ios:eas            # Simulator .app (no Apple account)
npm run ios:eas-device     # Device build (Apple Developer account)
```
