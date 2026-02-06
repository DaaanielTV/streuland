# Streuland – Plot-Erweiterungen (Biome, Upgrades, Distribution, Architektur)

## Zielbild
Diese Datei beschreibt eine modulare Erweiterung der bestehenden Streuland-Plot-Logik mit Fokus auf
Biome, Progression, intelligente Verteilung, Skalierung, Events und Analytics – ohne das bestehende
Stadtentwicklungssystem zu ersetzen.

---

## 1) Biome-abhängiges Plot-System

### Datenmodell (Java)
- `BiomeRuleSet`: Regeln, Multiplikatoren, Constraints.
- `BiomeEffect`: einzelner, stackbarer Effekt (aktivierbar, priorisiert).
- `BiomeEffectStack`: kombiniert Effekte deterministisch.
- `BiomeProfile`: Effekte pro Biome, inkl. Stack-IDs.
- `BiomeConfigLoader`: lädt YAML-Konfiguration in Profile.

```java
BiomeConfigLoader loader = new BiomeConfigLoader(plugin.getConfig());
Map<String, BiomeProfile> profiles = loader.loadProfiles();
BiomeProfile profile = profiles.get("PLAINS");
BiomeRuleSet rules = profile.getCombinedRules();
```

### Event-Hooks
- `PlotBiomeDetectedEvent`: Biome-Erkennung/Refresh.
- `PlotBiomeEffectAppliedEvent`: Effekt-Stack angewandt.

### Beispiel-Config
Siehe `config.yml`-Abschnitt `biomes`.

### Balancing-Strategie
- Multiplikatoren konservativ wählen (0.8–1.3), harte Caps für „max-*“-Constraints.
- Prioritäten nutzen: Basiseffekt (0), saisonale/temporäre Effekte (100+).
- Negative Effekte sind erlaubt, aber auf 1–2 pro Biome begrenzen.

### Performance-Hinweise
- Biome-Erkennung nur beim Claim/Upgrade/Chunk-Load, nicht pro Tick.
- Cache pro Plot, invalidieren bei Weltgen-Änderungen (Chunk-Regeneration).

---

## 2) Plot-Upgrades & Progression

### Upgrade-Baum
Ein gerichteter Graph von `PlotUpgradeDefinition`-Einträgen:
- Level-1-2, Level-2-3, Spezialisierungen (z. B. „Industrial Track“).

### Command-Design
- `/plot upgrade` → verfügbare Upgrades anzeigen
- `/plot upgrade <id>` → Upgrade kaufen/anwenden
- `/plot level` → aktuelles Level, Slots, Cooldowns

### Datenhaltung
Empfohlen:
- `plot_progression` (SQL) mit `plot_id`, `level`, `last_upgrade_at`, `unlock_flags`
- YAML-Fallback für Single-Server.

### Beispiel-Code
```java
Optional<PlotProgressionState> state = upgradeService.getState(plotId);
if (upgradeService.canUpgrade(plotId, playerId, "level-1-2")) {
    upgradeService.applyUpgrade(plotId, playerId, "level-1-2");
}
```

### Erweiterungsstrategie
- Neue Upgrades als Konfig-Eintrag (kein Core-Patch).
- Addons liefern eigene Upgrade-Definitionen via API.

---

## 3) Intelligente Plot-Verteilung

### Algorithmus-Vergleich
- **Naiv zufällig:** schnell, aber schlechte Qualität.
- **Weighted Scoring (empfohlen):** bewertet Terrain, Biome, Infrastruktur.
- **Heatmap-basiert:** gut für Netzwerk-Skalierung, aber teuer.

### Pseudocode
```
for candidate in sample(radius, batch):
  metrics = scan(candidate)
  score = wTerrain*terrain + wBiome*biome + wInfra*infra + wDensity*density
choose max(score)
```

