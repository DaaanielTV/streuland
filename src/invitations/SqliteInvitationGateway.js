// Simple file-based persistence to simulate SQLite MVP for InviteGateways
// This acts as a drop-in placeholder until a real SQLite layer is wired.
const fs = require('fs');
const path = require('path');

class SqliteInvitationGateway {
  constructor(dbPath) {
    // dbPath is optional; default to invites.json in project root
    this.dbPath = dbPath || path.resolve(__dirname, '../../invites.json');
    this._ensureStore();
  }

  _ensureStore() {
    try {
      if (!fs.existsSync(this.dbPath)) {
        fs.writeFileSync(this.dbPath, JSON.stringify({ invites: [] }, null, 2));
      }
      this._loadAll();
    } catch (e) {
      // best-effort defensive: keep in-memory if file IO fails
      this._memory = new Map();
    }
  }

  _loadAll() {
    const raw = fs.readFileSync(this.dbPath, 'utf8');
    const data = JSON.parse(raw);
    this._memory = new Map();
    (data.invites || []).forEach((inv) => this._memory.set(inv.code, inv));
  }

  _persistAll() {
    const invites = Array.from(this._memory.values());
    const payload = { invites };
    fs.writeFileSync(this.dbPath, JSON.stringify(payload, null, 2));
  }

  async create(invite) {
    this._memory.set(invite.code, invite);
    this._persistAll();
    return invite;
  }

  async list(filter = {}) {
    const items = Array.from(this._memory.values());
    if (!filter.owner) return items;
    return items.filter((i) => i.owner === filter.owner);
  }

  async update(code, updates) {
    const existing = this._memory.get(code);
    if (!existing) return null;
    const updated = Object.assign({}, existing, updates);
    this._memory.set(code, updated);
    this._persistAll();
    return updated;
  }

  async revoke(code) {
    const existed = this._memory.has(code);
    this._memory.delete(code);
    if (existed) this._persistAll();
    return existed;
  }

  async export() {
    return Array.from(this._memory.values());
  }
}

module.exports = SqliteInvitationGateway;
