// Express-like handler for /auth/signup-with-code (MVP stub)
const SignupService = require('../../auth/SignupService');

module.exports = async function signupWithCodeHandler(req, res) {
  try {
    const svc = new SignupService();
    const { code, password } = req.body || {};
    if (!code || !password) {
      res.statusCode = 400;
      return res.end(JSON.stringify({ error: 'Missing code or password' }));
    }
    const result = await svc.signUpWithCode(code, password);
    res.statusCode = 200;
    res.end(JSON.stringify(result));
  } catch (e) {
    res.statusCode = 400;
    res.end(JSON.stringify({ error: e.message }));
  }
};
