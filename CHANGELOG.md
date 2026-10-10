# Changelog

Alle nennenswerten Änderungen am E-Rechnung-Tool. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [Unveröffentlicht]

## [0.1.0] - 2026-10-10

Erste Version, eine Machbarkeitsstudie (Proof of Concept).

### Hinzugefügt
- PDF-Rechnungen per Drag & Drop einlesen, auch mehrere auf einmal; die Felder werden regelbasiert aus Text und Seitenpositionen **vorgeschlagen** (Spalten, mehrere Steuerzeilen, Rabattzeilen, Währungspräfix, Zahlungsziel in Tagen). Beträge werden strikt gelesen.
- Übersicht mit Fortschritt je PDF; Prüfmaske mit PDF-Ansicht, Pflichtfeld-Prüfung, Summenabgleich und Tabs für Beleg, Verkäufer, Käufer, Positionen und Prüfung. Abweichungen müssen ausdrücklich bestätigt werden.
- Erzeugung von **XRechnung** (XR 3.0) und **ZUGFeRD** (Profil EN 16931, PDF/A-3) mit formaler Validierung und Prüfprotokoll. Zuerst entsteht nur ein Entwurf; ins Archiv kommt die Rechnung mit „Ins Archiv übernehmen“. Die Beträge im erzeugten XML werden mit den bestätigten Summen verglichen. Nicht eingebettete Schriften werden bei ZUGFeRD abgelehnt.
- Archiv mit SHA-256-Hashes, Duplikat-Erkennung, atomarem Schreiben und Hash-Kette im Protokoll; Live-Suche und Integritätsprüfung (beim Start und im Archiv). Ein beschädigtes Protokoll wird nie überschrieben.
- Mandanten- und Konfigurationsdialog; IBAN-Prüfsumme, Pflichtangaben je Format.
- KI-Hilfe (optional, standardmäßig aus): Claude API oder OpenAI-kompatibler Endpunkt, Bestätigungsdialog vor dem Senden, feldweise Übernahme mit Plausibilitätsprüfung, Eintrag im Protokoll. Der API-Key wird nur auf Wunsch und unter Windows verschlüsselt (DPAPI) gespeichert.
- Updates über GitHub-Releases: Jede Version liegt in einem eigenen Ordner unter `versionen/`, ein Zeiger bestimmt die laufende. Pakete tragen eine Ed25519-Signatur, ein Fenster mit Ladebalken zeigt das Einspielen, und startet die neue Version nicht, kehrt der nächste Start automatisch zur vorherigen zurück.
- Nutzungs- und Haftungshinweis beim ersten Start, Fenster-Icon, 12 erfundene Beispielrechnungen von zwei Ausstellern in `beispiele/`.
- Auslieferungsordner für Windows x64 mit mitgelieferter Java-Laufzeit und `start.cmd`.
- Freiwillige Unterstützung: Menüpunkt „Hilfe → Projekt unterstützen (Buy me a coffee)“, ohne Anspruch auf Leistung.
- Open Source unter der Apache License 2.0 (`LICENSE`, `NOTICE`), `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, Issue-Vorlagen, CI und Dependabot.

[Unveröffentlicht]: https://github.com/ebolansk/e-rechnung/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ebolansk/e-rechnung/releases/tag/v0.1.0
