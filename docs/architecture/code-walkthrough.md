# Ausführlicher Code-Walkthrough (einsteigerfreundlich)

Dieses Dokument erklärt die Kernlogik entlang von Codeabschnitten sehr detailliert. Es fokussiert die wichtigsten Ausführungspfade und typische Fehlerquellen.

---

## Abschnitt A: Plugin-Start (`StreulandPlugin.onEnable`)

### Was passiert logisch?

1. Konfiguration wird geladen (`saveDefaultConfig`).
2. Persistenz (`PlotStorage`) wird initialisiert.
3. Domänenlogik (`PlotManager`) wird auf Storage aufgebaut.
4. Pfadgenerator und Listener werden danach erstellt.
5. Command-Executor wird registriert.
6. Fehler führen zu sauberem Deaktivieren des Plugins.

### Warum ist die Reihenfolge wichtig?

- `PlotManager` benötigt ein bereits initialisiertes Storage.
- `PathGenerator` benötigt einen gültigen PlotManager.
- `ProtectionListener` darf erst aktiv sein, wenn Plotdaten ladbar sind.

### Randfälle

- Weltname aus Konfiguration existiert nicht → `IllegalStateException` im Manager.
- Command in `plugin.yml` fehlt → frühes, klares Fail-fast mit Fehlertext.

---

## Abschnitt B: Claiming-Synchronisation (`PlotManager.claimPlotForPlayer`)

### Problemstellung

Beim Claiming wurde vorher nur das Storage aktualisiert. Der SpatialGrid-Eintrag konnte dabei auf einem alten Plotobjekt bleiben. Folge:

- Positionsabfragen lieferten teilweise den alten `UNCLAIMED`-Status,
- obwohl die Datei und der Cache schon auf `CLAIMED` standen.

### Neue Logik

1. Storage claimt den Plot und liefert das aktuelle Plotobjekt zurück.
2. Falls sich die Objektinstanz geändert hat, wird im Grid synchronisiert:
   - altes Objekt entfernen,
   - neues Objekt einfügen.

### Ergebnis

Storage, Grid und Schutzlogik sehen denselben Zustand.

---

## Abschnitt C: Persistenz (`PlotStorage`)

### Wichtige Detailpunkte

- Null-sichere Owner-Prüfung in `getPlayerPlots` (`player.equals(plot.getOwner())`), um NPEs bei `UNCLAIMED`-Plots zu vermeiden.
- Entfernen einer ungenutzten Owner-Indexstruktur, die keine echte Leistungsoptimierung brachte und inkonsistent gepflegt wurde.
- `claimPlot(...)` liefert nun den aktuellen Plot zurück, sodass aufrufende Schichten korrekt weiterarbeiten können.

### Warum das robust ist

- Die Datenquelle bleibt klar: `cachedPlots` ist authoritative In-Memory-Repräsentation.
- Keine redundanten Indizes ohne konsistente Invalidierung.

---

## Abschnitt D: Pfadgenerierung (`PathGenerator`)

### Bisherige Schwächen

- Konfigurierbare Breite wurde gelesen, aber praktisch ignoriert (harte 5x5-Ausdehnung).
- Mehrere Felder und Methoden waren nicht verwendet.

### Jetzt

- Die konfigurierte Breite steuert die Flächenausdehnung entlang der Bresenham-Linie.
- Unnötige Felder/Methoden wurden entfernt.
- Materialwahl nutzt definierte `pathStoneBlocks` konsistent.

### Designentscheidung

Bewusst einfache Pfadlogik statt komplexer Pathfinder:

- robust,
- gut verständlich,
- wenig Fehlerfläche.

---

## Abschnitt E: Command-Workflows

### `/plot claim`

- Prüft Spielerposition, Plotstatus und Plotlimit.
- Nutzt jetzt den zentralen Claim-Pfad des Managers (inkl. Grid-Sync).
- Erzeugt danach den Weg.

### `/plot create`

- Asynchrone Suche/Erstellung bleibt erhalten.
- Bei Wiederverwendung unbeanspruchter Plots greift ebenfalls die Claim-Synchronisation.

### `/plot trust` und `/plot untrust`

- Plotbesitz wird geprüft.
- Manager prüft zusätzlich null-sicher, damit keine NPE bei defekten Datenzuständen auftritt.

---

## Abschnitt F: Schutzlogik (`ProtectionListener`)

### Was wurde gewartet?

- Unbenutzte Felder/Imports entfernt.
- Verhalten unverändert gehalten: Pfadschutz, Plotschutz, Wilderness-Verbot.

### Warum nur minimale Änderungen?

Die Schutzregeln waren fachlich konsistent; daher wurde nur technischer Ballast entfernt (kein Feature-Umbau).

---

## Abschnitt G: Clean-Code-/Wartbarkeitsregeln, die angewandt wurden

1. Redundante Datenstrukturen entfernt.
2. Null-sichere Vergleiche bei optionalen Feldern (`owner`).
3. Domänenkonsistenz über zentrale Methode statt duplizierter Claim-Logik.
4. Fail-fast bei Plugin-Fehlkonfiguration.
5. Entfernen toter Felder/Imports für bessere Lesbarkeit.

---

## Abschnitt H: Einsteiger-Checkliste für künftige Änderungen

Wenn du eine Methode anpasst, prüfe immer:

- Ändert sich nur Storage oder auch SpatialGrid?
- Läuft Code im richtigen Thread (Bukkit-API beachten)?
- Gibt es `owner == null`-Pfade?
- Spiegelt die Doku (`docs/`) den neuen Ablauf korrekt wider?
