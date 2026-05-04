package de.streuland.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.streuland.invite.InvitationGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.streuland.security.JwtUtil;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SignUpWithCodeHandler implements HttpHandler {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int PBKDF2_KEY_LENGTH = 256;

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
        JsonObject payload = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        String username = payload.has("username") ? payload.get("username").getAsString() : null;
        String email = payload.has("email") ? payload.get("email").getAsString() : null;
        String password = payload.has("password") ? payload.get("password").getAsString() : null;
        String inviteCode = payload.has("invitation_code") ? payload.get("invitation_code").getAsString() : null;

        if (username == null || email == null || password == null || inviteCode == null) {
            respond(exchange, 400, error("invalid_request", "Missing required fields"));
            return;
        }

        if (!invitationGateway.consumeIfValid(inviteCode, Instant.now())) {
            respond(exchange, 400, error("invalid_invitation", "Invitation code not valid"));
            return;
        }

        try {
            String salt = generateSalt();
            String passwordHash = hashPassword(password.toCharArray(), salt);
            com.streuland.auth.User newUser = userGateway.createUser(username, email, passwordHash, salt, inviteCode, null);
            String token = JwtUtil.createToken(newUser.id, 24 * 60 * 60 * 1000L, getJwtSecret());
            Map<String, String> resp = new HashMap<>();
            resp.put("userId", newUser.id);
            resp.put("token", token);
            respond(exchange, 201, gson.toJson(resp));
        } catch (Exception e) {
            respond(exchange, 500, error("signup_error", "Sign-up failed"));
        }
    }

    private String getJwtSecret() {
        String s = (jwtSecret != null && !jwtSecret.isEmpty()) ? jwtSecret : System.getProperty("streuland.jwt.secret");
        if (s == null || s.isEmpty()) s = System.getenv("STREULAND_JWT_SECRET");
        if (s == null || s.trim().isEmpty()) throw new IllegalStateException("Missing JWT secret configuration");
        return s;
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return DatatypeConverter.printHexBinary(salt).toLowerCase();
    }

    private String hashPassword(char[] password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, DatatypeConverter.parseHexBinary(saltHex), PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return DatatypeConverter.printHexBinary(skf.generateSecret(spec).getEncoded()).toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
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
