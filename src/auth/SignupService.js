// MVP Sign-Up service using in-memory InviteRepository and JwtUtil
const InviteRepository = require('../invitations/InviteRepository');
const InviteCode = require('../invitations/InviteCode');
const { hashPasswordPBKDF2, signPayload } = require('../utils/JwtUtil');

function requiredJwtSecret() {
  if (!process.env.JWT_SECRET || !process.env.JWT_SECRET.trim()) {
    throw new Error('MissingJWTSecret');
  }
  return process.env.JWT_SECRET;
}

class SignupService {
  constructor(repo) {
    // allow injection for tests or alternate storage
    this.repo = repo || new InviteRepository();
  }

  async signUpWithCode(code, password) {
    const invite = await this.repo.find(code);
    if (!invite) throw new Error('InviteNotFound');
    // naive: mark used and return a JWT for the user
    const nowHashed = hashPasswordPBKDF2(password, code);
    // update uses
    invite.uses = (invite.uses || 0) + 1;
    await this.repo.update(code, { uses: invite.uses });
    const payload = { sub: code, owner: invite.owner, roles: invite.roles || [] };
    const token = signPayload(payload, requiredJwtSecret(), { expiresIn: '1h' });
    return { token, hashedPassword: nowHashed };
  }
}

module.exports = SignupService;
