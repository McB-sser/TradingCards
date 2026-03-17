# TradingCards

Minecraft-Plugin auf Maven-Basis, das TradingCards aus Bildern und JSON-Metadaten erzeugt und als Map-Items bzw. Wandanzeigen in Minecraft nutzbar macht.

## Funktionen

- Admins legen `.png`, `.jpg` oder `.jpeg` Dateien in `plugins/TradingCards/motifs`.
- Optional kann zu jedem Motiv eine gleichnamige `.json`-Datei mit Karten-Metadaten abgelegt werden.
- Das Plugin laedt Motive und Metadaten neu per Command und gibt Karten per Befehl aus.
- Jede Handkarte ist eine eigene Map mit Kartenlayout, Lore, festen Zufallswerten und gespeicherten Stats.
- Die Werte einer Karte werden genau einmal erzeugt und bleiben beim Platzieren, Abbauen, Neustart und erneuten Aufheben erhalten.
- Bereits platzierte Frames und Karten im Inventar werden nach Neustarts, Join, Chunk-Load und Inventar-Aktionen wieder korrekt gebunden.

## Platzierung

- `Rechtsklick`: platziert eine `1x3`-Anzeige mit Motiv oben und verdeckter Wertekarte unten.
- `Schleichend + Rechtsklick`: platziert ein `1x2`-Motivbild ohne Wertekarte.
- `Schleichend + Linksklick`: platziert nur die Wertekarte als `1x1`.
- Die Wandanzeigen werden mit unsichtbaren Item Frames erzeugt und verhalten sich wie ein gemeinsames Bild.
- Beim Abbauen wird das komplette Display entfernt und wieder zu genau einer Handkarte.

## Verdeckte Wertekarte

- Die Wertekarte startet aufgehaengt verdeckt.
- In der `1x3`-Anzeige sieht man unten zuerst nur einen Sockel.
- Bei der einzelnen `1x1`-Wertekarte sieht man zuerst ein Fragezeichen.
- Mit `Rechtsklick` auf die Wertekarte wird sie aufgedeckt.
- Mit weiterem `Rechtsklick` wird sie wieder verdeckt.

## Karteninhalt

- Die Handkarte zeigt Titel, Beschreibung, Nummer, Serie, Seltenheit und berechneten Kartenwert.
- Die vier Stats sind `Leben`, `Hunger`, `Ruestung` und `Kraft`.
- Alle Werte werden zusaetzlich in der Lore der Karte gespeichert.
- Seltenheit und Multiplikator beeinflussen den angezeigten Kartenwert.

## Befehle

- `/tradingcards list`
- `/tradingcards reload`
- `/tradingcards give <spieler> <motiv>`

## Generator-Tool

Das Web-Tool liegt unter `tools/map-generator/index.html`.

Es erzeugt:

- `motif-id.png` als Motivdatei fuer das Poster
- `motif-id.json` mit Karten-Metadaten
- `manifest.json` mit allen exportierten Karten
