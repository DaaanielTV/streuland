package de.streuland.approval;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.streuland.discord.DiscordNotifier;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class PlotApprovalService {
    private static final ApprovalRule DEFAULT_RULE = ApprovalRule.MODERATOR_ONLY;

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    private final DiscordNotifier discordNotifier;
    private final Gson gson;
    private final java.io.File pendingDir;

    public PlotApprovalService(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator, DiscordNotifier discordNotifier) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
        this.discordNotifier = discordNotifier;
        this.gson = new GsonBuilder().serializeNulls().create();
        this.pendingDir = new java.io.File(plugin.getDataFolder(), "pending");
        if (!pendingDir.exists()) {
            pendingDir.mkdirs();
        }
    }

    public boolean requiresApproval(Player player) {
        if (!plugin.getConfig().getBoolean("plot.create-requires-approval", false)) {
            return false;
        }
        List<String> whitelist = plugin.getConfig().getStringList("plot.create-approval-whitelist");
        String uuid = player.getUniqueId().toString();
        return !whitelist.contains(uuid) && !whitelist.contains(player.getName());
    }

    public PlotApprovalRequest createPending(Player player) {
        return request(player, PlotApprovalActionType.CREATE, null, Collections.emptyMap());
    }

    public PlotApprovalRequest request(Player requester, PlotApprovalActionType actionType, String plotId, Map<String, String> details) {
        long now = System.currentTimeMillis();
        long expiresAt = now + (getExpirationSeconds() * 1000L);
        PlotApprovalRequest request = PlotApprovalRequest.pending(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                actionType,
                plotId,
                requester.getUniqueId(),
                requester.getName(),
                requester.getWorld().getName(),
                now,
                expiresAt,
                UUID.randomUUID().toString().replace("-", ""),
                details
        );
        save(request);
        sendRequestNotification(request);
        return request;
    }

    public List<PlotApprovalRequest> listPending() {
        expireRequests();
        return loadAll().stream()
                .filter(PlotApprovalRequest::isPending)
                .sorted(Comparator.comparingLong(PlotApprovalRequest::getCreatedAt))
                .collect(Collectors.toList());
    }

    public PlotApprovalRequest getPending(String id) {
        PlotApprovalRequest req = readFile(new java.io.File(pendingDir, id + ".json"));
        if (req == null || !req.isPending()) {
            return null;
        }
        if (req.isExpired(System.currentTimeMillis())) {
            req.markExpired(System.currentTimeMillis());
            save(req);
            return null;
        }
        return req;
    }

    public List<PlotApprovalRequest> getAllRequests() {
        expireRequests();
        return loadAll().stream()
                .sorted(Comparator.comparingLong(PlotApprovalRequest::getCreatedAt))
                .collect(Collectors.toList());
    }

    public boolean approve(String id) {
        return approve(id, null, "legacy approve");
    }

    public boolean approve(String id, Player reviewer, String comment) {
        PlotApprovalRequest request = getPending(id);
        if (request == null) {
            return false;
        }
        if (reviewer != null && !canReview(request, reviewer)) {
            return false;
        }
        boolean executed = executeApprovalAction(request);
        if (!executed) {
            return false;
        }
        request.markApproved(reviewer == null ? null : reviewer.getUniqueId(), System.currentTimeMillis(), comment);
        save(request);
        notifyRequester(request, "§aDein Antrag wurde genehmigt (" + request.getActionType().name().toLowerCase() + ").");
        return true;
    }

    public boolean reject(String id) {
        return deny(id, null, "legacy reject");
    }

    public boolean deny(String id, Player reviewer, String reason) {
        PlotApprovalRequest request = getPending(id);
        if (request == null) {
            return false;
        }
        if (reviewer != null && !canReview(request, reviewer)) {
            return false;
        }
        request.markDenied(reviewer == null ? null : reviewer.getUniqueId(), System.currentTimeMillis(), reason);
        save(request);
        notifyRequester(request, "§cDein Antrag wurde abgelehnt." + (reason == null ? "" : " §7" + reason));
        return true;
    }

    public boolean cancel(String id, Player requester, String reason) {
        PlotApprovalRequest request = getPending(id);
        if (request == null) {
            return false;
        }
        if (!request.getRequesterId().equals(requester.getUniqueId()) && !requester.hasPermission("streuland.plot.approval")) {
            return false;
        }
        request.markCanceled(requester.getUniqueId(), System.currentTimeMillis(), reason);
        save(request);
        return true;
    }

    public boolean approveByToken(String id, String token) {
        PlotApprovalRequest req = getPending(id);
        return req != null && req.getToken().equals(token) && approve(id);
    }

    public boolean rejectByToken(String id, String token) {
        PlotApprovalRequest req = getPending(id);
        return req != null && req.getToken().equals(token) && reject(id);
    }

    public int expireRequests() {
        long now = System.currentTimeMillis();
        int expired = 0;
        for (PlotApprovalRequest req : loadAll()) {
            if (req.isPending() && req.isExpired(now)) {
                req.markExpired(now);
                save(req);
                expired++;
            }
        }
        return expired;
    }

    public boolean canReview(PlotApprovalRequest request, Player actor) {
        ApprovalRule rule = resolveRule(request.getActionType());
        if (rule == ApprovalRule.MODERATOR_ONLY) {
            return actor.hasPermission("streuland.plot.approval") || actor.hasPermission("streuland.plot.approval.moderator");
        }
        if (rule == ApprovalRule.OWNER_ONLY) {
            if (request.getPlotId() == null || request.getPlotId().trim().isEmpty()) {
                return actor.hasPermission("streuland.plot.approval") || actor.hasPermission("streuland.plot.approval.moderator");
            }
            Plot plot = findPlotById(request.getPlotId());
            return plot != null && plot.getOwner() != null && plot.getOwner().equals(actor.getUniqueId());
        }
        List<String> approvers = plugin.getConfig().getStringList("plot.approval.configurable.approvers");
        String uuid = actor.getUniqueId().toString();
        return approvers.contains(uuid)
                || approvers.contains(actor.getName())
                || actor.hasPermission("streuland.plot.approval")
                || actor.hasPermission("streuland.plot.approval.moderator");
    }

    private Plot findPlotById(String plotId) {
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getPlotId().equals(plotId)) {
                return plot;
            }
        }
        return null;
    }

    private ApprovalRule resolveRule(PlotApprovalActionType actionType) {
        FileConfiguration config = plugin.getConfig();
        String key = "plot.approval.rules." + actionType.configKey();
        String configured = config.getString(key, null);
        return ApprovalRule.fromConfig(configured, DEFAULT_RULE);
    }

    private long getExpirationSeconds() {
        return Math.max(5L, plugin.getConfig().getLong("plot.approval.expiration-seconds", 90L));
    }

    private boolean executeApprovalAction(PlotApprovalRequest request) {
        PlotApprovalActionType actionType = request.getActionType();
        switch (actionType) {
            case CREATE:
                return executeCreateApproval(request);
            case DELETE:
                return request.getPlotId() != null && plotManager.deletePlot(request.getPlotId(), request.getRequesterId(), true);
            case UNCLAIM:
                return request.getPlotId() != null && plotManager.unclaimPlot(request.getPlotId(), request.getRequesterId(), true);
            case TRANSFER:
                String target = request.getDetails().get("targetPlayerId");
                if (request.getPlotId() == null || target == null) {
                    return false;
                }
                try {
                    UUID targetId = UUID.fromString(target);
                    return plotManager.transferPlotOwnership(request.getPlotId(), request.getRequesterId(), targetId);
                } catch (IllegalArgumentException ex) {
                    return false;
                }
            case RESIZE:
                return true;
            default:
                return false;
        }
    }

    private boolean executeCreateApproval(PlotApprovalRequest request) {
        World world = Bukkit.getWorld(request.getWorldName());
        if (world == null) {
            return false;
        }
        plotManager.createPlotAsync(request.getRequesterId(), world).thenAccept(plot -> {
            if (plot == null) {
                return;
            }
            List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(plot);
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                pathGenerator.buildPathBlocks(pathBlocks);
                Player target = Bukkit.getPlayer(request.getRequesterId());
                if (target != null && target.isOnline()) {
                    target.sendMessage("§aDein Plot-Antrag wurde genehmigt! Plot: " + plot.getPlotId());
                }
            });
        });
        return true;
    }

    private void sendRequestNotification(PlotApprovalRequest request) {
        String baseUrl = plugin.getConfig().getString("discord.approval-base-url", "").trim();
        String message = "🧱 Plot approval requested by " + request.getRequesterName()
                + " (id: " + request.getId() + ", action: " + request.getActionType().name().toLowerCase() + ")";
        if (!baseUrl.isEmpty()) {
            message += "\nApprove: " + baseUrl + "/api/approval/approve?id=" + request.getId() + "&token=" + request.getToken();
            message += "\nReject: " + baseUrl + "/api/approval/reject?id=" + request.getId() + "&token=" + request.getToken();
        } else {
            message += "\nApprove/reject in-game: /plot approve|deny " + request.getId();
        }
        discordNotifier.sendWebhook("plot-approvals", message, Collections.<String, Object>emptyMap());
    }

    private void notifyRequester(PlotApprovalRequest request, String message) {
        Player target = Bukkit.getPlayer(request.getRequesterId());
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        }
    }

    private void save(PlotApprovalRequest request) {
        java.io.File file = new java.io.File(pendingDir, request.getId() + ".json");
        try {
            Files.write(file.toPath(), gson.toJson(request).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save pending approval " + request.getId());
        }
    }

    private List<PlotApprovalRequest> loadAll() {
        java.io.File[] files = pendingDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return Collections.emptyList();
        }
        List<PlotApprovalRequest> requests = new ArrayList<>();
        for (java.io.File file : files) {
            PlotApprovalRequest req = readFile(file);
            if (req != null) {
                requests.add(req);
            }
        }
        return requests;
    }

    private PlotApprovalRequest readFile(java.io.File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return gson.fromJson(text, PlotApprovalRequest.class);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read pending approval file " + file.getName());
            return null;
        }
    }
}
