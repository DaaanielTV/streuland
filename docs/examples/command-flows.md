# Beispiele: typische Command-Flows

## 1) Neuer Plot (`/plot create`)

1. Spieler führt Command aus.
2. System prüft Plotlimit.
3. System sucht asynchron:
   - erst unbeanspruchte Plots,
   - danach neue gültige Position.
4. Plot wird geclaimt.
5. Weg zum Zielpunkt wird gebaut.

## 2) Plot am Standort claimen (`/plot claim`)

1. Spieler steht in Plotbereich.
2. Zustand muss `UNCLAIMED` sein.
3. Claim erfolgt inklusive Grid-Synchronisierung.
4. Spieler erhält Erfolgsmeldung.

## 3) Teleport (`/plot home`)

- 1 Plot: direkt teleportieren.
- Mehrere Plots: Liste oder Index (`/plot home 2`).

## 4) Trust-Verwaltung

- `/plot trust <Spieler>`
- `/plot untrust <Spieler>`

Voraussetzung: Ausführender Spieler ist Owner des Plots.
