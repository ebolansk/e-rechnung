# E-Rechnung-Tool

<img src="docs/images/logo.png" alt="Logo" width="96">

Das Tool nimmt PDF-Ausgangsrechnungen per Drag & Drop entgegen und erzeugt daraus eine **XRechnung** oder ein **ZUGFeRD** (Profil EN 16931). Es prüft das Ergebnis, erstellt ein Prüf-Protokoll und legt alles mit Hashes im Archiv ab (Änderungen werden nachweisbar).

> **Machbarkeitsstudie:** Das Tool ist ein Proof of Concept (Version 0.x), kein fertiges oder abgenommenes Produkt. Setzen Sie es nur ein, wenn Sie jedes Ergebnis selbst prüfen; für den produktiven, steuerlich relevanten Einsatz ist es nicht freigegeben.
>
> **Wichtig:** Das Tool unterstützt beim Erstellen von E-Rechnungen. Die Daten werden aus dem PDF **vorgeschlagen**, Sie bestätigen sie in der Prüfmaske. Das Prüf-Protokoll bestätigt nur die **formale Konformität** des Ausgabeformats, nicht die inhaltliche Richtigkeit. Die inhaltliche Verantwortung liegt beim Rechnungsaussteller. Rechtliche Fragen (Fristen, Aufbewahrung, Behandlung von PDF-Original und E-Rechnung) bitte mit der Steuerberatung klären.

![Prüfmaske mit einer erfundenen Beispielrechnung](docs/images/pruefmaske.png)

## Start

1. ZIP entpacken (zum Beispiel auf den Desktop). Es wird nichts installiert, es gibt keine Registry-Einträge und keine Admin-Rechte.
2. `start.cmd` ausführen.

Das Tool schreibt nur in zwei Bereiche: in den **Archivordner** (Standard: `archiv/` neben `start.cmd`) und in den Ordner **`daten/`** (Konfiguration, Mandanten, Protokoll, Zwischenspeicher). Der Programmordner muss dafür beschreibbar sein.

## Bedienung