### Java-Beispiel
```java
WeightedPlotDistributor distributor = new WeightedPlotDistributor(scorer);
CompletableFuture<Optional<CandidatePlotLocation>> best =
    distributor.selectBestAsync(candidates);
```

### Fallback-Strategien
- Wenn kein Kandidat die Mindestschwelle erreicht: Radius erhöhen.
- Nach N Fehlschlägen: unclaimed pool oder manuelle Admin-Queue.

### Performance-Analyse
- `async-batch-size` limitiert Samples pro Tick.
- Vorberechnete „terrain variance“-Maps pro Chunk.
- Optional Debug-Heatmap (nur Admin & async).

---

## 4) Architektur & Skalierung

### Paketstruktur (vorgeschlagen)
```
de.streuland.core
de.streuland.plot
de.streuland.plot.biome
de.streuland.plot.upgrade
de.streuland.plot.distribution
de.streuland.storage
de.streuland.api
de.streuland.analytics
```

### Storage-Abstraktion
- `PlotRepository` als Interface.
- Implementierungen: YAML → SQLite/MySQL.
- Migrations-Tool (CLI): YAML → SQL (batch, idempotent).

### Threading-Modell
- **Main-Thread:** Block-Änderungen, Event Dispatch.
- **Async:** Suche/Scoring, Analytics, IO.
- **Scheduler:** Throttling und Rate-Limits für Upgrades.

### Best-Practices
- Kein Blocking im Main-Thread.
- Events für Erweiterungen statt Core-Änderungen.
- Konfig-Driven statt Hardcodes.

---

## 5) Events & Community

### Event-Lifecycle
1. Registrierung (Admin)
2. Teilnahmephase
3. Bewertung (manuell/automatisch)
4. Belohnung/Archiv

### Beispiel-Events
- Bau-Wettbewerb „Frühling“
- Saisonale Themen (z. B. Winter-Deko)

### Command-Flows
- `/plot event create <name>`
- `/plot event join <name>`
- `/plot event rate <plot>`
- `/plot event close <name>`

### Abuse-Prevention
- Cooldowns und Teilnahme-Limits
- Bewertung nur einmal pro Spieler
- Anti-Alt-Checks via IP/Account-age

---

## 6) Analytics & Monitoring

### Datenpunkte
- Plot-Claim, Upgrade, Besuch
- Build-Activity pro Stunde
- Chunk-Load-Zeiten

### Erfassungsstrategie
- Event-basierte Records (`PlotAnalyticsRecord`)
- Batch-Writer (async)

### Speicherlösung
- SQLite/MySQL für Netzwerk-Mode
- JSON-Export für Dashboard

### Visualisierungsideen
- Heatmaps der Plot-Nutzung
- Upgrade-Funnel pro Woche

### Datenschutz
- Pseudonymisierung von Player-IDs
- Konfigurierbares Retention-Policy

---

## 7) Architekturdiagramm (textuell)
```
[Core]
  |-- [PlotManager]
  |-- [BiomeService] ----> [BiomeConfigLoader] -> [BiomeProfile]
  |-- [UpgradeService] --> [PlotUpgradeTree] -> [PlotLevelDefinition]
  |-- [Distributor] -----> [WeightedPlotDistributor] -> [Scorers]
  |-- [Analytics] --------> [PlotAnalyticsService]
  |
  '-- [Storage] ----------> [PlotRepository] -> (YAML|SQLite|MySQL)
```

---

## 8) Konkrete Implementierungsschritte
1. Konfig-Schema erweitern (`config.yml`).
2. Biome-Profile & Effekte laden, Plot-Cache einführen.
3. Upgrade-Tree aus Config laden, Vault/Item-Checks kapseln.
4. Weighted Distribution implementieren + Debug-Heatmap.
5. Storage-Abstraktion und Migrationstool ergänzen.
6. Analytics-Batch-Writer + optionale JSON-Exports.
7. API + Events für Addons stabilisieren.
