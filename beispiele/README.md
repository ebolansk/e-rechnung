# Beispielrechnungen zum Ausprobieren

Zwölf **erfundene** PDF-Rechnungen von nur **zwei Ausstellern** (Mandanten). Firmen, Personen, Anschriften, Steuer- und Kontodaten sind frei erfunden. Es sind ganz normale Rechnungs-PDFs, **keine E-Rechnungen**: Aus ihnen erzeugt das Tool erst XRechnung oder ZUGFeRD. Alle enthalten eingebettete Schriften, sind also für ZUGFeRD geeignet. Erzeugt von `ExampleInvoicesTest`.

| Aussteller | Ansprechpartner | Telefon | Dateien |
|---|---|---|---|
| Nordlicht Werbetechnik GmbH (Stuttgart) | Erika Beispiel | +49 711 5550123 | `…-nordlicht.pdf` (6 Stück) |
| Studio Feldweg UG (Tübingen) | Mara Feld | 07071 998877 | `…-feldweg.pdf` (6 Stück) |

Der Ansprechpartner steht auf jeder Rechnung im Briefkopf. Beim Anlegen des Mandanten tragen Sie ihn im Dialog ein (bei XRechnung ist er Pflicht, bei ZUGFeRD optional).

Die Layouts unterscheiden sich bewusst: klassisch nach DIN 5008, modern, ISO-Datum, Kurzdatum und Bruttopreise, Adresse rechts, **Gutschrift**, **Rabattzeile**, viele Positionen auf zwei Seiten, ohne Leistungsdatum und Referenz, nur 7 % MwSt, Menge vor Bezeichnung.

Ziehen Sie eine oder mehrere Dateien auf das Fenster. Die erste Rechnung eines Ausstellers legt einen Mandanten an (der Vorschlag kommt aus der Rechnung, Ansprechpartner und Telefon ergänzen Sie); jede weitere Rechnung desselben Ausstellers wird wiedererkannt. Dieselbe Datei zweimal abzulegen testet die Duplikat-Erkennung.
