// Integration-like test for SignupService with in-memory repo
const SignupService = require('../src/auth/SignupService');
const InviteCode = require('../src/invitations/InviteCode');
const InMemoryRepo = require('../tests/Placeholders');

// Simple mock repo if placeholder exists, otherwise reuse in-memory implementation
class SimpleRepo {
  constructor() { this.map = new Map(); }
  async find(code) { return this.map.get(code) || null; }
  async create(inv) { this.map.set(inv.code, inv); return inv; }
  async update(code, updates) { const v = this.map.get(code); if(!v) return null; const u = Object.assign({}, v, updates); this.map.set(code, u); return u; }
  async list() { return Array.from(this.map.values()); }
  async export() { return Array.from(this.map.values()); }
}

test('SignupService integration with in-memory repo', async () => {
  const repo = new SimpleRepo();
  const invite = new InviteCode({ code: 'INTEG1', owner: 'admin', roles: ['player'] });
  await repo.create(invite);
  const svc = new SignupService(repo);
  const res = await svc.signUpWithCode('INTEG1', 'pw123');
  expect(res).toHaveProperty('token');
});
