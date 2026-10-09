# Sicherheit

## Unterstützte Versionen
Sicherheitskorrekturen gibt es für die jeweils neueste veröffentlichte Version.

## Sicherheitslücke melden
Bitte **nicht** als öffentliches Issue. Nutzen Sie die vertrauliche Meldung von GitHub:
<https://github.com/ebolansk/e-rechnung/security/advisories/new>

Beschreiben Sie, was betroffen ist und wie man es nachstellt, ohne echte Rechnungs- oder Personendaten. Das Projekt wird in der Freizeit gepflegt; eine erste Rückmeldung ist in der Regel innerhalb weniger Tage möglich, eine Frist kann nicht zugesagt werden.

## Was das Tool tut und nicht tut
- Es arbeitet lokal. Netzwerkzugriffe gibt es nur für die **optionale** KI-Hilfe (nach Bestätigung je Rechnung) und für die Update-Suche (auf Klick, auf Wunsch beim Start).
- Der API-Key der KI-Hilfe bleibt standardmäßig im Arbeitsspeicher; ein optionales Update-Token liegt unverschlüsselt in `daten/update.json`.
- Updates werden nur per SHA-256 aus dem Release geprüft, **nicht signiert**: Wer das GitHub-Konto kontrolliert, kann Paket und Summe gemeinsam austauschen.
- Das Archiv macht Änderungen nachweisbar (Hashes, Hash-Kette), verhindert sie aber nicht.
