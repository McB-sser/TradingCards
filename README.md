# TradingCards

Minecraft-Plugin auf Maven-Basis, das Bilddateien aus einem Motiv-Ordner in Minecraft-Karten umwandelt.

## Funktionen

- Admins legen `.png`, `.jpg` oder `.jpeg` Dateien in den Plugin-Ordner `plugins/TradingCards/motifs`.
- Das Plugin laedt diese Dateien als Motive.
- Das Plugin laedt optional gleichnamige `.json`-Dateien mit Karten-Metadaten und schreibt diese in die Item-Lore.
- Mit einem Befehl kann einem Spieler eine einzelne TradingCard-Map gegeben werden.
- Die Hand-Map zeigt die Metadaten, das platzierte 1x2-Wanddisplay zeigt das eigentliche Motivbild.
- Ein lokales HTML/JS-Tool erzeugt passende 1x2-Motivposter und speichert TradingCard-Metadaten als JSON.

## Befehle

- `/tradingcards list`
- `/tradingcards reload`
- `/tradingcards give <spieler> <motiv>`

## Build

Maven-JAR bauen:

```powershell
D:\Maven\apache-maven-3.9.9\bin\mvn.cmd clean package
```

Die fertige Datei liegt anschliessend unter `target/`.

## Generator-Tool

Das Web-Tool liegt unter `tools/map-generator/index.html`.

Es erzeugt:

- `motif-id.png` als 1x2-Motivposter im Format `128x256`
- `motif-id.json` mit Karten-Metadaten
- `manifest.json` mit allen exportierten Karten

## Hinweis

Die aktuelle Plugin-Version gibt eine Metadaten-Map aus. Beim Platzieren an einer Wand entstehen daraus zwei unsichtbar gerahmte Maps als gemeinsames 1x2-Motivdisplay.
