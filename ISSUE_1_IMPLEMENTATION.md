# Issue #1 Implementation Checklist

All items below are implemented in this patch.

1. [x] Add configurable `plot.max-plots-per-player` setting.
2. [x] Add configurable `plot.delete-confirm-timeout-seconds` setting.
3. [x] Update `/plot` command usage text in `plugin.yml` for all subcommands.
4. [x] Harden player-plot lookup to avoid null-owner crashes.
5. [x] Replace owner index map with `UUID -> Set<plotId>` to support multiple plots per owner.
6. [x] Keep owner index consistent on every save/replacement.
7. [x] Skip indexing for unclaimed plots (`owner == null`).
8. [x] Return claimed plot instance from storage claim operation.
9. [x] Add storage method to unclaim plots (`CLAIMED -> UNCLAIMED`).
10. [x] Add storage delete method that returns the removed plot.
11. [x] Add next plot number discovery from existing IDs at startup.
12. [x] Initialize `PlotManager` plot counter from persisted data.
13. [x] Enforce configurable max-plot limit for `/plot create`.
14. [x] Enforce configurable max-plot limit for `/plot claim`.
15. [x] Add manager-level `claimPlotAt` helper.
16. [x] Add manager-level `unclaimPlot` helper with ownership/force checks.
17. [x] Add manager-level `deletePlot` helper with ownership/force checks.
18. [x] Keep spatial index in sync when plots are deleted.
19. [x] Add `/plot help` alias behavior when no args are passed.
20. [x] Add `/plot unclaim [plotId]` command.
21. [x] Add `/plot delete [plotId]` command with staged confirmation.
22. [x] Add `/plot confirm` command for pending delete execution.
23. [x] Add `/plot cancel` command to cancel pending delete.
24. [x] Add OP-only `/plot generate <gridSize> <spacing>` command.
25. [x] Add `/plot stats` command with claimed/unclaimed/grid cell counts.
26. [x] Extend help output to document all new subcommands.
27. [x] Allow trust/untrust targets to be resolved as offline players.
28. [x] Add clearer error and guidance messages for delete/unclaim flows.
