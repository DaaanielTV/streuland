# Streuland Plot Plugin - Architecture Documentation

## Overview
Streuland is a Paper 1.16.5 plugin that creates a random plot system within vanilla Minecraft worlds. Plots are randomly distributed (not grid-based), automatically connected via paths, and fully protected for their owners.

## Project Structure

```
streuland/
├── src/main/java/de/streuland/
│   ├── StreulandPlugin.java              # Main plugin class (JavaPlugin)
│   ├── plot/
│   │   ├── Plot.java                     # Plot data model
│   │   ├── PlotManager.java              # Central plot orchestrator
│   │   └── PlotStorage.java              # YAML persistence layer
│   ├── path/
│   │   └── PathGenerator.java            # Path generation (Bresenham algorithm)
│   ├── listener/
│   │   └── ProtectionListener.java       # Event-based plot protection
│   └── command/
│       └── PlotCommandExecutor.java      # /plot command handler
├── src/main/resources/
│   ├── plugin.yml                        # Plugin metadata
│   └── config.yml                        # Configurable settings
└── pom.xml                               # Maven build configuration
```

## Component Responsibilities

### 1. **Plot** (`plot/Plot.java`)
- **Data model** for a single plot
- Stores: ID, center coordinates (X/Z), size, owner UUID, creation timestamp
- Provides methods: `contains(x,z)`, `isAllowed(player)`, `distance(plot)`, trust management
- **Immutable after creation** - modifications go through PlotManager

### 2. **PlotStorage** (`plot/PlotStorage.java`)
- **Persistent YAML storage** for plot data
- One YAML file per plot in `plugins/Streuland/plots/` folder
- Maintains in-memory cache for performance
- Methods: `savePlot()`, `loadAllPlots()`, `getPlot()`, `getAllPlots()`, `deletePlot()`
- **Thread-safe for reads**, saves are scheduled on main thread

### 3. **PlotManager** (`plot/PlotManager.java`)
- **Central orchestrator** for plot operations
- Handles:
  - Plot creation with async validation
  - Terrain suitability checking (water/lava rejection)
  - Distance constraints (no overlap, minimum separation)
  - Plot queries by coordinates
  - Trust/untrust operations
- **Returns CompletableFuture** for async operations
- Validates terrain before creating plots

### 4. **PathGenerator** (`path/PathGenerator.java`)
- **Generates paths** between plots using Bresenham line algorithm
- Strategy:
  - Connects new plot to nearest existing plot
  - Simple line-of-sight (no complex pathfinding)
  - Bresenham 2D line for integer coordinates
  - Expands line to configurable path width
  - Places GRAVEL path blocks
  - Removes trees/grass, preserves water/lava
- **Returns list of BlockPositions** to be applied on main thread

### 5. **ProtectionListener** (`listener/ProtectionListener.java`)
- **Event handlers** for plot protection using Bukkit events:
  - `BlockBreakEvent` - Prevent unauthorized block breaking
  - `BlockPlaceEvent` - Prevent unauthorized building
  - `PlayerInteractEvent` - Prevent visitor interaction with doors/buttons
- Rules:
  - Only owner/trusted can build in plots
  - Visitors can walk through but not interact
  - Paths (GRAVEL blocks) are indestructible
  - Configurable visitor interaction restrictions

### 6. **PlotCommandExecutor** (`command/PlotCommandExecutor.java`)
- Handles `/plot` command and subcommands:
  - `/plot create` - Create new plot (async)
  - `/plot info` - Show current plot details
  - `/plot trust <player>` - Add trusted player
  - `/plot untrust <player>` - Remove trusted player
  - `/plot home` - Teleport to plot
  - `/plot list` - List player's plots

### 7. **StreulandPlugin** (`StreulandPlugin.java`)
- Main JavaPlugin class
- Initializes all components in dependency order
- Registers events and commands
- Handles enable/disable lifecycle

## Key Design Decisions

### ✅ Async/Sync Boundaries
- **Terrain validation**: Runs async (CPU-intensive)
- **Block changes**: Scheduled on main thread via `Bukkit.getScheduler()`
- **Storage operations**: Cached in memory, saves async-safe

**Why**: Prevents lag spikes when players create plots. Async validation finds location, then main thread only applies blocks.

### ✅ Random Plot Distribution (NOT Grid)
- Uses random X/Z coordinates within configurable radius
- Validates 10 attempts to find suitable location
- Distance checked against all existing plots
- **Much more organic** than grid-based systems

### ✅ Simple Path Generation
- Bresenham line algorithm (stable, no floating paths)
- Connects new plot to **nearest plot only**
- Expands line by path width
- Path merge logic: intentionally omitted for stability
  - Each plot generates one path to nearest neighbor
  - Paths may overlap, which is fine (multiple GRAVEL blocks)

