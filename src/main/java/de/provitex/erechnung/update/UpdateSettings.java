// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

/** Quelle der Updates: GitHub-Repository (owner/name). Das Token ist optional und nur für ein privates Repository (Fork) nötig.
 * checkOnStart (Standard aus): beim Programmstart höchstens einmal täglich im Hintergrund nach einer neuen Version fragen. */
public record UpdateSettings(String repo, String token, boolean checkOnStart) {
    public static final String DEFAULT_REPO = "ebolansk/e-rechnung";

    public UpdateSettings(String repo, String token) {
        this(repo, token, false);
    }

    public UpdateSettings {
        repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
        token = token == null ? "" : token.trim();
    }

    public boolean usable() {
        return repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }
}
