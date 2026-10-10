// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

/** Quelle der Updates: GitHub-Repository (owner/name). Das Token ist optional und nur für ein privates Repository (Fork) nötig.
 * checkOnStart (Standard aus): beim Programmstart höchstens einmal täglich im Hintergrund nach einer neuen Version fragen.
 * signingKey (Base64, nur für einen Fork mit eigenem Release-Schlüssel): öffentlicher Ed25519-Schlüssel, gegen den die Pakete
 * geprüft werden; leer gilt der in die Anwendung eingebaute Schlüssel. */
public record UpdateSettings(String repo, String token, boolean checkOnStart, String signingKey) {
    public static final String DEFAULT_REPO = "ebolansk/e-rechnung";

    public UpdateSettings(String repo, String token) {
        this(repo, token, false, null);
    }

    public UpdateSettings(String repo, String token, boolean checkOnStart) {
        this(repo, token, checkOnStart, null);
    }

    public UpdateSettings withCheckOnStart(boolean value) {
        return new UpdateSettings(repo, token, value, signingKey);
    }

    public UpdateSettings {
        repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
        token = token == null ? "" : token.trim();
        signingKey = signingKey == null ? "" : signingKey.trim();
    }

    public boolean usable() {
        return repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }
}
