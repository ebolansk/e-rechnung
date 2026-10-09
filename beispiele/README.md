# Beispielrechnungen zum Ausprobieren

Alle Firmen, Personen, Anschriften und Kontodaten in diesen PDFs sind **erfunden**. Die Dateien dienen nur dazu, das Tool zu testen. Sie wurden mit den Testklassen (`SampleInvoices`, `SampleVariants`) erzeugt und enthalten eingebettete Schriften, sind also für ZUGFeRD geeignet.

Einfach auf das Fenster des Tools ziehen. Die Layouts unterscheiden sich bewusst:

| Datei | Besonderheit |
|---|---|
| `beispiel-klassisch` | DIN 5008, Infoblock rechts, 19 % |
| `beispiel-modern` | Steuernummer in der Fußzeile |
| `beispiel-dienstleister` | zwei Steuersätze, zwei Seiten |
| `beispiel-01` … `beispiel-10` | ISO-Datum und Präfix, Kurzdatum und Bruttopreise, Adresse rechts, **Gutschrift**, **Rabattzeile**, viele Positionen auf zwei Seiten, ohne Leistungsdatum, nur 7 %, Menge vor Bezeichnung |

Beim ersten Ablegen ist der Aussteller unbekannt: Das Tool schlägt einen neuen Mandanten vor. Zum Test des Summenabgleichs und der Duplikat-Erkennung dieselbe Datei zweimal ablegen.
