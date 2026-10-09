// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

public enum AiProvider {
    OFF("Aus"),
    CLAUDE("Claude API"),
    OPENAI("OpenAI-kompatibel");

    private final String label;

    AiProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
