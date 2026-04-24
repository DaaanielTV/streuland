// Facade to expose a minimal API for invites with switchable gateways
let InviteServiceClass;
try {
  // Prefer SQLite gateway if available and environment flag is set
  if (process.env.USE_SQLITE_GATEWAY === '1') {
    const SqliteInvitationGatewaySqlite = require('./SqliteInvitationGatewaySqlite');
    InviteServiceClass = class InviteService {
      constructor(dbPath) {
        this.useSqlite = true;
        this.gateway = new SqliteInvitationGatewaySqlite(dbPath);
      }
      async create(invite) { return this.gateway.create(invite); }
      async list(filter) { return this.gateway.list(filter || {}); }
      async update(code, updates) { return this.gateway.update(code, updates); }
      async revoke(code) { return this.gateway.revoke(code); }
      async export() { return this.gateway.export(); }
      async find(code) {
        const items = await this.export();
        return items.find(i => i.code === code) || null;
      }
    };
  }
} catch {
  // fall back to in-memory if sqlite lib is unavailable
}

if (!InviteServiceClass) {
  const InviteRepository = require('./InviteRepository');
  InviteServiceClass = class InviteService {
    constructor() {
      this.useSqlite = false;
      this.repo = new InviteRepository();
    }
    async create(invite) { return this.repo.create(invite); }
    async list(filter) { return this.repo.list(filter || {}); }
    async update(code, updates) { return this.repo.update(code, updates); }
    async revoke(code) { return this.repo.revoke(code); }
    async export() { return this.repo.export(); }
    async find(code) { return this.repo.find(code); }
  };
}

module.exports = { InviteService: InviteServiceClass };
