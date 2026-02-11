# Refactoring-Notizen (2026-02)

## Entfernt / vereinfacht

1. **Redundanter Owner-Index in `PlotStorage` entfernt**
   - War ohne konsistente Pflege potenziell fehleranfällig.
   - Kein aktueller produktiver Zugriffspfad nutzte diese Struktur.

2. **Nicht verwendete Felder/Imports entfernt**
   - In mehreren Klassen technischer Ballast entfernt.

3. **Claiming-Konsistenz verbessert**
   - Claim-Logik auf zentralen Managerpfad ausgerichtet.
   - Synchronisierung zwischen Storage und SpatialGrid sichergestellt.

4. **NPE-Risiken reduziert**
   - Null-sichere Owner-Vergleiche in Plotabfragen und Owner-Checks.

5. **Konfigurationsnutzung verbessert**
   - `path.width` wirkt nun in der tatsächlichen Pfaderweiterung.

## Nicht geändert (bewusst)

- Grundlegendes Berechtigungsmodell (Owner/Trusted/Visitor)
- Grundarchitektur (Storage + Manager + Grid + Listener + Command)
- Einfache Pfadstrategie (Bresenham) ohne komplexe Terrainnavigation
