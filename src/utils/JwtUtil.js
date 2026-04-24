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

function hashPasswordSHA256(password, salt) {
  const h = crypto.createHash('sha256');
  h.update(password + (salt || ''));
  return h.digest('hex');
}

module.exports = {
  signPayload,
  verifyToken,
  hashPasswordSHA256,
};
