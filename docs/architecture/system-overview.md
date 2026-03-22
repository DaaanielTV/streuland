# Systemüberblick

## Architektur in Schichten

1. **Einstieg/Bootstrap**
   - `StreulandPlugin` initialisiert Konfiguration, Services und Listener.

2. **Anwendungslogik**
   - Plot-Management (`PlotManager`, `PlotStorage`, `SpatialGrid`)
   - Erweiterungen (Markt, Freigabe, Upgrades, Biome, Backups)

3. **Schnittstellen**
   - Commands (`/plot`, `/district`, Unterbefehle)
   - Web/Dashboard (`WebServer`, REST, WebSocket)
   - Event-Listener für Schutz- und Gameplay-Regeln

4. **Persistenz/Dateien**
   - YAML-Konfigurationen
   - Plot- und Feature-Daten in Plugin-Verzeichnissen
   - Optionale SQLite-Nutzung für bestimmte Dienste

## Nicht-funktionale Ziele

- Vorhersagbare Laufzeit im Server-Tick
- Robuste Fehlerbehandlung bei I/O
- Gute Wartbarkeit durch modulare Services

## Integrationen

- Paper API (Server-Events/Commands)
- Vault API (Ökonomie)
- Java-WebSocket (Dashboard-Liveupdates)
- Gson/SnakeYAML für Datenverarbeitung

## Qualitätsmaßnahmen

- Unit-/Integrationstests für Kernpfade
- Build-Validierung über Maven
- Doku-Pflege parallel zu Feature-Änderungen
