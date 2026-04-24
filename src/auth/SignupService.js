// MVP Sign-Up service using in-memory InviteRepository and JwtUtil
const InviteRepository = require('../invitations/InviteRepository');
const InviteCode = require('../invitations/InviteCode');
const { hashPasswordSHA256, signPayload } = require('../utils/JwtUtil');

class SignupService {
  constructor(repo) {
    // allow injection for tests or alternate storage
    this.repo = repo || new InviteRepository();
  }

  async signUpWithCode(code, password) {
    const invite = await this.repo.find(code);
    if (!invite) throw new Error('InviteNotFound');
    // naive: mark used and return a JWT for the user
    const nowHashed = hashPasswordSHA256(password, code);
    // update uses
    invite.uses = (invite.uses || 0) + 1;
    await this.repo.update(code, { uses: invite.uses });
    const payload = { sub: code, owner: invite.owner, roles: invite.roles || [] };
    const token = signPayload(payload, process.env.JWT_SECRET || 'dev-secret', { expiresIn: '1h' });
    return { token, hashedPassword: nowHashed };
  }
}

module.exports = SignupService;
