// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

/** Ein GitHub-Release mit Update-Paket. sha256 stammt aus dem Release (Asset .sha256 oder Zeile „SHA-256:“ in der Beschreibung),
 * signature ist die Ed25519-Signatur des Pakets (Asset .sig, Base64) oder null, wenn das Release keine hat. */
public record ReleaseInfo(String tag, String version, String notes, String packageName, String packageUrl, long size,
                          String sha256, String signature) {
}
