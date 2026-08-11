#!/usr/bin/env bash
# Report the state of the local toolchain. Read-only: installs nothing, changes nothing.
# Exit 0 if everything required is present, 1 if a required tool is missing.
set -uo pipefail

cd "$(dirname "$0")/.."

missing=0
warn=0

check() {              # check <name> <required|optional> <version-command...>
  local name=$1 need=$2; shift 2
  if command -v "$name" >/dev/null 2>&1; then
    printf '  %-10s OK    %s\n' "$name" "$("$@" 2>&1 | head -1)"
  elif [ "$need" = required ]; then
    printf '  %-10s MISSING (required)\n' "$name"; missing=$((missing + 1))
  else
    printf '  %-10s missing (optional)\n' "$name"; warn=$((warn + 1))
  fi
}

echo "=== Tooling ==="
check git      required git --version
check java     required java -version
check adb      required adb version
check maestro  required maestro --version
check emulator optional emulator -version
check jq       optional jq --version
check python3  optional python3 --version

echo
echo "=== Gradle wrapper ==="
if [ -x ./gradlew ]; then
  echo "  ./gradlew            present and executable"
  if [ -f gradle/wrapper/gradle-wrapper.properties ]; then
    echo "  distribution         $(grep -m1 distributionUrl gradle/wrapper/gradle-wrapper.properties | sed 's/.*\///')"
  fi
elif [ -f ./gradlew ]; then
  echo "  ./gradlew            present but NOT executable  -> chmod +x gradlew"
  warn=$((warn + 1))
else
  echo "  ./gradlew            not found (no Gradle project here?)"
  warn=$((warn + 1))
fi

echo
echo "=== Android SDK ==="
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_dir" ] && [ -f local.properties ]; then
  sdk_dir=$(grep -m1 '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2- || true)
  [ -n "$sdk_dir" ] && echo "  source               local.properties"
else
  [ -n "$sdk_dir" ] && echo "  source               ANDROID_HOME / ANDROID_SDK_ROOT"
fi
if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
  echo "  sdk.dir              $sdk_dir"
else
  echo "  sdk.dir              NOT RESOLVED"
  warn=$((warn + 1))
fi

echo
echo "=== Devices (adb devices -l) ==="
if command -v adb >/dev/null 2>&1; then
  adb devices -l | sed 's/^/  /'
  device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  echo "  connected and ready: $device_count"
  [ "$device_count" = "0" ] && echo "  note: UI tests will be SKIPPED without a device"
else
  echo "  adb not available"
fi

echo
echo "=== Summary ==="
if [ "$missing" -gt 0 ]; then
  echo "  $missing required tool(s) missing"
  cat <<'FIXES'

  How to fix:
    git       xcode-select --install            (macOS)  |  apt install git
    java      install a JDK 17+ (Temurin) and set JAVA_HOME
    adb       comes with Android SDK platform-tools; add to PATH:
                export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
    maestro   curl -Ls "https://get.maestro.mobile.dev" | bash
                then: export PATH="$HOME/.maestro/bin:$PATH"
    emulator  install via Android Studio > SDK Manager, then add:
                export PATH="$HOME/Library/Android/sdk/emulator:$PATH"

  Nothing is installed automatically by this script, on purpose.
FIXES
  exit 1
fi

echo "  all required tools present ($warn optional warning(s))"
exit 0
