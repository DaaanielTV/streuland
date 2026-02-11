package de.streuland.district;

import de.streuland.plot.Plot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Builds clusters of neighboring plots to form districts.
 */
public class DistrictClusterService {
    public List<Set<Plot>> clusterPlots(Collection<Plot> plots) {
        List<Set<Plot>> clusters = new ArrayList<>();
        Set<Plot> visited = new HashSet<>();

        for (Plot plot : plots) {
            if (visited.contains(plot)) {
                continue;
            }
            Set<Plot> cluster = new HashSet<>();
            Queue<Plot> queue = new ArrayDeque<>();
            queue.add(plot);
            visited.add(plot);

            while (!queue.isEmpty()) {
                Plot current = queue.poll();
                cluster.add(current);
                for (Plot other : plots) {
                    if (!visited.contains(other) && areNeighbors(current, other)) {
                        visited.add(other);
                        queue.add(other);
                    }
                }
            }

            clusters.add(cluster);
        }
        return clusters;
    }

    private boolean areNeighbors(Plot a, Plot b) {
        boolean overlapZ = a.getMinZ() < b.getMaxZ() && a.getMaxZ() > b.getMinZ();
        boolean overlapX = a.getMinX() < b.getMaxX() && a.getMaxX() > b.getMinX();

        boolean touchingX = a.getMaxX() == b.getMinX() || b.getMaxX() == a.getMinX();
        boolean touchingZ = a.getMaxZ() == b.getMinZ() || b.getMaxZ() == a.getMinZ();

        return (touchingX && overlapZ) || (touchingZ && overlapX);
    }
}
