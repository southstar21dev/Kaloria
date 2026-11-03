/// Kaloria is a personal server: accounts are created in the admin UI, not by public sign-up.
migrate((app) => {
  const users = app.findCollectionByNameOrId("users");
  users.listRule = "id = @request.auth.id";
  users.viewRule = "id = @request.auth.id";
  users.createRule = null;
  users.updateRule = "id = @request.auth.id";
  users.deleteRule = null;
  users.authToken.duration = 2592000; // 30 days; the app refreshes it on every sync
  app.save(users);
}, (app) => {
  const users = app.findCollectionByNameOrId("users");
  users.createRule = "";
  users.authToken.duration = 1209600;
  app.save(users);
});
