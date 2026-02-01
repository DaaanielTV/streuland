# Three-State Plot Architecture Implementation

## Overview
Successfully refactored Streuland's plot system from a binary model (claimed/unclaimed) to a proper three-tier architecture with explicit state tracking, spatial indexing, and position-based claiming.

## Architecture Changes

### 1. **Plot State Enum** (`Plot.java`)
- Added `PlotState` enum with two states:
  - `UNCLAIMED`: Plot exists but has no owner. Any player can build here.
  - `CLAIMED`: Plot has an owner. Only owner/trusted players can build.
- Updated `isAllowed()` method:
  - UNCLAIMED plots return `true` for any player (public building)
  - CLAIMED plots check owner/trusted status as before
- Two constructors for flexibility:
  - Default constructor infers state from owner (null → UNCLAIMED, non-null → CLAIMED)
  - Full constructor allows explicit state specification

### 2. **Spatial Grid Indexing** (New `SpatialGrid.java`)
- Implements 25×25 grid-based spatial indexing for O(1) location-to-plot lookups
- **Cell Size**: 25 blocks (tunable parameter in class)
- **Features**:
  - `rebuild()`: Rebuilds grid from loaded plots (called on startup)
  - `getPlotAt(x, z)`: Fast location lookup via primary cell + 8 adjacent cells
  - `isLocationAvailable()`: Checks for overlaps when generating new plots
  - `addPlot()` / `removePlot()`: Incremental index updates
- Replaces linear O(n) search through all plots on every block interaction

### 3. **PlotManager Integration**
- Added `SpatialGrid` field, initialized during construction
- Spatial grid is rebuilt from storage on startup
- Updated `getPlotAt()` to use `spatialGrid.getPlotAt()` instead of linear search
- Updated `isValidPlotLocation()` to use `spatialGrid.isLocationAvailable()` for overlap detection
- Updated `createPlotAtLocation()` to add plots to grid after creation
- Updated `generateUnclaimedPlots()` to create UNCLAIMED state plots and add to grid
- Added `getSpatialGrid()` getter for access to grid

### 4. **ProtectionListener Three-Zone Logic**
Completely rewritten permission system with explicit zone handling:

