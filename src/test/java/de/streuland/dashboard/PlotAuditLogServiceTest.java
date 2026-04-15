package de.streuland.dashboard;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotAuditLogServiceTest {

    @Test
    void filtersAuditEventsByActionActorAndSearch() {
        PlotAuditLogService service = new PlotAuditLogService(200);
        service.record("plot_1", "backup_create", "admin", Collections.singletonMap("snapshotId", "snap_1"));
        service.record("plot_1", "trust", "mod-team", Collections.singletonMap("target", "player_a"));
        service.record("plot_2", "upgrade_change", "system", Collections.singletonMap("toLevel", 2));

        List<PlotAuditEvent> filtered = service.listFiltered("plot_1", "trust", "mod", "player_a", 20);

        assertEquals(1, filtered.size());
        assertEquals("trust", filtered.get(0).getAction());
        assertEquals("plot_1", filtered.get(0).getPlotId());
    }
}
