#!/usr/bin/env bash
# Creates the release signing key (macOS/Linux). Keep release.jks and passwords safe.
set -e
cd "$(dirname "$0")/.."
keytool -genkeypair -v -keystore release.jks -alias iktishafat -keyalg RSA -keysize 4096 -validity 10000
echo "For GitHub Actions secret IKT_KEYSTORE_BASE64:"
echo "  base64 -w0 release.jks"
