# Security Review Findings (Strict)

Date: 2026-05-04
Scope: `src/` + `pom.xml`

## 1) Insecure default JWT secrets enable token forgery (Critical)
- Java signup flow falls back to a static secret (`"default-secret"`) if no configured secret exists.
- Node signup flow falls back to `"dev-secret"`.
- An attacker who knows these defaults can mint valid tokens anywhere this fallback is active.

Evidence:
- `src/main/java/de/streuland/auth/SignUpWithCodeHandler.java` (`getJwtSecret()` fallback).
- `src/auth/SignupService.js` (`process.env.JWT_SECRET || 'dev-secret'`).

## 2) Password storage uses salted SHA-256 instead of password KDF (High)
- Passwords are hashed with `SHA-256(salt + password)` directly.
- This is not password-hard and is vulnerable to fast offline cracking if DB is leaked.
- Should use Argon2id/scrypt/bcrypt/PBKDF2-HMAC-SHA256 with strong work factor and per-user salt.

Evidence:
- `src/main/java/de/streuland/auth/SignUpWithCodeHandler.java` (`sha256(salt + password)`).
- `src/auth/SignupService.js` (`hashPasswordSHA256`).

## 3) Invitation max-uses check is vulnerable to race condition / overuse (High)
- Validation reads invitation (`uses`, `maxUses`), then user is created, then `incrementUses` runs in a separate step.
- Concurrent requests can pass validation simultaneously and exceed `maxUses`.
- No transactional `UPDATE ... WHERE uses < max_uses` guard is used.

Evidence:
- `src/main/java/de/streuland/auth/SignUpWithCodeHandler.java` (check-before-increment flow).
- `src/main/java/de/streuland/invite/SqliteInvitationGateway.java` (`incrementUses` unguarded `uses = uses + 1`).
- `src/invitations/InviteRepository.js` and `src/invitations/SqliteInvitationGateway.js` (non-atomic read/modify/write pattern).

## 4) Internal exception messages are returned to clients (Medium)
- Signup handler returns `e.getMessage()` directly in JSON response.
- This can leak DB constraint names, table details, or internal state useful for exploitation.

Evidence:
- `src/main/java/de/streuland/auth/SignUpWithCodeHandler.java` (`respond(... error("signup_error", e.getMessage()))`).

## 5) Invitation code generation uses `Math.random()` (Medium)
- Invitation codes are generated with non-cryptographic RNG.
- Predictable tokens are feasible under some conditions; invite codes should be generated via CSPRNG.

Evidence:
- `src/main/java/de/streuland/invite/SqliteInvitationGateway.java` (`generateCode()` uses `Math.random()`).

## 6) Dependency risk: outdated SnakeYAML 1.26 (High)
- `snakeyaml:1.26` is old and has had multiple security advisories in the 1.x line.
- For OSS release, pin to a currently supported version and run dependency scanning (OWASP Dependency-Check / osv-scanner / Snyk).

Evidence:
- `pom.xml` declares `org.yaml:snakeyaml:1.26`.

## 7) GPL-3.0 compliance blocker if distributed with current dependency set (High, legal/compliance)
- `sqlite-jdbc` bundles SQLite, which is Public Domain; GPL projects generally can include this.
- **But** GPL-3.0 compliance still requires preserving third-party license notices and corresponding attribution/disclaimer texts for bundled dependencies.
- No dedicated THIRD_PARTY_NOTICES / NOTICE aggregation is present in repository root.
- Before open-sourcing under GPL-3.0, you should add third-party notices and verify every transitive dependency license compatibility.

Evidence:
- `pom.xml` lists external libraries.
- Repository root currently lacks an explicit third-party notices file.

