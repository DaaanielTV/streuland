// Lightweight placeholder for SQLite-backed invitation gateway (MVP stub)
// In a real MVP this would implement CRUD operations against a SQLite DB.
class SqliteInvitationGateway {
  constructor(db) {
    this.db = db; // placeholder, e.g., sqlite3.Database
  }

  async create(invite) {
    // placeholder: would insert into DB and return created invite
    return invite;
  }

  async list(filter = {}) {
    // placeholder: would query DB with filters
    return [];
  }

  async update(code, updates) {
    // placeholder
    return { code, ...updates };
  }

  async revoke(code) {
    // placeholder
    return { code, revoked: true };
  }

  async export() {
    // placeholder
    return [];
  }
}

module.exports = SqliteInvitationGateway;
