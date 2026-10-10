# Sicherheit

## Unterstützte Versionen
Sicherheitskorrekturen gibt es für die jeweils neueste veröffentlichte Version.

## Sicherheitslücke melden
Bitte **nicht** als öffentliches Issue. Nutzen Sie die vertrauliche Meldung von GitHub:
<https://github.com/ebolansk/e-rechnung/security/advisories/new>

Beschreiben Sie, was betroffen ist und wie man es nachstellt, ohne echte Rechnungs- oder Personendaten. Das Projekt wird in der Freizeit gepflegt; eine erste Rückmeldung ist in der Regel innerhalb weniger Tage möglich, eine Frist kann nicht zugesagt werden.

## Was das Tool tut und nicht tut
- Es arbeitet lokal. Netzwerkzugriffe gibt es nur für die **optionale** KI-Hilfe (nach Bestätigung je Rechnung) und für die Update-Suche (auf Klick, auf Wunsch beim Start).
- Der API-Key der KI-Hilfe bleibt standardmäßig im Arbeitsspeicher; auf Wunsch wird er unter Windows per DPAPI verschlüsselt gespeichert. Ein optionales Update-Token liegt unverschlüsselt in `daten/update.json`.
- Update-Pakete tragen eine **Ed25519-Signatur** und werden gegen den in die Anwendung eingebauten öffentlichen Schlüssel geprüft; nicht oder falsch signierte Pakete werden weder heruntergeladen noch installiert. Die SHA-256-Summe im Release schützt nur vor beschädigten Downloads. Wer den privaten Release-Schlüssel besitzt, kann gültige Updates ausstellen; er liegt nicht im Repository.
- Restrisiko: `daten/update.json` kann `repo` und `signingKey` (für Forks mit eigenem Schlüssel) überschreiben. Wer in `daten/` oder den Programmordner schreiben kann, kann damit eigene Pakete einspielen lassen (und könnte das Programm ohnehin austauschen). Schützen Sie den Programmordner entsprechend.
- Das Archiv macht Änderungen nachweisbar (Hashes, Hash-Kette), verhindert sie aber nicht.
