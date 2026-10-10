#!/usr/bin/env bash
# Veröffentlicht ein GitHub-Release (öffentliches Repository). Aufruf: scripts/release.sh [--full-only] [Notizen]
# Voraussetzungen: scripts/build-dist.sh lief auf dem aktuellen Commit, `gh auth login` ist erledigt, der Arbeitsstand ist
# committet und gepusht, das CI für diesen Commit ist grün. Veröffentlicht erst nach ausdrücklicher Bestätigung.
#   --full-only  nur das vollständige ZIP, ohne Update-Paket: für Versionen, die sich nicht per Update einspielen lassen (zum
#                Beispiel nach einer Umbenennung, die start.cmd und Launcher der installierten Version unbrauchbar macht); die
#                Update-Funktion findet dann kein Paket und bietet nichts an.
set -euo pipefail
cd "$(dirname "$0")/.."
FULL_ONLY=0
if [ "${1:-}" = "--full-only" ]; then FULL_ONLY=1; shift; fi
VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
TOOL=$(grep -m1 'TOOL =' src/main/java/io/github/ebolansk/erechnung/Version.java | sed -E 's/.*"(.*)".*/\1/')
[ "$VERSION" = "$TOOL" ] || { echo "pom.xml ($VERSION) und Version.TOOL ($TOOL) stimmen nicht überein." >&2; exit 1; }
source scripts/env.sh
source scripts/tool-versions.sh

# Der Tag muss auf genau dem Commit liegen, aus dem das Paket gebaut wurde.
[ -z "$(git status --porcelain)" ] || { echo "Arbeitsstand ist nicht committet." >&2; exit 1; }
git fetch -q origin
HEAD_COMMIT=$(git rev-parse HEAD)
[ "$HEAD_COMMIT" = "$(git rev-parse origin/main)" ] || { echo "HEAD ist nicht identisch mit origin/main: erst pushen (oder aktualisieren)." >&2; exit 1; }
[ -f dist/BUILD_COMMIT ] && [ "$(cat dist/BUILD_COMMIT)" = "$HEAD_COMMIT" ] || {
  echo "dist/ stammt nicht aus HEAD ($HEAD_COMMIT; gebaut: $(cat dist/BUILD_COMMIT 2>/dev/null || echo unbekannt)): scripts/build-dist.sh erneut ausführen." >&2; exit 1; }
CI=$(gh run list --workflow build.yml --commit "$HEAD_COMMIT" --json status,conclusion -q '.[0] | "\(.status) \(.conclusion)"' 2>/dev/null || true)
[ "$CI" = "completed success" ] || { echo "Das CI für $HEAD_COMMIT ist nicht grün (Stand: ${CI:-kein Lauf gefunden}). Erst abwarten." >&2; exit 1; }

FULL="dist/E-Rechnung-${VERSION}-win-x64.zip"
[ -f "$FULL" ] || { echo "Vollständiges ZIP fehlt: erst scripts/build-dist.sh ausführen." >&2; exit 1; }
ASSETS=("$FULL")
SHA_LINE=""
if [ "$FULL_ONLY" = 0 ]; then
  ZIP="dist/E-Rechnung-update-${VERSION}.zip"
  [ -f "$ZIP" ] && [ -f "$ZIP.sha256" ] || { echo "Update-Paket fehlt: erst scripts/build-dist.sh ausführen." >&2; exit 1; }
  [ -f "$ZIP.sig" ] || { echo "Signatur fehlt (Schlüssel ~/.config/e-rechnung/update-signing.key): ohne sie installiert die Anwendung das Update nicht." >&2; exit 1; }
  java -cp target/classes io.github.ebolansk.erechnung.update.UpdateSignature verify "$ZIP" "$ZIP.sig" || { echo "Die Signatur passt nicht zum eingebauten Schlüssel." >&2; exit 1; }
  SHA=$(cut -d' ' -f1 "$ZIP.sha256")
  ASSETS=("$ZIP" "$ZIP.sha256" "$ZIP.sig" "$FULL")
  SHA_LINE="SHA-256: ${SHA}"
fi

# Release-Notizen vor der Bestätigung ermitteln und anzeigen; ohne Abschnitt im CHANGELOG kein Release.
NOTES="${1:-$(awk -v v="[${VERSION}]" '/^## /{p=index($0,v)>0; next} p' CHANGELOG.md)}"
[ -n "$NOTES" ] || { echo "CHANGELOG.md hat keinen Abschnitt '## [${VERSION}]': Einträge aus 'Unveröffentlicht' dorthin verschieben." >&2; exit 1; }
if [ "$FULL_ONLY" = 1 ]; then
  NOTES="${NOTES}

**Dieses Release enthält bewusst kein Update-Paket.** Bitte das vollständige ZIP laden und in den bisherigen Ordner entpacken (\`daten/\` und das Archiv bleiben erhalten); die Update-Suche der Vorversion bietet dieses Release nicht an."
fi
echo "Veröffentlicht wird Release v${VERSION} auf Commit ${HEAD_COMMIT:0:12} mit: ${ASSETS[*]}"
[ -z "$SHA_LINE" ] || echo "$SHA_LINE"
echo "---- Release-Notizen ----"; echo "$NOTES"; echo "-------------------------"
read -r -p "Wirklich veröffentlichen? (ja/nein) " answer
[ "$answer" = "ja" ] || { echo "Abgebrochen."; exit 1; }
gh release create "v${VERSION}" "${ASSETS[@]}" --target "$HEAD_COMMIT" --title "E-Rechnung-Tool ${VERSION} (Machbarkeitsstudie)" --notes "${NOTES}

${SHA_LINE}

Mitgelieferte Java-Laufzeit im vollständigen ZIP: Temurin ${JDK_RELEASE}"
