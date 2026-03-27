package de.streuland.dashboard;

import de.streuland.plot.AreaType;
import de.streuland.plot.Permission;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.Role;
import de.streuland.plot.market.MarketListing;
import de.streuland.plot.market.PlotMarketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard-facing projection layer separated from raw game logic.
 */
public class PlotDashboardService {
    private final PlotManager plotManager;
    private final PlotMarketService marketService;

    public PlotDashboardService(PlotManager plotManager, PlotMarketService marketService) {
        this.plotManager = plotManager;
        this.marketService = marketService;
    }

    public List<Map<String, Object>> listPlots(String search, String owner, String areaType, String marketStatus) {
        String normalizedSearch = normalize(search);
        String normalizedOwner = normalize(owner);
        String normalizedAreaType = normalize(areaType);
        String normalizedMarketStatus = normalize(marketStatus);
        Map<String, MarketListing> listings = getListingMap();

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Plot plot : plotManager.getAllPlots()) {
            Map<String, Object> row = toSummary(plot, listings.get(plot.getPlotId()));
            if (!matches(row, normalizedSearch, normalizedOwner, normalizedAreaType, normalizedMarketStatus)) {
                continue;
            }
            rows.add(row);
        }
        Collections.sort(rows, Comparator.comparing(o -> String.valueOf(o.get("plotId"))));
        return rows;
    }

    public Map<String, Object> getPlotDetails(String plotId) {
        Plot plot = plotManager.getStorage().getPlot(plotId);
        if (plot == null) {
            return null;
        }
        MarketListing listing = getListingMap().get(plot.getPlotId());
        Map<String, Object> payload = toSummary(plot, listing);
        PlotData data = plotManager.getStorage().getPlotData(plot.getPlotId());

        payload.put("coordinates", buildCoordinates(plot));
        payload.put("roles", buildRoles(plot));
        payload.put("upgrades", new ArrayList<String>(data.getUnlockedAbilities()));
        payload.put("stats", new LinkedHashMap<String, Double>(data.getStatBonuses()));
        payload.put("publicVisit", data.isPublicVisitEnabled());
        payload.put("showcaseTitle", data.getShowcaseTitle());
        payload.put("showcaseTags", new ArrayList<String>(data.getShowcaseTags()));
        return payload;
    }

    public Map<String, Object> checkPermission(String plotId, UUID player, Permission permission) {
        Plot plot = plotManager.getStorage().getPlot(plotId);
        if (plot == null) {
            return null;
        }
        Permission checked = permission == null ? Permission.BUILD : permission;
        boolean allowed = plotManager.hasPermission(plotId, player, checked);

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("plotId", plotId);
        response.put("player", player == null ? null : player.toString());
        response.put("permission", checked.name());
        response.put("allowed", allowed);
        response.put("role", player == null ? Role.VISITOR.name() : plot.getRole(player).name());
        return response;
    }

    public boolean mutateTrustedPlayer(String plotId, UUID actor, UUID target, String action) {
        if (plotId == null || actor == null || target == null || action == null) {
            return false;
        }
        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);
        if ("add".equals(normalizedAction)) {
            return plotManager.assignRole(plotId, actor, target, Role.BUILDER);
        }
        if ("remove".equals(normalizedAction)) {
            return plotManager.removeRole(plotId, actor, target);
        }
        return false;
    }

    private Map<String, Object> toSummary(Plot plot, MarketListing listing) {
        PlotData data = plotManager.getStorage().getPlotData(plot.getPlotId());
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("plotId", plot.getPlotId());
        row.put("owner", plot.getOwner() == null ? null : plot.getOwner().toString());
        row.put("trustedPlayers", toUuidStrings(plot.getTrustedPlayers()));
        row.put("areaType", plot.getAreaType().name());
        row.put("upgrades", new ArrayList<String>(data.getUnlockedAbilities()));
        row.put("marketStatus", listing == null ? "NOT_LISTED" : "LISTED");
        row.put("marketPrice", listing == null ? null : listing.getPrice());
        row.put("createdAt", plot.getCreatedAt());
        return row;
    }

    private Map<String, Object> buildCoordinates(Plot plot) {
        Map<String, Object> coordinates = new LinkedHashMap<String, Object>();
        coordinates.put("centerX", plot.getCenterX());
        coordinates.put("centerZ", plot.getCenterZ());
        coordinates.put("spawnY", plot.getSpawnY());
        coordinates.put("size", plot.getSize());
        return coordinates;
    }

    private Map<String, String> buildRoles(Plot plot) {
        Map<String, String> roles = new LinkedHashMap<String, String>();
        for (Map.Entry<UUID, Role> entry : plot.getRoles().entrySet()) {
            roles.put(entry.getKey().toString(), entry.getValue().name());
        }
        return roles;
    }

    private List<String> toUuidStrings(Iterable<UUID> players) {
        List<String> values = new ArrayList<String>();
        for (UUID id : players) {
            values.add(id.toString());
        }
        Collections.sort(values);
        return values;
    }

    private Map<String, MarketListing> getListingMap() {
        Map<String, MarketListing> map = new HashMap<String, MarketListing>();
        for (MarketListing listing : marketService.getActiveListingsSnapshot()) {
            map.put(listing.getPlotId(), listing);
        }
        return map;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(Map<String, Object> row,
                            String search,
                            String owner,
                            String areaType,
                            String marketStatus) {
        if (!owner.isEmpty()) {
            Object rowOwner = row.get("owner");
            if (rowOwner == null || !String.valueOf(rowOwner).toLowerCase(Locale.ROOT).contains(owner)) {
                return false;
            }
        }

        if (!areaType.isEmpty()) {
            AreaType rowType = AreaType.valueOf(String.valueOf(row.get("areaType")));
            if (!rowType.name().toLowerCase(Locale.ROOT).contains(areaType)) {
                return false;
            }
        }

        if (!marketStatus.isEmpty()) {
            String rowMarket = String.valueOf(row.get("marketStatus")).toLowerCase(Locale.ROOT);
            if (!rowMarket.equals(marketStatus)) {
                return false;
            }
        }

        if (search.isEmpty()) {
            return true;
        }
        String plotId = String.valueOf(row.get("plotId")).toLowerCase(Locale.ROOT);
        String rowOwner = String.valueOf(row.get("owner")).toLowerCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        List<String> trusted = (List<String>) row.get("trustedPlayers");
        return plotId.contains(search)
                || rowOwner.contains(search)
                || String.join(",", trusted).toLowerCase(Locale.ROOT).contains(search);
    }
}
