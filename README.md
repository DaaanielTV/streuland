# Streuland Plot Plugin

Streuland Plot Plugin is an open-source Paper plugin for managing player plots in a vanilla-style Minecraft world. It provides protected plots, path generation, district progression, environment controls, a marketplace, approvals, and an optional web dashboard.

## Project Overview

This repository contains the full Java source, tests, and documentation for the Streuland plugin (`de.streuland`). It is built with Maven and targets Paper `1.16.5`.

## Features / Purpose

- Plot creation, ownership, trust/untrust, deletion workflows
- Area-based protection model (`PATH`, `PLOT_UNCLAIMED`, `PLOT_CLAIMED`)
- Automatic path generation and world-area partitioning
- District progression and neighborhood systems
- Plot upgrades, market listings, and approval workflows
- YAML + SQLite-backed storage components
- Optional REST/WebSocket dashboard assets

## Installation

### Prerequisites

- Java 8+
- Maven 3.8+
- A Paper server compatible with `1.16.5`

### Build from Source

```bash
mvn clean package
```

The plugin jar will be generated in `target/`.

### Deploy to Paper

1. Copy the generated jar into your server `plugins/` directory.
2. Start or restart the server.
3. Edit generated plugin config files under `plugins/Streuland/` as needed.

## Usage

Common commands:

- `/plot create` – create a new plot
- `/plot info` – inspect current plot data
- `/plot trust <player>` and `/plot untrust <player>` – manage collaborators
- `/plot list` – list owned plots
- `/plot home` – teleport to plot
- `/district ...` – district management operations
- `/plotapprove ...` – review approval requests
- `/streuland ...` – diagnostics and maintenance commands

See additional command-flow documentation in `docs/examples/command-flows.md`.

## Development Setup

```bash
git clone <your-fork-or-repo-url>
cd streuland
mvn clean verify
```

Recommended:

- Use an IDE with Maven import enabled.
- Keep all generated build output local (`target/` is intentionally ignored).

## Configuration

Primary configuration files are packaged from `src/main/resources/` and generated into the plugin data folder on first run:

- `config.yml`
- `world_main.yml`
- `world_nether.yml`
- `world_end.yml`
- `plot-upgrades.yml`
- `quests.yml`
- `messages_en.yml` / `messages_de.yml`

## Build / Run Instructions

- Build jar: `mvn clean package`
- Run test suite: `mvn test`
- Run full verification: `mvn clean verify`

No compiled artifacts are stored in version control. Regenerate binaries locally using the Maven commands above.

## Troubleshooting

- **Dependency resolution fails**: ensure network access to Maven repositories listed in `pom.xml`.
- **Plugin not loading**: verify Paper version compatibility and check startup logs for missing soft dependencies (`Vault`, `WorldGuard`).
- **Config issues**: delete invalid generated config files in `plugins/Streuland/` and restart to regenerate defaults.

## Documentation

- `docs/README.md` – documentation index
- `docs/architecture/system-overview.md` – architecture map
- `docs/architecture/code-walkthrough.md` – source orientation
- `docs/api/core-components.md` – module responsibilities
- `docs/examples/command-flows.md` – command examples

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

GNU GPLv3. See [LICENSE](LICENSE).
