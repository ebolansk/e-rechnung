# Mitwirken

Danke für Ihr Interesse. Fehlerberichte, Ideen und Pull Requests sind willkommen.

## Fehler und Ideen
Über die [Issues](https://github.com/ebolansk/e-rechnung/issues) mit den Vorlagen. **Keine echten Rechnungen, Namen, Kontodaten oder Steuernummern** anhängen; ein erfundenes oder anonymisiertes PDF genügt. Sicherheitslücken bitte nicht öffentlich melden, siehe [SECURITY.md](SECURITY.md).

## Entwicklung
```bash
scripts/setup-tools.sh     # lädt JDK 21 und Maven nach .tools/
scripts/test.sh            # alle Tests (alternativ: mvn test mit JDK 21)
scripts/build-dist.sh      # Auslieferungsordner für Windows
```

Optional, aber empfohlen: `git config core.hooksPath .githooks` führt vor jedem Push die Tests aus und bricht bei einem Fehler ab.

## Pull Requests
- Ein Thema pro Pull Request, kurz begründet.
- Neue oder geänderte Funktion braucht einen Test; `mvn test` muss grün sein.
- Testdaten sind erfunden (siehe `beispiele/` und `SampleInvoices`). Keine echten Rechnungen oder personenbezogenen Daten.
- Texte im Programm und in der Dokumentation sind deutsch, mit echten Umlauten.
- `CHANGELOG.md` im Abschnitt „Unveröffentlicht“ ergänzen.
- Neue Abhängigkeiten nur mit kompatibler Lizenz (Apache 2.0, MIT, BSD, EPL, MPL 2.0) und Eintrag in `NOTICE`.

## Lizenz der Beiträge
Mit einem Beitrag erklären Sie sich einverstanden, dass er unter der [Apache License 2.0](LICENSE) des Projekts steht (Abschnitt 5 der Lizenz). Sie bestätigen, dass Sie den Beitrag selbst geschrieben haben oder dazu berechtigt sind.

## Verhalten
Es gilt der [Verhaltenskodex](CODE_OF_CONDUCT.md).
