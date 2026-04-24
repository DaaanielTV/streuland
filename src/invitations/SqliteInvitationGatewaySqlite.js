// Real SQLite-backed invitation gateway (minimal MVP implementation)
// Requires sqlite3 package. This is a simple wrapper around a single table invites.
"use strict";
const sqlite3 = require('sqlite3').verbose();
const path = require('path');

class SqliteInvitationGatewaySqlite {
  constructor(dbPath) {
    const dbFile = dbPath || path.resolve(__dirname, '../../invites.sqlite3');
    this.db = new sqlite3.Database(dbFile);
    this._init();
  }

  _init() {
    const create = `CREATE TABLE IF NOT EXISTS invites (
      code TEXT PRIMARY KEY,
      owner TEXT,
      expiresAt TEXT,
      maxUses INTEGER,
      uses INTEGER,
      roles TEXT
    )`;
    this.db.run(create);
  }

  async create(invite) {
    const sql = `INSERT OR REPLACE INTO invites(code, owner, expiresAt, maxUses, uses, roles) VALUES(?,?,?,?,?,?)`;
    const roles = Array.isArray(invite.roles) ? invite.roles.join(',') : '';
    return new Promise((resolve, reject) => {
      this.db.run(sql, [invite.code, invite.owner, invite.expiresAt, invite.maxUses, invite.uses, roles], function(err) {
        if (err) return reject(err);
        resolve(invite);
      });
    });
  }

  async list(filter = {}) {
    const ownerClause = filter.owner ? 'WHERE owner = ?' : '';
    const sql = `SELECT * FROM invites ${ownerClause}`;
    const params = filter.owner ? [filter.owner] : [];
    return new Promise((resolve, reject) => {
      this.db.all(sql, params, (err, rows) => {
        if (err) return reject(err);
        const res = rows.map((r) => ({ ...r, roles: (r.roles || '').split(',').filter(Boolean) }));
        resolve(res);
      });
    });
  }

  async update(code, updates) {
    const sets = [];
    const vals = [];
    if (updates.owner !== undefined) { sets.push('owner = ?'); vals.push(updates.owner); }
    if (updates.expiresAt !== undefined) { sets.push('expiresAt = ?'); vals.push(updates.expiresAt); }
    if (updates.maxUses !== undefined) { sets.push('maxUses = ?'); vals.push(updates.maxUses); }
    if (updates.uses !== undefined) { sets.push('uses = ?'); vals.push(updates.uses); }
    if (updates.roles !== undefined) { sets.push('roles = ?'); vals.push(Array.isArray(updates.roles) ? updates.roles.join(',') : updates.roles); }
    vals.push(code);
    const sql = `UPDATE invites SET ${sets.join(', ')} WHERE code = ?`;
    return new Promise((resolve, reject) => {
      this.db.run(sql, vals, function(err) {
        if (err) return reject(err);
        resolve({ code, ...updates });
      });
    });
  }

  async revoke(code) {
    const sql = `DELETE FROM invites WHERE code = ?`;
    return new Promise((resolve, reject) => {
      this.db.run(sql, [code], function(err) {
        if (err) return reject(err);
        resolve(true);
      });
    });
  }

  async export() {
    const sql = `SELECT * FROM invites`;
    return new Promise((resolve, reject) => {
      this.db.all(sql, [], (err, rows) => {
        if (err) return reject(err);
        const res = rows.map((r) => ({ ...r, roles: (r.roles || '').split(',').filter(Boolean) }));
        resolve(res);
      });
    });
  }
}

module.exports = SqliteInvitationGatewaySqlite;
