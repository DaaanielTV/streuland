package de.streuland.dashboard;

import de.streuland.backup.PlotBackupCoordinator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Versioned backup workflow wrapper with pre-restore restore-point creation.
 */
public class PlotBackupOperationsService {
    private static final String VERSION = "backup-v1";

    private final PlotBackupCoordinator backupCoordinator;
    private final PlotAuditLogService auditLogService;

    public PlotBackupOperationsService(PlotBackupCoordinator backupCoordinator, PlotAuditLogService auditLogService) {
        this.backupCoordinator = backupCoordinator;
        this.auditLogService = auditLogService;
    }

    public CompletableFuture<Map<String, Object>> createBackup(String plotRef, UUID actorId, String actorName, String note) {
        return backupCoordinator.takeSnapshot(plotRef, actorId, actorName, note)
                .thenApply(snapshotId -> {
                    if (snapshotId == null) {
                        return null;
                    }
                    String resolvedPlotId = backupCoordinator.resolvePlotId(plotRef).orElse(plotRef);
                    Map<String, Object> payload = buildBackupPayload(resolvedPlotId, snapshotId, note, null);
                    auditLogService.record(resolvedPlotId, "backup_create", actorName, payload);
                    return payload;
                });
    }

    public List<Map<String, Object>> listBackups(String plotRef) {
        List<String> ids = backupCoordinator.listSnapshots(plotRef);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String resolvedPlotId = backupCoordinator.resolvePlotId(plotRef).orElse(plotRef);
        for (String id : ids) {
            rows.add(buildBackupPayload(resolvedPlotId, id, null, null));
        }
        return rows;
    }

    public CompletableFuture<Map<String, Object>> restoreBackup(String plotRef, String snapshotId, UUID actorId, String actorName) {
        String resolvedPlotId = backupCoordinator.resolvePlotId(plotRef).orElse(null);
        if (resolvedPlotId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return backupCoordinator.takeSnapshot(resolvedPlotId, actorId, actorName, "pre-restore:" + snapshotId)
                .thenCompose(restorePointId -> backupCoordinator.restore(resolvedPlotId, snapshotId)
                        .thenApply(success -> {
                            if (!success) {
                                return null;
                            }
                            Map<String, Object> payload = buildBackupPayload(resolvedPlotId, snapshotId, "restore", restorePointId);
                            auditLogService.record(resolvedPlotId, "backup_restore", actorName, payload);
                            return payload;
                        }));
    }

    private Map<String, Object> buildBackupPayload(String plotId, String snapshotId, String note, String rollbackSnapshotId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("version", VERSION);
        payload.put("plotId", plotId);
        payload.put("snapshotId", snapshotId);
        payload.put("createdAt", Instant.now().toString());
        payload.put("note", note);
        payload.put("rollbackSnapshotId", rollbackSnapshotId);
        payload.put("reversible", rollbackSnapshotId != null);
        return payload;
    }
}
