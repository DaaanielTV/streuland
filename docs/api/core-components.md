# API-Referenz (interne Komponenten)

## `de.streuland.plot.Plot`

### Zweck
Unveränderliche Kernmetadaten eines Plots (Koordinaten, Owner, Zustand), plus veränderbare Trust-Liste.

### Wichtige Methoden
- `contains(int x, int z)`: Bereichsprüfung.
- `isAllowed(UUID player)`: Berechtigungsprüfung basierend auf Zustand + Owner/Trust.

### Randfälle
- `owner == null` ist nur bei `UNCLAIMED` sinnvoll.

## `de.streuland.plot.PlotStorage`

### Zweck
Persistenz und In-Memory-Cache.

### Wichtige Methoden
- `savePlot(Plot plot)`
- `getPlayerPlots(UUID player)`
- `claimPlot(String plotId, UUID player)`

### Vertragsdetails
- `claimPlot(...)` gibt den aktuellen Plotzustand zurück.
- YAML-Datei und Cache werden gemeinsam aktualisiert.

## `de.streuland.plot.SpatialGrid`

### Zweck
Schnelle Positionsabfragen.

### Schlüsselmethoden
- `getPlotAt(x,z)`
- `isLocationAvailable(centerX, centerZ, plotSize)`
- `addPlot/removePlot/rebuild`

## `de.streuland.plot.PlotManager`

### Zweck
Geschäftslogik und Orchestrierung.

### Schlüsselmethoden
- `createPlotAsync(UUID)`
- `claimPlotForPlayer(Plot, UUID)`
- `getPlotAt(x,z)`
- `generateUnclaimedPlots(...)`

### Threading
- Rechenlogik kann async laufen.
- Weltveränderungen immer auf Mainthread.

## `de.streuland.path.PathGenerator`

### Zweck
Wegpunkte erzeugen und Wege in der Welt bauen.

### Schlüsselmethoden
- `generatePath(Plot newPlot)`
- `buildPathBlocks(List<BlockPosition>)`

### Hinweis
Die effektive Flächenbreite hängt direkt von `path.width` ab.

## `de.streuland.listener.ProtectionListener`

### Zweck
Durchsetzung der Bau-/Interaktionsregeln.

### Events
- `BlockBreakEvent`
- `BlockPlaceEvent`
- `PlayerInteractEvent`
