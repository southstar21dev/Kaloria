/// Water tracking was removed from the app (Room schema 3), so its collection goes too. This
/// deletes the stored entries; reverting restores an empty collection, not the data.
migrate((app) => {
  let water;
  try {
    water = app.findCollectionByNameOrId("water_entries");
  } catch (_) {
    return; // already removed by hand in the admin UI
  }
  app.delete(water);
}, (app) => {
  const users = app.findCollectionByNameOrId("users");
  const ownerOnly = "owner = @request.auth.id";
  app.save(new Collection({
    type: "base",
    name: "water_entries",
    listRule: ownerOnly,
    viewRule: ownerOnly,
    createRule: "@request.auth.id != '' && @request.body.owner = @request.auth.id",
    updateRule: ownerOnly + " && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)",
    deleteRule: null,
    fields: [
      { type: "number", name: "millilitres", required: true, min: 0, onlyInt: true },
      { type: "number", name: "loggedAt", required: true, onlyInt: true },
      { type: "relation", name: "owner", required: true, collectionId: users.id, maxSelect: 1, cascadeDelete: true },
      { type: "number", name: "clientUpdatedAt", required: true, onlyInt: true },
      { type: "bool", name: "deleted" },
      { type: "autodate", name: "created", onCreate: true, onUpdate: false },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE INDEX idx_water_entries_owner_updated ON water_entries (owner, updated)",
    ],
  }));
});
