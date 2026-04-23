package de.streuland.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Validate invitation endpoint: GET /auth/validate-invitation?code=XYZ */
public class ValidateInvitationHandler implements HttpHandler {
    private final de.streuland.invite.InvitationGateway invitationGateway;
    private final Gson gson = new Gson();

    public ValidateInvitationHandler(de.streuland.invite.InvitationGateway invitationGateway) {
        this.invitationGateway = invitationGateway;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String codeParam = null;
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String p : raw.split("&")) {
                if (p.startsWith("code=")) {
                    codeParam = p.substring("code=".length());
                    break;
                }
            }
        }
        if (codeParam == null) {
            String err = gson.toJson(new HashMap<String, String>() {{ put("error", "missing_code"); put("message", "Invitation code required"); }});
            respond(exchange, 400, err);
            return;
        }
        de.streuland.invite.InvitationCode c = invitationGateway.getByCode(codeParam);
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        if (c == null || c.isRevoked) {
            resp.put("status", "invalid");
            respond(exchange, 200, gson.toJson(resp));
            return;
        }
        if (c.expiresAt != null) {
            Instant exp = Instant.parse(c.expiresAt);
            resp.put("expiresAt", c.expiresAt);
            resp.put("valid", Instant.now().isBefore(exp) && (c.maxUses == null || c.uses < c.maxUses) && !c.isRevoked);
        } else {
            resp.put("expiresAt", null);
            resp.put("valid", c.maxUses == null || c.uses < c.maxUses) && !c.isRevoked;
        }
        resp.put("serverId", c.serverId);
        resp.put("allowedRoles", c.allowedRoles);
        respond(exchange, 200, gson.toJson(resp));
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
