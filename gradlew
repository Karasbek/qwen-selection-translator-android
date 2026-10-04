#!/usr/bin/env sh
# Lightweight bootstrap used because the generated artifact environment does not
# ship Gradle. Codex may replace this with the official Gradle Wrapper later.
GRADLE_VERSION=9.4.1
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOOT_DIR="$BASE_DIR/.gradle-bootstrap"
DIST_DIR="$BOOT_DIR/gradle-$GRADLE_VERSION"
ZIP="$BOOT_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$BOOT_DIR" || { echo "Cannot create $BOOT_DIR" >&2; return 1 2>/dev/null || false; }
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ZIP" || { echo "Gradle download failed" >&2; return 1 2>/dev/null || false; }
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL" || { echo "Gradle download failed" >&2; return 1 2>/dev/null || false; }
    else
      echo "Need curl or wget to bootstrap Gradle" >&2
      return 1 2>/dev/null || false
    fi
  fi
  command -v unzip >/dev/null 2>&1 || { echo "Need unzip to bootstrap Gradle" >&2; return 1 2>/dev/null || false; }
  rm -rf "$DIST_DIR"
  unzip -q "$ZIP" -d "$BOOT_DIR" || { echo "Gradle unzip failed" >&2; return 1 2>/dev/null || false; }
fi

"$DIST_DIR/bin/gradle" "$@"
