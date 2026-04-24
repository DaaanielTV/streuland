// Test MVP validate-invitation API handler (mocked request/response)
const validateInvitation = require('../src/api/auth/validate-invitation');
const InviteRepository = require('../src/invitations/InviteRepository');

test('validate invitation exists', async () => {
  const repo = new InviteRepository();
  const code = 'VALID123';
  await repo.create({ code, owner: 'admin', roles: ['player'] });
  // Simple mock req/res
  const req = { query: { code } };
  const res = {
    statusCode: 0,
    _body: null,
    end: function (payload) { this._body = payload; }
  };
  await validateInvitation(req, res);
  // expect status 200
  expect(res.statusCode).toBe(200);
  const body = JSON.parse(res._body);
  expect(body).toHaveProperty('valid', true);
});
