package de.streuland.auth;

import java.time.Instant;

public class User {
    public String id;
    public String username;
    public String email;
    public String serverId;
    public String createdAt;
    public String inviteCodeId;
    public String salt;

    public User() {}

    public static User create(String id, String username, String email, String serverId, String inviteCodeId) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.email = email;
        u.serverId = serverId;
        u.inviteCodeId = inviteCodeId;
        u.createdAt = Instant.now().toString();
        u.salt = null;
        return u;
    }
}
