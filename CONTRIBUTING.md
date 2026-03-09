# Beitragen zu Streuland

Danke für deinen Beitrag! Bitte nutze den folgenden Ablauf über **Issue + Pull Request**.

## 1) Issue erstellen

Lege vor größeren Änderungen ein Issue an mit:

- Problemstellung oder Feature-Wunsch
- Vorschlag für die Umsetzung
- Risiken/Migrationsbedarf
- Grobe Teststrategie

Kleine Tippfehler- oder Doku-Fixes können direkt per PR eingereicht werden.

## 2) Branch erstellen

Namensvorschlag:

- `feat/<kurzbeschreibung>`
- `fix/<kurzbeschreibung>`
- `docs/<kurzbeschreibung>`

## 3) Lokal prüfen

```bash
mvn clean verify
```

Wenn die Umgebung keinen Zugriff auf externe Maven-Repositories hat, dokumentiere das transparent im PR.

## 4) Pull Request erstellen

Bitte im PR enthalten:

- Kurzbeschreibung der Änderung
- Verknüpftes Issue (`Fixes #...` oder `Refs #...`)
- Testnachweis (ausgeführte Befehle + Ergebnis)
- Hinweise auf Breaking Changes oder Konfigurationsänderungen

## 5) Review-Richtlinien

- Kleine, nachvollziehbare Commits
- Keine unnötigen Formatierungsänderungen
- Dokumentation bei Feature- oder API-Änderungen aktualisieren

## 6) Merge-Kriterien

- Code-Review abgeschlossen
- Relevante Tests erfolgreich
- Doku aktualisiert
- Offene TODOs im PR beschrieben oder in Folge-Issues ausgelagert
