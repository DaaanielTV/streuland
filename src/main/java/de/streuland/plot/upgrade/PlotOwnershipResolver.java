package de.streuland.plot.upgrade;

import java.util.UUID;

public interface PlotOwnershipResolver {
    boolean isOwner(String plotId, UUID playerId);
}
