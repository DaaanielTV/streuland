// Minimal API Router scaffold for Admin API: Create/List/Update/Revoke/Export
// Uses the MVP InviteService (SQLite gateway wrapper) when available.
const { InviteService } = require('../../../invitations/index');
module.exports = function invitationsRouter(dbPath) {
  // Simple structure to show endpoints; actual HTTP server wiring is left for integration.
  const router = {
    post: (path, handler) => {},
    get: (path, handler) => {},
    put: (path, handler) => {},
  };
  const service = new InviteService(dbPath);

  // Example endpoint signatures (no-op implementations)
  router.post('/api/dashboard/ops/invitations', async (req, res) => {
    const invite = req.body || { code: 'DEMO' };
    const created = await service.create(invite);
    res.statusCode = 201;
    res.end(JSON.stringify(created));
  });
  router.get('/api/dashboard/ops/invitations', async (req, res) => {
    const list = await service.export();
    res.statusCode = 200;
    res.end(JSON.stringify(list));
  });

  return router;
};
