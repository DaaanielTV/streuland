# ⚠️⚠️ Plugin is a Work-In-Progress, go ahead and experiment with it :) ⚠️⚠️

# Streuland Plot Plugin

A **random plot system** for Paper 1.16.5 Minecraft servers with vanilla world generation.

## Features

✅ **Random Plot Distribution** - Plots spawn randomly in the world (not grid-based)
✅ **Automatic Path Generation** - New plots auto-connect via paths to nearest plots
✅ **Vanilla World Preservation** - Uses default world generation, no custom terrain
✅ **Plot Protection** - Only owner/trusted players can build
✅ **Persistent Storage** - YAML-based plot data that survives restarts
✅ **Async Operations** - Plot creation runs async to prevent lag

## Quick Start

### 1. Build
```bash
mvn clean package
```

### 2. Install
Copy `target/Streuland-1.0.0-SNAPSHOT.jar` to your server's `plugins/` folder

### 3. Configure
Edit `plugins/Streuland/config.yml`:
- Set world name
- Configure plot size (default 64x64)
- Adjust search radius for plot placement

### 4. Restart Server
```
/restart
```

Check console for:
```
Streuland enabled successfully!
```

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/plot create` | Create a new plot at random location |
| `/plot info` | Show details about your current plot |
| `/plot trust <player>` | Allow player to build on your plot |
| `/plot untrust <player>` | Remove player from trusted list |
| `/plot home` | Teleport to your plot |
| `/plot list` | List all your plots |
| `/plot ` | Show help message |

## Configuration

**File:** `plugins/Streuland/config.yml`

```yaml
# World to use for plots
world:
  name: "world"

# Plot settings
plot:
  size: 64                    # Size of each plot (blocks)
  min-distance: 100           # Minimum distance between plot centers
  max-search-radius: 5000     # Search radius from spawn

# Path settings
path:
  width: 4                    # Path width in blocks

# Terrain validation
terrain:
  water-rejection: true       # Reject plots in water
  adjacent-water-threshold: 3 # Reject if > 3 water blocks nearby
```

## Architecture

The plugin is modular with clear separation of concerns:

- **PlotManager** - Central orchestrator for plot operations
- **PlotStorage** - YAML persistence layer
- **PathGenerator** - Generates paths using Bresenham algorithm
- **ProtectionListener** - Event-based plot protection
- **PlotCommandExecutor** - Command handler

📖 See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed documentation.

## Plot Creation Process

When a player runs `/plot create`:

1. **Async Validation** (non-blocking)
   - Random location in search radius
   - Terrain check (not in water/lava)
   - Distance validation (no overlaps)

2. **Main Thread Execution** (when validation passes)
   - Plot data created and saved
   - Path generated from new plot to nearest existing plot
   - Path blocks placed (GRAVEL)
   - Player teleported to plot

3. **Storage** (persistent)
   - Plot data saved to `plugins/Streuland/plots/`
   - Survives server restarts

## Plot Protection Rules

| Action | Owner | Trusted | Visitor |
|--------|-------|---------|---------|
| Build | ✅ | ✅ | ❌ |
| Break | ✅ | ✅ | ❌ |
| Interact* | ✅ | ✅ | ❌ |
| Walk | ✅ | ✅ | ✅ |
| Edit Paths | ❌ | ❌ | ❌ |

*Doors, buttons, levers, chests, etc.

## Data Storage

Plot data is stored as YAML files in `plugins/Streuland/plots/`:

```
plots/
├── plot_1.yml       # First plot
├── plot_2.yml       # Second plot
└── index.yml        # Quick lookup index
```

Each plot file contains:
- Plot ID and coordinates
- Owner UUID and creation timestamp
- List of trusted player UUIDs

## Requirements

- **Server**: PaperMC 1.16.5
- **Java**: 8 or higher
- **Permissions**: Default (no permission nodes needed)

## Building from Source

```bash
# Clone or extract the source
cd Streuland

# Build with Maven
mvn clean package

# Find JAR in target/
```

**Dependencies**: Paper API 1.16.5 (provided by Maven)

## Troubleshooting

### Plugin won't start
- Check console for errors
- Ensure Java 8+ is installed
- Verify Paper 1.16.5 is running

### Players can't create plots
- Check search radius in config (default 5000 blocks)
- Ensure terrain has suitable areas (not all ocean)
- Try `/plot create` multiple times (limited to 10 attempts)

### Plots not saving
- Check `plugins/Streuland/plots/` folder exists
- Verify write permissions on the server directory

## Performance Notes

- Plot creation is async to prevent lag
- Path generation uses simple Bresenham algorithm
- In-memory cache keeps all plots loaded
- Each plot query is O(n) - fine for <500 plots
- Paths are built as GRAVEL blocks (cheap to place)

## Future Plans

- Plot deletion with owner verification
- Schematic pasting on creation
- Permission levels (visitor/member/owner)
- Economy integration (sell plots)
- Admin commands (plot force-create, delete, etc)

## License

This plugin is created as part of the Streuland project.

---

**Version:** 1.0.0-SNAPSHOT
**Target:** Paper 1.16.5-R0.1-SNAPSHOT
**Created:** February 1, 2026
