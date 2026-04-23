package de.streuland.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.streuland.invite.InvitationGateway;
import de.streuland.invite.InvitationCode;
import javax.xml.bind.DatatypeConverter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.security.SecureRandom;
import de.streuland.security.JwtUtil;
import de.streuland.auth.UserGateway;

/** Minimal Sign-Up endpoint: POST /auth/signup-with-code
    Payload: { username, email, password, invitation_code }
 */
public class SignUpWithCodeHandler implements HttpHandler {
    private final InvitationGateway invitationGateway;
    private final UserGateway userGateway;
    private final Gson gson = new Gson();
    private final String jwtSecret;

    public SignUpWithCodeHandler(InvitationGateway invitationGateway, UserGateway userGateway) {
        this(invitationGateway, userGateway, null);
    }

    public SignUpWithCodeHandler(InvitationGateway invitationGateway, UserGateway userGateway, String jwtSecret) {
        this.invitationGateway = invitationGateway;
        this.userGateway = userGateway;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
        String username = payload.has("username") ? payload.get("username").getAsString() : null;
        String email = payload.has("email") ? payload.get("email").getAsString() : null;
        String password = payload.has("password") ? payload.get("password").getAsString() : null;
        String inviteCode = payload.has("invitation_code") ? payload.get("invitation_code").getAsString() : null;

        if (username == null || email == null || password == null || inviteCode == null) {
            respond(exchange, 400, error("invalid_request", "Missing required fields"));
            return;
        }

        // Validate Invitation
        InvitationCode code = invitationGateway.getByCode(inviteCode);
        if (code == null || code.isRevoked) {
            respond(exchange, 400, error("invalid_invitation", "Invitation code not valid"));
            return;
        }
        // expiry check
        if (code.expiresAt != null) {
            Instant exp = Instant.parse(code.expiresAt);
            if (Instant.now().isAfter(exp)) {
                respond(exchange, 400, error("expired_invitation", "Invitation has expired"));
                return;
            }
        }
        if (code.maxUses != null && code.uses >= code.maxUses) {
            respond(exchange, 400, error("max_uses_reached", "Invitation has reached its usage limit"));
            return;
        }

        // Password hashing (salted SHA-256 for MVP)
        String salt = generateSalt();
        String passwordHash = sha256(salt + password);

        // Create user
        String serverId = code.serverId;
        try {
            String passwordSalt = salt;
            com.streuland.auth.User newUser = userGateway.createUser(username, email, passwordHash, passwordSalt, code.id, serverId);
            // Increment uses of invitation
            invitationGateway.incrementUses(code.id);
            String token = JwtUtil.createToken(newUser.id, 24 * 60 * 60 * 1000L, getJwtSecret());
            Map<String, String> resp = new HashMap<>();
            resp.put("userId", newUser.id);
            resp.put("token", token);
            respond(exchange, 201, gson.toJson(resp));
        } catch (Exception e) {
            respond(exchange, 500, error("signup_error", e.getMessage()));
        }
    }

    private String getJwtSecret() {
        String s = null;
        if (jwtSecret != null && !jwtSecret.isEmpty()) {
            s = jwtSecret;
        } else {
            s = System.getProperty("streuland.jwt.secret");
            if (s == null || s.isEmpty()) {
                s = System.getenv("STREULAND_JWT_SECRET");
            }
            if (s == null || s.isEmpty()) {
                s = "default-secret";
            }
        }
        return s;
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return DatatypeConverter.printHexBinary(salt).toLowerCase();
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String error(String code, String message) {
        Map<String, String> m = new HashMap<>();
        m.put("error", code);
        m.put("message", message);
        return gson.toJson(m);
    }
}
