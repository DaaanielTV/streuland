// Facade to expose a minimal API for invites using a SQLite-backed gateway
const SqliteInvitationGatewaySqlite = require('./SqliteInvitationGatewaySqlite');

class InviteService {
  constructor(dbPath) {
    this.gateway = new SqliteInvitationGatewaySqlite(dbPath);
  }

  async create(invite) {
    return this.gateway.create(invite);
  }

  async list(filter) {
    return this.gateway.list(filter || {});
  }

  async update(code, updates) {
    return this.gateway.update(code, updates);
  }

  async revoke(code) {
    return this.gateway.revoke(code);
  }

  async export() {
    return this.gateway.export();
  }
}

module.exports = { InviteService };
