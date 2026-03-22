package de.streuland.approval;

import com.google.gson.Gson;
import de.streuland.discord.DiscordNotifier;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class PlotApprovalService {
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
        this.gson = new Gson();
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
        PlotApprovalRequest request = new PlotApprovalRequest(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getName(),
                System.currentTimeMillis(),
                UUID.randomUUID().toString().replace("-", "")
        );
        save(request);

        String baseUrl = plugin.getConfig().getString("discord.approval-base-url", "").trim();
        String message = "🧱 Plot approval requested by " + player.getName() + " (id: " + request.getId() + ")";
        if (!baseUrl.isEmpty()) {
            message += "\nApprove: " + baseUrl + "/api/approval/approve?id=" + request.getId() + "&token=" + request.getToken();
            message += "\nReject: " + baseUrl + "/api/approval/reject?id=" + request.getId() + "&token=" + request.getToken();
        } else {
            message += "\nApprove/reject in-game: /plotapprove approve|reject " + request.getId();
        }
        discordNotifier.sendWebhook("plot-approvals", message, Collections.<String, Object>emptyMap());
        return request;
    }

    public List<PlotApprovalRequest> listPending() {
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
        requests.sort(Comparator.comparingLong(PlotApprovalRequest::getCreatedAt));
        return requests;
    }

    public PlotApprovalRequest getPending(String id) {
        return readFile(new java.io.File(pendingDir, id + ".json"));
    }

    public boolean approve(String id) {
        PlotApprovalRequest request = getPending(id);
        if (request == null) {
            return false;
        }
        World world = Bukkit.getWorld(request.getWorldName());
        if (world == null) {
            return false;
        }
        plotManager.createPlotAsync(request.getPlayerId(), world).thenAccept(plot -> {
            if (plot == null) {
                return;
            }
            List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(plot);
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                pathGenerator.buildPathBlocks(pathBlocks);
                Player target = Bukkit.getPlayer(request.getPlayerId());
                if (target != null && target.isOnline()) {
                    target.sendMessage("§aDein Plot-Antrag wurde genehmigt! Plot: " + plot.getPlotId());
                }
            });
        });
        delete(id);
        return true;
    }

    public boolean reject(String id) {
        PlotApprovalRequest request = getPending(id);
        if (request == null) {
            return false;
        }
        Player target = Bukkit.getPlayer(request.getPlayerId());
        if (target != null && target.isOnline()) {
            target.sendMessage("§cDein Plot-Antrag wurde abgelehnt.");
        }
        delete(id);
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

    private void save(PlotApprovalRequest request) {
        java.io.File file = new java.io.File(pendingDir, request.getId() + ".json");
        try {
            Files.write(file.toPath(), gson.toJson(request).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save pending approval " + request.getId());
        }
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

    private void delete(String id) {
        java.io.File file = new java.io.File(pendingDir, id + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
}
