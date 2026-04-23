package de.streuland.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import de.streuland.invite.InvitationCode;
import de.streuland.invite.InvitationGateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignUpFlowTest {
    private HttpExchange exchangeFromBody(String body) {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        Headers reqHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(reqHeaders);
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        Headers respHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(respHeaders);
        return exchange;
    }

    @Test
    void signup_with_valid_invite_returns_user_and_jwt() throws Exception {
        InvitationGateway inviteGate = Mockito.mock(InvitationGateway.class);
        UserGateway userGate = Mockito.mock(UserGateway.class);

        // Prepare invite code
        InvitationCode code = new InvitationCode();
        code.id = "invite-1";
        code.serverId = "server-1";
        code.expiresAt = null;
        code.maxUses = null;
        code.isRevoked = false;
        when(inviteGate.getByCode("INVITE")).thenReturn(code);

        // Prepare new user
        User user = User.create("user-1", "alice", "alice@example.com", "server-1", code.id);
        when(userGate.createUser(eq("alice"), eq("alice@example.com"), anyString(), anyString(), eq(code.id), eq("server-1"))).thenReturn(user);

        // Build handler with a secret
        SignUpWithCodeHandler handler = new SignUpWithCodeHandler(inviteGate, userGate, "test-secret");

        String json = "{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"pw123\",\"invitation_code\":\"INVITE\"}";
        HttpExchange exchange = exchangeFromBody(json);

        handler.handle(exchange);

        // Verify response
        java.io.ByteArrayOutputStream bos = (java.io.ByteArrayOutputStream) exchange.getResponseBody();
        String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
        assertTrue(obj.has("userId"));
        assertTrue(obj.has("token"));

        // Ensure invite usage is incremented
        verify(inviteGate).incrementUses(code.id);
    }

    @Test
    void signup_with_invalid_invite_fails() throws Exception {
        InvitationGateway inviteGate = Mockito.mock(InvitationGateway.class);
        UserGateway userGate = Mockito.mock(UserGateway.class);

        when(inviteGate.getByCode("INVITE")).thenReturn(null);
        SignUpWithCodeHandler handler = new SignUpWithCodeHandler(inviteGate, userGate, "test-secret");

        String json = "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"password\":\"pw123\",\"invitation_code\":\"INVITE\"}";
        HttpExchange exchange = exchangeFromBody(json);
        handler.handle(exchange);

        java.io.ByteArrayOutputStream bos = (java.io.ByteArrayOutputStream) exchange.getResponseBody();
        String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
        assertEquals("invalid_invitation", obj.get("error").getAsString());
    }
}
