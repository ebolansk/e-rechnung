# Changelog

Alle nennenswerten Änderungen am E-Rechnung-Tool. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [Unveröffentlicht]

## [0.1.0] - 2026-10-09

Erste Version, eine Machbarkeitsstudie (Proof of Concept).

### Hinzugefügt
- PDF-Rechnungen per Drag & Drop einlesen; die Felder werden regelbasiert aus Text und Seitenpositionen **vorgeschlagen** (Spalten, mehrere Steuerzeilen, Rabattzeilen, Währungspräfix, Zahlungsziel in Tagen).
- Prüfmaske mit PDF-Ansicht, Pflichtfeld-Prüfung, Summenabgleich und Beleg-Status; Abweichungen müssen ausdrücklich bestätigt werden.
- Erzeugung von **XRechnung** (XR 3.0) und **ZUGFeRD** (Profil EN 16931, PDF/A-3) mit Validierung. Nicht eingebettete Schriften werden bei ZUGFeRD abgelehnt.
- Archiv mit SHA-256-Hashes, Duplikat-Erkennung, atomarem Schreiben und Hash-Kette im Protokoll; Suche im Archiv und Integritätsprüfung (beim Start und per Menü).
- Mandanten- und Konfigurationsdialog.
- KI-Hilfe (optional, standardmäßig aus): Claude API oder OpenAI-kompatibler Endpunkt, Bestätigungsdialog vor dem Senden, feldweise Übernahme mit Plausibilitätsprüfung, Eintrag im Protokoll.
- Update über GitHub-Releases ohne Token (öffentliches Repository): auf Klick oder auf Wunsch einmal täglich beim Start (Standard aus), SHA-256-Prüfung, Austausch beim nächsten Start, Rückkehr zur Vorversion.
- Nutzungs- und Haftungshinweis beim ersten Start, Fenster-Icon, 13 erfundene Beispielrechnungen in `beispiele/`.
- Auslieferungsordner für Windows x64 mit mitgelieferter Java-Laufzeit und `start.cmd`.
- Open Source unter der Apache License 2.0 (`LICENSE`, `NOTICE`), `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, Issue-Vorlagen, CI und Dependabot.

[Unveröffentlicht]: https://github.com/ebolansk/e-rechnung/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ebolansk/e-rechnung/releases/tag/v0.1.0
