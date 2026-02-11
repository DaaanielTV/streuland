package de.streuland.plot.distribution;

/**
 * Scores candidate plot locations for weighted selection.
 */
public interface PlotLocationScorer {
    double score(CandidatePlotLocation candidate);
}
