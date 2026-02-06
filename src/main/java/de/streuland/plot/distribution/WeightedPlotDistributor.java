package de.streuland.plot.distribution;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Selects the best plot location using weighted scoring with async execution.
 */
public class WeightedPlotDistributor {
    private final PlotLocationScorer scorer;

    public WeightedPlotDistributor(PlotLocationScorer scorer) {
        this.scorer = scorer;
    }

    public CompletableFuture<Optional<CandidatePlotLocation>> selectBestAsync(List<CandidatePlotLocation> candidates) {
        return CompletableFuture.supplyAsync(() -> candidates.stream()
            .max(Comparator.comparingDouble(scorer::score)));
    }
}
