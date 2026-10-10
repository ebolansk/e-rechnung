#!/usr/bin/env bash
# Baut dist/E-Rechnung (Windows x64) und das ZIP. Voraussetzung: scripts/setup-tools.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
DIST=dist/E-Rechnung
# Jede Version liegt in einem eigenen Ordner (versionen/<version>), versionen/aktuell.txt zeigt auf die laufende (siehe launcher/Swap).
APP="$DIST/versionen/$VERSION"
rm -rf dist && mkdir -p "$APP/lib" "$DIST/daten"
# Der Commit, aus dem dieses Paket stammt (scripts/release.sh prüft, dass der Tag auf genau diesem Commit liegt).
{ git rev-parse HEAD; } > dist/BUILD_COMMIT
[ -z "$(git status --porcelain)" ] || echo "WARNUNG: Arbeitsstand nicht committet, das Paket stammt nicht aus HEAD." >&2

mvn -q -B -ntp -DskipTests clean package dependency:copy-dependencies -DoutputDirectory="$APP/lib" -DincludeScope=runtime
cp "target/e-rechnung-${VERSION}.jar" "$APP/lib/e-rechnung.jar"

# Windows-JDK (nur die jmods) für den Cross-jlink: festgelegtes Release mit Prüfsumme (scripts/tool-versions.sh). Der Ordner heißt nach
# dem Release, damit eine Änderung dort einen frischen Download erzwingt und die ausgelieferte Runtime nicht still veraltet.
source scripts/tool-versions.sh
WIN=".tools/win-jdk-${JDK_RELEASE//+/_}"
if [ ! -d "$WIN" ]; then
  # Erst in ein Zwischenverzeichnis, dann umbenennen: ein Abbruch (Download, Prüfsumme, Entpacken) hinterlässt kein halbes JDK.
  rm -rf "$WIN.tmp" && mkdir -p "$WIN.tmp"
  download_verified "$JDK_URL_BASE/$JDK_WIN_X64_FILE" .tools/win-jdk.zip sha256 "$JDK_WIN_X64_SHA256"
  if command -v unzip >/dev/null 2>&1; then unzip -q .tools/win-jdk.zip -d "$WIN.tmp"; else python3 -m zipfile -e .tools/win-jdk.zip "$WIN.tmp"; fi
  rm .tools/win-jdk.zip
  mv "$WIN.tmp" "$WIN"
fi
WINJMODS=$(ls -d "$WIN"/*/jmods | head -1)

MODULES=$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps -cp "$APP/lib/*" "$APP/lib/e-rechnung.jar" 2>/dev/null || true)
[ -n "$MODULES" ] || echo "WARNUNG: jdeps lieferte keine Modulliste; es gilt nur die feste Liste, ein fehlendes Modul fiele erst unter Windows auf." >&2
MODULES="${MODULES:+$MODULES,}java.desktop,java.net.http,java.xml,java.naming,java.logging,java.sql,java.management,java.prefs,java.datatransfer,java.scripting,jdk.unsupported,jdk.localedata,jdk.crypto.ec"
jlink --module-path "$WINJMODS" --add-modules "$MODULES" --strip-java-debug-attributes --no-header-files --no-man-pages --compress zip-6 --output "$DIST/runtime"

# Launcher: schaltet vor dem Start auf ein vorbereitetes Update um. Er liegt im Programmordner und zusätzlich in der Version
# (Update-Pakete bestehen nur aus dieser); die Anwendung übernimmt ihn beim Start in den Programmordner (UpdateStartup.housekeeping).
mkdir -p "$APP/launcher" "$DIST/launcher"
jar --create --file "$APP/launcher/launcher.jar" -C target/classes io/github/ebolansk/erechnung/launcher
cp "$APP/launcher/launcher.jar" "$DIST/launcher/launcher.jar"

# Startbildschirm (wird von start.cmd per -splash gezeigt)
java -Djava.awt.headless=true -cp target/classes io.github.ebolansk.erechnung.ui.SplashImage "$APP/splash.png"

sed 's/$/\r/' scripts/start.cmd.template > "$APP/start.cmd"
cp "$APP/start.cmd" "$DIST/start.cmd"
printf 'versionen/%s\n' "$VERSION" > "$DIST/versionen/aktuell.txt"
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
cp -r "$APP" "$UPD/app"
printf '{"version":"%s","gebaut":"%s"}\n' "$VERSION" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$UPD/manifest.json"
UPDZIP="E-Rechnung-update-${VERSION}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd "$UPD" && zip -qr "../$UPDZIP" manifest.json app)
else
  (cd "$UPD" && python3 -m zipfile -c "../$UPDZIP" manifest.json app)
fi
rm -rf "$UPD"
(cd dist && sha256sum "$UPDZIP" > "$UPDZIP.sha256")
# Signatur (Ed25519): ohne Schlüssel entsteht ein unsigniertes Paket, das die Anwendung nicht installiert und release.sh nicht veröffentlicht.
KEY="${ERECHNUNG_SIGNING_KEY:-$HOME/.config/e-rechnung/update-signing.key}"
if [ -f "$KEY" ]; then
  java -cp target/classes io.github.ebolansk.erechnung.update.UpdateSignature sign "$KEY" "dist/$UPDZIP" > "dist/$UPDZIP.sig"
  java -cp target/classes io.github.ebolansk.erechnung.update.UpdateSignature verify "dist/$UPDZIP" "dist/$UPDZIP.sig"
else
  echo "WARNUNG: kein Signaturschlüssel ($KEY), das Update-Paket bleibt unsigniert." >&2
fi
echo "Mitgelieferte Java-Laufzeit: Temurin $JDK_RELEASE"
du -sh "$DIST" "dist/E-Rechnung-${VERSION}-win-x64.zip"
ls -l dist/E-Rechnung-update-* 
