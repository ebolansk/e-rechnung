// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.audit;

import java.io.IOException;
import java.util.Map;

/** Schreibt Protokolleinträge. Als Schnittstelle, damit Fehlerfälle testbar sind. */
@FunctionalInterface
public interface AuditTrail {
    AuditLog.Entry append(String action, Map<String, String> details) throws IOException;
}
