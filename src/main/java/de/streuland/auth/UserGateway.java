package de.streuland.auth;

public interface UserGateway {
    de.streuland.auth.User createUser(String username, String email, String passwordHash, String passwordSalt, String inviteCodeId, String serverId);
}
