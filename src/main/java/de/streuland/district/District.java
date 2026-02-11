package de.streuland.district;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a cluster of neighboring plots forming a district.
 */
public class District {
    private final String id;
    private String name;
    private final Set<String> plotIds;
    private DistrictLevel level;
    private final DistrictProgress progress;
    private final long createdAt;

    public District(String id, String name, Set<String> plotIds, DistrictLevel level, long createdAt) {
        this.id = id;
        this.name = name;
        this.plotIds = new HashSet<>(plotIds);
        this.level = level;
        this.progress = new DistrictProgress();
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getPlotIds() {
        return Collections.unmodifiableSet(plotIds);
    }

    public void addPlot(String plotId) {
        plotIds.add(plotId);
    }

    public void removePlot(String plotId) {
        plotIds.remove(plotId);
    }

    public DistrictLevel getLevel() {
        return level;
    }

    public void setLevel(DistrictLevel level) {
        this.level = level;
    }

    public DistrictProgress getProgress() {
        return progress;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
