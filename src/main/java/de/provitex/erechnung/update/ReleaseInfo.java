// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

/** Ein GitHub-Release mit Update-Paket. sha256 stammt aus dem Release (Asset .sha256 oder Zeile „SHA-256:“ in der Beschreibung). */
public record ReleaseInfo(String tag, String version, String notes, String packageName, String packageUrl, long size,
                          String sha256) {
}
