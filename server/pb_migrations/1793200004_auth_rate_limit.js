/// Throttles password sign-ins, for app users and superusers alike, so passwords can't be guessed
/// at speed: 5 attempts per minute per client IP. Only sign-in is limited; PocketBase's default
/// rules would also throttle record creates, which a phone's first sync sends by the thousand.
///
/// Behind a reverse proxy every request comes from the proxy's IP, so the limit is shared by all
/// clients unless the proxy's client-IP header is set under Settings > Application > Trusted proxy.
/// Rate limiting already switched on in the admin UI is left as it is.
migrate((app) => {
  const settings = app.settings();
  if (settings.rateLimits.enabled) return;
  unmarshal({
    rateLimits: {
      enabled: true,
      rules: [{ label: "*:authWithPassword", audience: "", duration: 60, maxRequests: 5 }],
    },
  }, settings);
  app.save(settings);
}, (app) => {
  const settings = app.settings();
  settings.rateLimits.enabled = false;
  app.save(settings);
});
