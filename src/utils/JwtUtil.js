// Minimal JWT utilities for MVP using HS256
const crypto = require('crypto');
const jwt = require('jsonwebtoken');

function signPayload(payload, secret, options = {}) {
  const defaultOpts = { algorithm: 'HS256', expiresIn: '1h' };
  return jwt.sign(payload, secret, Object.assign({}, defaultOpts, options));
}

function verifyToken(token, secret) {
  try {
    return jwt.verify(token, secret);
  } catch (e) {
    return null;
  }
}

function hashPasswordPBKDF2(password, salt) {
  return crypto.pbkdf2Sync(password, salt, 210000, 32, 'sha256').toString('hex');
}

module.exports = {
  signPayload,
  verifyToken,
  hashPasswordPBKDF2,
};
