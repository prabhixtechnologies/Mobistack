#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm install
cd ios
pod install
cd ..
if [[ "${1:-}" == "device" ]]; then
  exec npx expo run:ios --device
fi
exec npx expo run:ios
