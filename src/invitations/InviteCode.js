// Minimal InviteCode model for MVP (SQLite-backed persistence later)
class InviteCode {
  constructor({ code, owner, expiresAt = null, maxUses = null, uses = 0, roles = [] } = {}) {
    this.code = code;
    this.owner = owner;
    this.expiresAt = expiresAt; // ISO string or null
    this.maxUses = maxUses; // number or null
    this.uses = uses; // number of times used
    this.roles = roles;
  }

  isExpired(now = new Date()) {
    if (!this.expiresAt) return false;
    return new Date(this.expiresAt) < now;
  }

  canUse() {
    if (this.maxUses != null && this.uses >= this.maxUses) return false;
    return !this.isExpired();
  }
}

module.exports = InviteCode;
