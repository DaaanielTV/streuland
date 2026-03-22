# Streuland Plot Plugin

Streuland ist ein Plot-System für Paper 1.16.5 mit Vanilla-Weltgenerierung. Das Plugin trennt Wege, freie Plots und beanspruchte Plots explizit über Area-Typen.

## Funktionen

- Schutzlogik auf Basis von `AreaType` (`PATH`, `PLOT_UNCLAIMED`, `PLOT_CLAIMED`)
- Automatische Wegerzeugung zwischen Plots
- Persistenz per YAML-Dateien
- Asynchrone Plot-Erstellung zur Lastreduktion
- Erweiterungen für Markt, Freigabeprozess, Biome, Dashboard und Backups

## Schnellstart

### 1) Bauen

```bash
mvn clean package
```

### 2) Installieren

Kopiere `target/Streuland-1.0.0-SNAPSHOT.jar` in den Ordner `plugins/` deines Servers.

### 3) Konfigurieren

Bearbeite `plugins/Streuland/config.yml` (Weltname, Plot-Größe, Suchradius, Limits).

### 4) Server neu starten

```text
/restart
```

## Wichtige Befehle

| Befehl | Beschreibung |
|---|---|
| `/plot create` | Neuen Plot erzeugen |
| `/plot info` | Plot-Informationen anzeigen |
| `/plot trust <spieler>` | Bau-Rechte vergeben |
| `/plot untrust <spieler>` | Bau-Rechte entziehen |
| `/plot home` | Zum Plot teleportieren |
| `/plot list` | Eigene Plots auflisten |
| `/plot unclaim [plotId]` | Plot freigeben |
| `/plot delete [plotId]` | Plot löschen (mit Bestätigung) |
| `/plot confirm` / `/plot cancel` | Löschvorgang bestätigen/abbrechen |

## Build- und Entwicklungsstatus

- Die Maven-Konfiguration wurde bereinigt (kaputte XML-/Dependency-Definitionen entfernt).
- In Umgebungen ohne Zugriff auf Maven Central kann der Build trotz korrekter `pom.xml` fehlschlagen, weil Plugins/Artefakte nicht heruntergeladen werden können.

## Dokumentation

Weitere Dokumente findest du in `docs/`:

- [Dokumentationsübersicht](docs/README.md)
- [Architekturüberblick](docs/architecture/system-overview.md)
- [Code-Walkthrough](docs/architecture/code-walkthrough.md)
- [API-Kernkomponenten](docs/api/core-components.md)
- [Befehlsabläufe](docs/examples/command-flows.md)

## Mitwirken

Bitte lies [CONTRIBUTING.md](CONTRIBUTING.md) für den Ablauf über Issue + Pull Request.

## Lizenz

GNU GPLv3, siehe [LICENSE](LICENSE).
