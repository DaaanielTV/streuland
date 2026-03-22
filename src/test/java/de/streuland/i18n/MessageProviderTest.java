package de.streuland.i18n;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageProviderTest {

    @Test
    void resolvesByLocaleAndPlaceholders() {
        Map<String, Map<String, String>> bundles = new HashMap<>();
        Map<String, String> en = new HashMap<>();
        en.put("greet", "Hello {0}");
        Map<String, String> de = new HashMap<>();
        de.put("greet", "Hallo {0}");
        bundles.put("en", en);
        bundles.put("de", de);

        MessageProvider provider = new MessageProvider("en", bundles);

        assertEquals("Hallo Alex", provider.t("de", "greet", "Alex"));
        assertEquals("Hello Alex", provider.t("en", "greet", "Alex"));
    }

    @Test
    void fallsBackToServerLocaleAndSupportsPlayerLocale() {
        Map<String, Map<String, String>> bundles = new HashMap<>();
        bundles.put("en", Collections.singletonMap("welcome", "Welcome"));
        bundles.put("de", Collections.singletonMap("welcome", "Willkommen"));

        MessageProvider provider = new MessageProvider("en", bundles);
        UUID player = UUID.randomUUID();

        assertEquals("Welcome", provider.t(player, "welcome"));
        provider.setPlayerLocale(player, "de");
        assertEquals("Willkommen", provider.t(player, "welcome"));
        provider.setServerLocale("de");
        assertEquals("Willkommen", provider.t(UUID.randomUUID(), "welcome"));
    }
}
