# TradingCards Map Generator

Lokales HTML/JavaScript-Tool fuer 1x2 TradingCard-Motivposter.

## Ausgabe

Pro Karte werden folgende Dateien erzeugt:

- `card_id.png` als hohes Motivposter im Format `128x256`
- `card_id.json` mit TradingCard-Metadaten
- `manifest.json` mit allen exportierten Karten

## Nutzung

1. `tools/map-generator/index.html` in einem Chromium-basierten Browser oeffnen.
2. Bilder laden.
3. Motiv mit Zoom und X/Y-Verschiebung ausrichten und Metadaten pflegen.
4. Ausgabeordner waehlen.
5. Speichern.

## Hinweise

- Das 1x2-Panel dient als Vorschau fuer zwei vertikal gestapelte Minecraft-Maps.
- Ingame ist die Hand-Map fuer Metadaten gedacht, waehrend das Wanddisplay das PNG-Motiv zeigt.
- Direkter Ordner-Export nutzt die File System Access API und funktioniert am besten in Chrome oder Edge.
