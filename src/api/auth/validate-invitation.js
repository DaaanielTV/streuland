// MVP endpoint: validate an invitation code
const InviteRepository = require('../../invitations/InviteRepository');

module.exports = async function validateInvitation(req, res) {
  const { code } = req.query || {};
  const repo = new InviteRepository();
  const invite = await repo.find(code);
  if (!invite) {
    res.statusCode = 404;
    return res.end(JSON.stringify({ valid: false }));
  }
  res.statusCode = 200;
  res.end(JSON.stringify({ valid: true, invite }));
};
