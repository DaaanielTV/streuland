// Minimal placeholder for SQLite-backed user gateway
class SqliteUserGateway {
  constructor(db) {
    this.db = db;
  }

  async findByInviteCode(code) {
    // placeholder
    return null;
  }
}

module.exports = SqliteUserGateway;