**ZONE 1 - PATHS (Y=63-67)**
- Material checks: COBBLESTONE, MOSSY_COBBLESTONE only at Y=63
- Always denied (unbreakable, can't place on)
- Message: "Du kannst Path-Blöcke nicht abbauen!"

**ZONE 2 - UNCLAIMED PLOTS**
- Plot exists with `state == UNCLAIMED`
- Any player can build/break/interact
- Effectively public building area

**ZONE 3 - CLAIMED PLOTS**
- Plot exists with `state == CLAIMED`
- Only owner/trusted players can build
- Visitors blocked from breaking/placing (pre-existing interaction rules still apply)
- Message: "Dieser Plot gehört [Owner]!"

**ZONE 4 - WILDERNESS**
- Outside all plots
- No building allowed anywhere
- Message: "Du kannst nur in deinem Plot bauen!"

Updates to event handlers:
- `onBlockBreak()`: Explicit zone checks with appropriate messages
- `onBlockPlace()`: Same three-zone routing
- `onPlayerInteract()`: UNCLAIMED plots allow all interaction; CLAIMED plots respect ownership

### 5. **PlotStorage Persistence**
Updated YAML serialization to handle state field:
- **Save**: Writes `state: UNCLAIMED|CLAIMED` to plot YAML files
- **Load**: Reads state from file; backwards compatible (infers state if missing)
- **claimPlot()**: Explicitly transitions plot from UNCLAIMED → CLAIMED state
- **savePlot()**: Handles null owners gracefully (unclaimed plots)

### 6. **PlotCommandExecutor Refactored** (Two Clear Commands)

#### `/plot create`
- **Purpose**: Generate a NEW plot at random location (on-demand generation)
- **Flow**:
  1. Check plot limit (4 plots)
  2. Generate random location asynchronously
  3. Validate terrain and spacing
  4. Create CLAIMED plot (immediately owned by player)
  5. Build path blocks
  6. Counts toward 4-plot limit
- **Admin Use**: Can be called by admins to generate plot pools

#### `/plot claim`
- **Purpose**: Claim an EXISTING unclaimed plot at player location
- **Flow**:
  1. Find plot at player location (O(1) via spatial grid)
  2. Verify plot is in UNCLAIMED state
  3. Check plot limit (4 plots)
  4. Transition plot from UNCLAIMED → CLAIMED
  5. Build path blocks
  6. Counts toward 4-plot limit
- **Difference from `/plot create`**:
  - Position-based (player must stand in plot)
  - No generation
  - Works only with UNCLAIMED plots
  - Immediate feedback ("Du stehst in keinem Plot")

#### Other Commands
- `/plot info`: Now displays plot state (UNCLAIMED/CLAIMED)
- `/plot trust` / `/plot untrust`: Null-safe (checks owner != null)
- `/plot home`: Works only with CLAIMED plots
- `/plot list`: Lists player's CLAIMED plots

### 7. **New SpatialGrid Class**
Complete implementation in `SpatialGrid.java`:
- Public API: `rebuild()`, `addPlot()`, `removePlot()`, `getPlotAt()`, `isLocationAvailable()`
- Uses Map<String, List<Plot>> for grid cells (key format: "cellX,cellZ")
- Checks primary cell + 8 adjacent cells to handle boundary cases
- Memory efficient: Only stores plots, no unused grid cells
- Stateless: Can be rebuilt anytime without data loss

## Data Model Changes

### Plot class now contains:
```java
- String plotId
- int centerX, centerZ, size
- UUID owner (can be null)
- PlotState state (UNCLAIMED or CLAIMED)
- long createdAt
- int spawnY
- Set<UUID> trustedPlayers
```

### YAML File Format (example):
```yaml
id: plot_123
centerX: 100
centerZ: 200
size: 64
owner: "550e8400-e29b-41d4-a716-446655440000"
state: CLAIMED
createdAt: 1735689600000
spawnY: 65
trusted:
  - "550e8400-e29b-41d4-a716-446655440001"
```

Unclaimed plot example:
```yaml
id: unclaimed_5
centerX: 500
centerZ: 600
size: 64
owner: null
state: UNCLAIMED
createdAt: 1735689600000
spawnY: 65
trusted: []
```

## Performance Improvements

| Operation | Before | After | Improvement |
|-----------|--------|-------|-------------|
| Block interaction (location lookup) | O(n) linear scan | O(1) grid cell | n × faster |
| Plot validity checks (generation) | O(n) linear scan | O(1) grid + O(distance check) | ~n faster |
| Spatial reasoning | O(n) for nearby plots | O(1) cell lookup | Instant |

Where n = total number of plots in world.

## Backwards Compatibility

✅ **YAML Format**: Handles missing `state` field gracefully
  - If `state` field missing and `owner` is null → defaults to UNCLAIMED
  - If `state` field missing and `owner` exists → defaults to CLAIMED

✅ **Existing Plots**: Auto-migrates on first load
  - Plots without explicit state get correct state inferred
  - Grid is rebuilt from all loaded plots

⚠️ **First Load After Update**:
  - Existing plot files will be missing `state` field
  - System infers state based on owner presence
  - On next save, explicit `state` field will be written

## Testing Checklist

- [ ] Pre-generate unclaimed plots using `/plot create` (for testing)
- [ ] Player stands on unclaimed plot and claims with `/plot claim`
- [ ] Player tries to build in unclaimed plot (should succeed)
- [ ] Player claims plot, owner builds (should succeed)
- [ ] Visitor tries to build in claimed plot (should fail with owner message)
- [ ] Check `/plot info` shows state correctly
- [ ] Verify path generation works after claiming
- [ ] Test 4-plot limit enforcement
- [ ] Verify spatial grid with many plots (performance)
- [ ] Check YAML files for state field persistence

## Known Limitations & Future Improvements

1. **Grid Cell Size**: Currently hardcoded to 25 blocks. Could be made configurable per world.

2. **Spiral vs Random Generation**: `generateUnclaimedPlots()` uses random validation. Consider adding spiral pattern option.

3. **Pre-generation Strategy**: Recommend admins pre-generate UNCLAIMED plots during world setup:
   ```
   /plot create (admin calls many times to seed pool)
   Players then /plot claim from the pool
   ```

4. **Multi-Server Sync**: If using multiple servers, spatial grid must be rebuilt on each server's startup.

5. **Dynamic Grid Updates**: Grid updates are synchronous. For very large worlds (10k+ plots), consider async rebuild.

## Summary of Files Modified

1. **Plot.java**: Added PlotState enum, state field, constructors, getState() method, updated isAllowed()
2. **SpatialGrid.java**: NEW file - complete spatial indexing implementation
3. **PlotManager.java**: Added spatial grid field, updated getPlotAt(), isValidPlotLocation(), createPlotAtLocation()
4. **ProtectionListener.java**: Complete rewrite of permission logic with three-zone system
5. **PlotStorage.java**: Updated YAML serialization/deserialization for state field, updated claimPlot()
6. **PlotCommandExecutor.java**: Refactored handleCreate() and handleClaim() as distinct operations, updated handleInfo()

## Migration Guide

**For Existing Servers**:
1. Build and deploy new JAR
2. Server startup will load existing plots and infer state
3. Plots will be auto-migrated on next save (state field added)
4. No manual migration needed

**For New Servers**:
1. Deploy new version
2. Pre-generate plots: Admin calls `/plot create` multiple times
3. Players claim with `/plot claim` from the generated pool
4. System works as designed from day one
