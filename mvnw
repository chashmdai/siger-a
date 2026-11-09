#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
HOME_MAVEN="$BASE/.tools/apache-maven-3.9.11"
if [ ! -x "$HOME_MAVEN/bin/mvn" ]; then
  mkdir -p "$BASE/.tools"
  URL='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
  curl --fail --location --proto '=https' "$URL" -o "$BASE/.tools/maven.zip"
  EXPECTED=$(curl --fail --location --proto '=https' "$URL.sha512" | awk '{print $1}')
  ACTUAL=$(sha512sum "$BASE/.tools/maven.zip" | awk '{print $1}')
  [ "$ACTUAL" = "$EXPECTED" ] || { echo 'SHA-512 no coincide' >&2; exit 1; }
  unzip -q "$BASE/.tools/maven.zip" -d "$BASE/.tools"
  chmod +x "$HOME_MAVEN/bin/mvn"
fi
exec "$HOME_MAVEN/bin/mvn" "$@"