1. PDF-Rechnungen auf das Fenster ziehen (oder „Dateien auswählen…").
2. Wird der Rechnungsaussteller nicht erkannt, erscheint „Neuer Rechnungsaussteller erkannt". Daten prüfen, ergänzen, „Anlegen".
3. In der **Prüfmaske** links das PDF, rechts die Felder. Alle Felder prüfen und korrigieren. Unten stehen immer sichtbar: der Summenabgleich (berechnet gegen im PDF gelesen), der Beleg-Status und die offenen Punkte. „Erzeugen und archivieren" wird erst aktiv, wenn nichts mehr offen ist. Weichen die Summen im PDF von den berechneten ab, müssen Sie das ausdrücklich bestätigen (die Bestätigung wird im Archiv festgehalten). Den Beleg-Status können Sie bei Bedarf selbst festlegen (zum Beispiel nach Rücksprache mit der Steuerberatung); die automatische Einstufung bleibt in `daten.json` erhalten. Rabattzeilen werden als negative Menge mit positivem Preis erfasst.
4. Das Tool erzeugt das Ausgabeformat, prüft es und legt es nur bei bestandener Prüfung im Archiv ab.

Doppelklick auf eine Zeile der Tabelle öffnet den Archivordner der Rechnung.

## Konfiguration (Datei → Konfiguration)

- **Archivordner** und **Ordnervorlage** mit den Platzhaltern `{Mandant}`, `{Jahr}`, `{Monat}`, `{Rechnungsnummer}`. Standard: `{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}`.
- **Ausgabeformat:** XRechnung (reines XML) oder ZUGFeRD (PDF/A-3 mit eingebettetem XML).
- **Stichtag** für den Beleg-Status (Standard 01.01.2027).
- **Mandanten:** Rechnungsaussteller verwalten. Pflicht sind Anschrift, USt-IdNr. oder Steuernummer, E-Mail, IBAN sowie Ansprechpartner **und** Telefon (die XRechnung verlangt beides).

## Archiv

Pro Rechnung ein Ordner:

```
Archiv/Muster GmbH/2027/01/RE-2027-0001/
  vorlage-original.pdf     die abgelegte PDF-Vorlage, unverändert (geht nicht an den Kunden)
  ausgabe-xrechnung.xml    oder ausgabe-zugferd.pdf
  pruefprotokoll.html      Ergebnis der Format-Prüfung
  daten.json               bestätigte Felder, Hashes (SHA-256), Beleg-Status, Aufbewahrung bis
```

Archivierte Rechnungen werden vom Tool nie überschrieben, bearbeitet oder gelöscht. Änderungen an Dateien lassen sich über die SHA-256-Hashes in `daten.json` und die Hash-Kette in `daten/protokoll.jsonl` nachweisen. Das Tool setzt keinen Dateischreibschutz. Aufbewahrung: 8 Jahre ab Ende des Ausstellungsjahres (`retainUntil` in `daten.json`).

## Bekannte Grenzen

- **ZUGFeRD** braucht ein PDF mit eingebetteten Schriften (nicht eingebettete wie Helvetica werden abgelehnt). Abhilfe: PDF mit eingebetteten Schriften erzeugen oder XRechnung wählen.
- **Auslesen** ist regelbasiert und nur ein Vorschlag; bei ungewöhnlichen Layouts ist mehr Korrektur nötig.
- **Archiv** (Menü „Archiv“): Suche nach Nummer, Käufer, Datum, Betrag, Beleg-Status u. a. Die **Integritätsprüfung** (beim Start und im Menü) vergleicht Dateien mit den Hashes in `daten.json` und dem Protokoll. Änderungen werden nachweisbar, nicht verhindert.
- **KI-Hilfe** (Konfiguration → KI, Standard: aus) schlägt Felder vor (Claude API oder OpenAI-kompatibel). Gesendet wird erst nach Bestätigung des angezeigten Rechnungstexts; der Key bleibt standardmäßig im Arbeitsspeicher.
- **Updates** (Hilfe → Nach Updates suchen): auf Klick, auf Wunsch beim Start (Konfiguration → Update, Standard aus). Das Paket kommt aus den GitHub-Releases dieses Repositories, wird per SHA-256 geprüft (keine Signatur) und beim nächsten Start eingespielt. Die alte Version bleibt als `app.alt/` für „Vorherige Version wiederherstellen“.

## Lizenz und Haftung

Copyright 2026 Stefan Schmitt. Das Projekt steht unter der [Apache License 2.0](LICENSE); Hinweise zu Drittkomponenten stehen in [NOTICE](NOTICE). Die Software wird **ohne Gewährleistung und ohne Haftung** bereitgestellt, soweit gesetzlich zulässig (Abschnitte 7 und 8 der Lizenz). Beim ersten Start muss ein Nutzungs- und Haftungshinweis bestätigt werden (Bestätigung in `daten/hinweis.json` und im Protokoll; später unter Hilfe → Nutzungs- und Haftungshinweis). Die inhaltliche Verantwortung für Rechnungen liegt beim Rechnungsaussteller.

## Mitwirken und Sicherheit

Fehler, Ideen und Pull Requests sind willkommen: siehe [CONTRIBUTING.md](CONTRIBUTING.md) und den [Verhaltenskodex](CODE_OF_CONDUCT.md). Sicherheitslücken bitte vertraulich melden ([SECURITY.md](SECURITY.md)). Änderungen pro Version stehen im [CHANGELOG](CHANGELOG.md). Beispielrechnungen (alle erfunden) liegen in [`beispiele/`](beispiele/).

## Entwicklung

```bash
scripts/setup-tools.sh     # lädt JDK 21 und Maven nach .tools/
scripts/test.sh            # alle Tests
scripts/build-dist.sh      # baut dist/E-Rechnung und das ZIP für Windows x64
```

Release: Version in `pom.xml` und `Version.java` erhöhen, `scripts/build-dist.sh`, dann `scripts/release.sh` (fragt vor dem Veröffentlichen nach).
