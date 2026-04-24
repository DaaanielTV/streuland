// Minimal in-memory Invite repository for MVP. Persistency to SQLite comes later.
class InviteRepository {
  constructor() {
    this.invites = new Map(); // code -> InviteCode-like object
  }

  async create(invite) {
    this.invites.set(invite.code, invite);
    return invite;
  }

  async find(code) {
    return this.invites.get(code) || null;
  }

  async list({ owner } = {}) {
    const arr = Array.from(this.invites.values());
    if (!owner) return arr;
    return arr.filter((i) => i.owner === owner);
  }

  async update(code, updates) {
    const existing = this.invites.get(code);
    if (!existing) return null;
    const updated = Object.assign({}, existing, updates);
    this.invites.set(code, updated);
    return updated;
  }

  async revoke(code) {
    return this.invites.delete(code);
  }
}

module.exports = InviteRepository;
