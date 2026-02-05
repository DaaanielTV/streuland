# Architekturüberblick

## 1) Ziel des Plugins

Streuland verwaltet Grundstücke (Plots) in einer bestehenden Vanilla-Welt. Es soll:

- Plot-Erstellung und Claiming ermöglichen,
- Baurechte durchsetzen,
- persistente Speicherung bereitstellen,
- sowie Wege zwischen Plots erzeugen.

## 2) Bausteine und Zuständigkeiten

### `StreulandPlugin`
Lifecycle-Orchestrierung (`onEnable`/`onDisable`), Initialisierung der Hauptkomponenten und Command-Registrierung.

### `PlotStorage`
Persistenzschicht auf YAML-Basis:

- Laden aller Plots beim Start,
- Speichern einzelner Plots,
- Claim-Transition `UNCLAIMED -> CLAIMED`.

### `SpatialGrid`
Räumlicher Index zur schnellen Plot-Suche per Weltkoordinate. Verhindert lineare Vollsuchen bei Standardabfragen.

### `PlotManager`
Domänenlogik rund um Plot-Erzeugung und Validierung:

- zufällige Standortsuche,
- Terrain-Validierung,
- Distanz-/Überlappungschecks,
- konsistente Aktualisierung von Storage + SpatialGrid.

### `PathGenerator`
Erzeugt und baut Wege zwischen neuem Plot und nächstem Zielplot.

### `ProtectionListener`
Erzwingt Bauregeln in Plots, auf Wegen und in Wildnis.

### `PlotCommandExecutor`
Nutzeroberfläche (Befehle) für Spieler-Workflows.

## 3) Wichtige Invarianten

1. **Claiming-Invariante:** Ein geclaimter Plot muss in Storage und SpatialGrid denselben Zustand widerspiegeln.
2. **Ownership-Invariante:** Trust/Untrust ist nur durch den Besitzer erlaubt.
3. **Thread-Invariante:** Weltveränderungen (Block-Setzen) müssen im Bukkit-Mainthread passieren.

## 4) Datenfluss: `/plot claim`

1. Command bestimmt Plot per Spielerposition über `PlotManager.getPlotAt`.
2. Ownership-/Statusprüfung auf `UNCLAIMED`.
3. Claim wird über `PlotManager.claimPlotForPlayer(...)` durchgeführt.
4. Manager ruft `PlotStorage.claimPlot(...)` auf.
5. Manager synchronisiert den SpatialGrid-Eintrag (altes Plotobjekt entfernen, neues einfügen).
6. Wege werden generiert und auf Mainthread gebaut.

## 5) Designentscheidungen

- **YAML pro Plot-Datei:** Einfach debuggbar, adminfreundlich, ohne DB-Abhängigkeit.
- **Separater Spatial-Index:** Vermeidet lineare Suche bei häufigen Positionsabfragen.
- **Zustandsmodell über `PlotState`:** Klar trennbar zwischen unbeansprucht/beansprucht.
- **Asynchrone Standortsuche:** Reduziert Lag bei Berechnungen.

## 6) Grenzen / bekannte Trade-offs

- Pfadlogik nutzt bewusst eine einfache lineare Strategie (Bresenham), keine komplexe Navigation.
- Keine Versionierung/Migration alter Plotdateien über Schema-Versionen.
- Kein eigenes Locking-Konzept für konkurrierende Claims; erwartetes Lastprofil ist moderat.
