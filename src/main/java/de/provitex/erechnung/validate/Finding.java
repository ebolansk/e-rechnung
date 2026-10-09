// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.validate;

public record Finding(String severity, String ruleId, String location, String message) {
}
