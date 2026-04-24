// Minimal localization helper: picks locale from Accept-Language header (stubbed)
function pickLocale(acceptLanguageHeader, available = ['en','de']) {
  if (!acceptLanguageHeader) return 'en';
  const langs = acceptLanguageHeader.split(',').map(s => s.trim().split(';')[0]);
  for (const l of langs) {
    const short = l.split('-')[0];
    if (available.includes(short)) return short;
  }
  return 'en';
}

module.exports = { pickLocale };