### ✅ Vanilla World Preservation
- **No custom chunk generator** - uses default Vanilla generation
- Plots placed **after** world is generated
- Terrain validation ensures plots spawn in suitable locations
- Biomes, rivers, mountains remain untouched

### ✅ Persistent Storage
- **YAML-based** (simple, human-readable, Bukkit-native)
- One file per plot for modularity
- Index file for quick lookup
- In-memory cache for performance

## Configuration (`config.yml`)

```yaml
world:
  name: "world"              # World to use

plot:
  size: 64                   # Plot size (64x64)
  min-distance: 100          # Minimum separation between plots
  max-search-radius: 5000    # Search radius from spawn
  base-height: 64            # Reference Y-level

path:
  width: 4                   # Path width in blocks
  use-curves: true           # (Placeholder for future)

protection:
  allow-visitor-interact: false

terrain:
  water-rejection: true      # Reject plots in water
  lava-rejection: true       # Reject plots near lava
  adjacent-water-threshold: 3  # Water block count threshold

async:
  validate-async: true       # Run validation async
  path-generation-async: true  # (Can be async later)
```

## Event Handling Flow

### Plot Creation
```
Player: /plot create
  ↓
PlotCommandExecutor.handleCreate()
  ↓
PlotManager.createPlotAsync() [ASYNC]
  → Find random location
  → Validate terrain
  → Check distance to existing plots
  ↓
PathGenerator.generatePath() [MAIN THREAD]
  → Bresenham line to nearest plot
  → Expand to path width
  → Replace grass/trees with GRAVEL
  ↓
PlotStorage.savePlot() [MAIN THREAD]
  → Save to YAML file
  → Update in-memory cache
  ↓
Player: Success message
```

### Block Protection
```
Player: Break/Place/Interact
  ↓
ProtectionListener.on*Event()
  ↓
Check if block is path (GRAVEL)
  → Deny
  ↓
Check if in plot
  → Get plot at coordinates
  → Check if player is owner/trusted
  → Allow or deny
```

## Performance Considerations

### Chunk Loading
- Validation uses `World.getHighestBlockAt()` which auto-loads chunks
- Limited to 10 attempts per `/plot create` to prevent excessive loading
- Consider adding chunk unload after validation

### Distance Checking
- O(n) complexity where n = number of existing plots
- Fine for small servers (<500 plots)
- Could use spatial partitioning for large servers

### Storage
- In-memory cache keeps all plots loaded
- Saves only on modification (not continuous)
- Consider periodic backup in future

## Paper 1.16.5 Specific Notes

⚠️ **Async Considerations**
- Cannot modify blocks from async thread
- Use `Bukkit.getScheduler().scheduleSyncDelayedTask()` for block changes
- CompletableFuture + Scheduler combination is safe

⚠️ **Event Threading**
- All events fire on main thread
- Safe to call blocking operations in event handlers

⚠️ **Deprecated Methods**
- Avoid `@EventHandler(ignoreCancelled=true)` if cancelling in handler
- Use `org.bukkit.Material` enum (not string lookups)

⚠️ **Material Checks**
- 1.16.5 has `GRAVEL`, `COBBLESTONE` (use these for paths)
- Double-check material names if extending

## Future Enhancement Ideas

1. **Plot Deletion** - Add `/plot delete` command with owner verification
2. **Plot Selling** - Implement economy integration (if using Vault)
3. **Schematic Pasting** - Generate plot structures on creation
4. **Permission Levels** - Owner/Trusted/Visitor with granular controls
5. **Visit Counter** - Track who visits each plot
6. **Daily Maintenance** - Save/backup on server restart
7. **Spatial Indexing** - Quadtree for faster plot queries on large servers
8. **Dynamic Path Merging** - Sophisticated algorithm to merge overlapping paths

## Building & Testing

**Build JAR:**
```bash
mvn clean package
```

**Output:**
```
target/Streuland-1.0.0-SNAPSHOT.jar
```

**Installation:**
1. Copy JAR to `server/plugins/`
2. Restart server
3. Check logs for "Streuland enabled successfully!"
4. Run `/plot create` to test

**Console Output Example:**
```
[Streuland] ✓ PlotStorage initialized
[Streuland] ✓ PlotManager initialized
[Streuland] ✓ PathGenerator initialized
[Streuland] ✓ ProtectionListener registered
[Streuland] ✓ Commands registered
[Streuland] Streuland enabled successfully!
[Streuland] Loaded 0 plots
```

---

**Created:** February 1, 2026
**Version:** Streuland v1.0.0-SNAPSHOT
**Target:** Paper 1.16.5-R0.1-SNAPSHOT
**Java:** 8+
