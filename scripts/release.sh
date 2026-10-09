#!/usr/bin/env bash
# Veröffentlicht das Update-Paket als GitHub-Release (öffentliches Repository). Voraussetzung: scripts/build-dist.sh lief,
# `gh auth login` ist erledigt, Arbeitsstand ist committet und gepusht. Veröffentlicht erst nach ausdrücklicher Bestätigung.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
TOOL=$(grep -m1 'TOOL =' src/main/java/de/provitex/erechnung/Version.java | sed -E 's/.*"(.*)".*/\1/')
[ "$VERSION" = "$TOOL" ] || { echo "pom.xml ($VERSION) und Version.TOOL ($TOOL) stimmen nicht überein." >&2; exit 1; }
ZIP="dist/E-Rechnung-update-${VERSION}.zip"
[ -f "$ZIP" ] && [ -f "$ZIP.sha256" ] || { echo "Update-Paket fehlt: erst scripts/build-dist.sh ausführen." >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Arbeitsstand ist nicht committet." >&2; exit 1; }
SHA=$(cut -d' ' -f1 "$ZIP.sha256")
echo "Veröffentlicht wird Release v${VERSION} mit $ZIP (und dem Windows-ZIP, falls vorhanden)"
echo "SHA-256: $SHA"
read -r -p "Wirklich veröffentlichen? (ja/nein) " answer
[ "$answer" = "ja" ] || { echo "Abgebrochen."; exit 1; }
NOTES="${1:-$(awk -v v="[${VERSION}]" '/^## /{p=index($0,v)>0; next} p' CHANGELOG.md)}"
[ -n "$NOTES" ] || NOTES="Version ${VERSION}"
FULL="dist/E-Rechnung-${VERSION}-win-x64.zip"
ASSETS=("$ZIP" "$ZIP.sha256")
[ -f "$FULL" ] && ASSETS+=("$FULL")
gh release create "v${VERSION}" "${ASSETS[@]}" --title "E-Rechnung-Tool ${VERSION} (Machbarkeitsstudie)" --notes "${NOTES}

SHA-256: ${SHA}"
