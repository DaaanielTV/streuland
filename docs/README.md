# Streuland Dokumentation

Diese Dokumentation ist in Themenbereiche gegliedert, damit sowohl Einsteiger als auch erfahrene Maintainer schnell den richtigen Einstieg finden.

## Struktur

- `docs/architecture/`
  - Technische Gesamtarchitektur
  - Datenfluss beim Plot-Erstellen/Claimen
  - Begründung zentraler Designentscheidungen
- `docs/api/`
  - Klassen- und Methodenüberblick für die internen Java-Komponenten
  - Verantwortlichkeiten und erwartete Invarianten
- `docs/examples/`
  - Konfigurations- und Nutzungsbeispiele
- `docs/maintenance/`
  - Wartungsleitfäden und Refactoring-Notizen

## Schnellstart für Einsteiger

1. Lies `docs/architecture/system-overview.md`, um das große Bild zu verstehen.
2. Lies `docs/api/core-components.md`, um die Verantwortlichkeiten je Klasse zu sehen.
3. Lies `docs/examples/command-flows.md`, um typische Spieler-Workflows nachzuvollziehen.

## Wartungshinweis

Bei Änderungen an der Plot-Logik sollten **immer** gleichzeitig folgende Punkte überprüft werden:

- Synchronität zwischen Storage-Cache und `SpatialGrid`
- Korrekte Besitz-/Trust-Prüfungen in Commands und Listenern
- Thread-Grenzen zwischen Async-Berechnung und Bukkit-Hauptthread
