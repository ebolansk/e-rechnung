#!/usr/bin/env bash
# Baut dist/E-Rechnung (Windows x64) und das ZIP. Voraussetzung: scripts/setup-tools.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
DIST=dist/E-Rechnung
rm -rf dist && mkdir -p "$DIST/app/lib" "$DIST/daten"

mvn -q -B -ntp -DskipTests package dependency:copy-dependencies -DoutputDirectory="$DIST/app/lib" -DincludeScope=runtime
cp "target/e-rechnung-${VERSION}.jar" "$DIST/app/lib/e-rechnung.jar"

# Windows-JDK (nur die jmods) für den Cross-jlink holen
WIN=.tools/win-jdk
if [ ! -d "$WIN" ]; then
  mkdir -p "$WIN"
  curl -fsSL -o .tools/win-jdk.zip "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
  if command -v unzip >/dev/null 2>&1; then unzip -q .tools/win-jdk.zip -d "$WIN"; else python3 -m zipfile -e .tools/win-jdk.zip "$WIN"; fi
  rm .tools/win-jdk.zip
fi
WINJMODS=$(ls -d "$WIN"/*/jmods | head -1)

MODULES=$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps -cp "$DIST/app/lib/*" "$DIST/app/lib/e-rechnung.jar" 2>/dev/null || true)
MODULES="${MODULES:+$MODULES,}java.desktop,java.net.http,java.xml,java.naming,java.logging,java.sql,java.management,java.prefs,java.datatransfer,java.scripting,jdk.unsupported,jdk.localedata,jdk.crypto.ec"
jlink --module-path "$WINJMODS" --add-modules "$MODULES" --strip-java-debug-attributes --no-header-files --no-man-pages --compress zip-6 --output "$DIST/runtime"

# Launcher: tauscht vor dem Start app/ gegen ein vorbereitetes Update. Eigene Jar außerhalb von app/, weil app/ ersetzt wird.
mkdir -p "$DIST/launcher"
jar --create --file "$DIST/launcher/launcher.jar" -C target/classes de/provitex/erechnung/launcher

sed 's/$/\r/' scripts/start.cmd.template > "$DIST/start.cmd"
cp README.md "$DIST/README.txt"
cp LICENSE NOTICE "$DIST/"
if command -v zip >/dev/null 2>&1; then
  (cd dist && zip -qr "E-Rechnung-${VERSION}-win-x64.zip" E-Rechnung)
else
  (cd dist && python3 -m zipfile -c "E-Rechnung-${VERSION}-win-x64.zip" E-Rechnung)
fi
# Update-Paket für „Hilfe → Nach Updates suchen“: nur app/ plus Manifest (Programm, nicht Runtime/Launcher/Daten)
UPD="dist/update-staging"
rm -rf "$UPD" && mkdir -p "$UPD"
cp -r "$DIST/app" "$UPD/app"
printf '{"version":"%s","gebaut":"%s"}\n' "$VERSION" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$UPD/manifest.json"
UPDZIP="E-Rechnung-update-${VERSION}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd "$UPD" && zip -qr "../$UPDZIP" manifest.json app)
else
  (cd "$UPD" && python3 -m zipfile -c "../$UPDZIP" manifest.json app)
fi
rm -rf "$UPD"
(cd dist && sha256sum "$UPDZIP" > "$UPDZIP.sha256")
du -sh "$DIST" "dist/E-Rechnung-${VERSION}-win-x64.zip"
ls -l dist/E-Rechnung-update-* 
