package de.streuland.market;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pure filtering/sorting service for market listings.
 * Keeps query rules out of command transport code.
 */
public class PlotMarketFilter {

    public record Query(Double minPrice, Double maxPrice, String plotIdContains, Sort sort, int limit) {
        public Query {
            limit = Math.max(1, Math.min(100, limit));
        }
    }

    public enum Sort {
        PRICE_ASC,
        PRICE_DESC,
        NEWEST,
        OLDEST;

        public static Sort fromInput(String raw) {
            if (raw == null) {
                return PRICE_ASC;
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "price_desc", "desc", "high" -> PRICE_DESC;
                case "newest", "new" -> NEWEST;
                case "oldest", "old" -> OLDEST;
                default -> PRICE_ASC;
            };
        }
    }

    public List<MarketListing> apply(List<MarketListing> source, Query query) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(query, "query");

        return source.stream()
                .filter(listing -> query.minPrice() == null || listing.getPrice() >= query.minPrice())
                .filter(listing -> query.maxPrice() == null || listing.getPrice() <= query.maxPrice())
                .filter(listing -> query.plotIdContains() == null
                        || query.plotIdContains().isBlank()
                        || listing.getPlotId().toLowerCase(Locale.ROOT).contains(query.plotIdContains().toLowerCase(Locale.ROOT)))
                .sorted(comparator(query.sort()))
                .limit(query.limit())
                .collect(Collectors.toList());
    }

    private Comparator<MarketListing> comparator(Sort sort) {
        return switch (sort) {
            case PRICE_DESC -> Comparator.comparingDouble(MarketListing::getPrice).reversed()
                    .thenComparing(MarketListing::getPlotId);
            case NEWEST -> Comparator.comparingLong(MarketListing::getCreatedAt).reversed()
                    .thenComparing(MarketListing::getPlotId);
            case OLDEST -> Comparator.comparingLong(MarketListing::getCreatedAt)
                    .thenComparing(MarketListing::getPlotId);
            case PRICE_ASC -> Comparator.comparingDouble(MarketListing::getPrice)
                    .thenComparing(MarketListing::getPlotId);
        };
    }
}
