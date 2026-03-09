package de.streuland.warp;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class CooldownManagerTest {

    @Test
    public void setCooldownShouldBlockThenExpire() throws Exception {
        CooldownManager manager = new CooldownManager();
        UUID playerId = UUID.randomUUID();

        manager.setCooldown(playerId, 100L);
        Assert.assertTrue(manager.isOnCooldown(playerId));

        Thread.sleep(150L);
        Assert.assertFalse(manager.isOnCooldown(playerId));
    }
}
