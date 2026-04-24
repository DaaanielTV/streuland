// Test MVP SignupService with injected in-memory repo
const SignupService = require('../src/auth/SignupService');
const InviteCode = require('../src/invitations/InviteCode');

class InMemoryRepo {
  constructor() {
    this.invites = new Map();
  }
  async find(code) {
    return this.invites.get(code) || null;
  }
  async update(code, updates) {
    const v = this.invites.get(code);
    if (!v) return null;
    const updated = Object.assign({}, v, updates);
    this.invites.set(code, updated);
    return updated;
  }
  async create(inv) {
    this.invites.set(inv.code, inv);
    return inv;
  }
}

test('SignupService signUpWithCode happy path', async () => {
  const repo = new InMemoryRepo();
  const invite = new InviteCode({ code: 'TEST123', owner: 'admin', roles: ['player'] });
  await repo.create(invite);
  const svc = new SignupService(repo);
  // run signUpWithCode with code and password
  const res = await svc.signUpWithCode('TEST123', 'secret');
  expect(res).toHaveProperty('token');
  expect(res).toHaveProperty('hashedPassword');
});
