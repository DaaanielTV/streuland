# API- und Kernkomponenten

## Plugin-Einstieg

### `StreulandPlugin`
- Bootstrap der Services
- Laden der Konfiguration
- Registrierung von Commands und Listenern

## Plot-Kern

### `PlotManager`
- Zentrale Fachlogik für Erstellen, Beanspruchen, Freigeben und Prüfen von Plots

### `PlotStorage`
- Persistenz der Plotdaten (Datei-basiert/YAML)

### `SpatialGrid`
- Räumlicher Index für schnelle Plot-Lookups

## Schutz und Berechtigungen

### `ProtectionListener`
- Event-basierte Block-/Interaktionsprüfungen

### `Role` / `Permission`
- Rollen- und Rechteauswertung pro Plot

## Zusätzliche Domänen

- Markt: `MarketManager`, `PlotMarketService`
- Freigaben: `PlotApprovalService`
- Backups/Snapshots: `SnapshotService`, `SnapshotManager`
- Biome/Upgrades: `BiomeBonusService`, `PlotUpgradeService`
- Dashboard/Web: `RestApiController`, `DashboardWebSocketServer`, `WebServer`

## Grundprinzipien

1. Fachlogik getrennt von Transport (Command/Web/API)
2. Persistenz hinter Services kapseln
3. Schutzregeln früh und zentral validieren
4. Seiteneffekte (I/O, Netzwerk) möglichst isoliert testen
