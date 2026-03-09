package de.streuland.warp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class PlotEconomyHookTest {

    @Test
    public void chargeAndPayoutShouldUseEconomyProvider() {
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        OfflinePlayer owner = mock(OfflinePlayer.class);
        when(economy.withdrawPlayer(player, 25.0)).thenReturn(new EconomyResponse(25.0, 75.0, EconomyResponse.ResponseType.SUCCESS, "ok"));

        PlotEconomyHook hook = new PlotEconomyHook(economy);
        Assert.assertTrue(hook.charge(player, 25.0));

        hook.payout(owner, 25.0);
        verify(economy).depositPlayer(owner, 25.0);
    }
}
