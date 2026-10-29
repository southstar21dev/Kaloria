/// One collection per synced Room table. Ids are generated on the device in PocketBase's own
/// format, so a row keeps its id everywhere. `clientUpdatedAt` (epoch ms) decides conflicts;
/// `updated` is the server's change cursor for pulls; `deleted` carries soft deletes.
///
/// Nutrients and goal targets are JSON, not number fields: PocketBase stores an empty number as
/// 0, and "not on the label" must stay distinct from "zero".
migrate((app) => {
  const users = app.findCollectionByNameOrId("users");
  const ownerOnly = "owner = @request.auth.id";
  // Creates must also claim the caller as owner.
  const createRule = "@request.auth.id != '' && @request.body.owner = @request.auth.id";


  const common = [
    { type: "relation", name: "owner", required: true, collectionId: users.id, maxSelect: 1, cascadeDelete: true },
    { type: "number", name: "clientUpdatedAt", required: true, onlyInt: true },
    { type: "bool", name: "deleted" },
    { type: "autodate", name: "created", onCreate: true, onUpdate: false },
    { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
  ];

  const collection = (name, fields) => {
    const c = new Collection({
      type: "base",
      name,
      listRule: ownerOnly,
      viewRule: ownerOnly,
      createRule,
      updateRule: ownerOnly + " && (@request.body.owner:isset = false || @request.body.owner = @request.auth.id)",
      deleteRule: null, // deletes are soft and synced like any other change
      fields: fields.concat(common),
      indexes: [
        `CREATE INDEX idx_${name}_owner_updated ON ${name} (owner, updated)`,
      ],
    });
    app.save(c);
  };

  collection("foods", [
    { type: "text", name: "name", required: true, max: 200 },
    { type: "text", name: "brand", max: 200 },
    { type: "text", name: "barcode", max: 32 },
    { type: "select", name: "source", required: true, maxSelect: 1, values: ["Label", "OpenFoodFacts", "Usda", "Manual"] },
    { type: "text", name: "sourceRef", max: 64 },
    { type: "select", name: "unit", required: true, maxSelect: 1, values: ["Gram", "Millilitre"] },
    { type: "json", name: "servings", maxSize: 20000 },
    { type: "bool", name: "favorite" },
    { type: "json", name: "nutrients", required: true, maxSize: 20000 },
  ]);

  collection("log_entries", [
    { type: "text", name: "foodId", max: 15 },
    { type: "text", name: "foodName", required: true, max: 200 },
    { type: "text", name: "brand", max: 200 },
    { type: "number", name: "amount", required: true, min: 0 },
    { type: "select", name: "unit", required: true, maxSelect: 1, values: ["Gram", "Millilitre"] },
    { type: "number", name: "loggedAt", required: true, onlyInt: true },
    { type: "text", name: "note", max: 500 },
    { type: "json", name: "nutrientsPer100", required: true, maxSize: 20000 },
  ]);

  collection("water_entries", [
    { type: "number", name: "millilitres", required: true, min: 0, onlyInt: true },
    { type: "number", name: "loggedAt", required: true, onlyInt: true },
  ]);

  collection("weight_entries", [
    { type: "number", name: "kilograms", required: true, min: 0 },
    { type: "number", name: "loggedAt", required: true, onlyInt: true },
  ]);

  collection("goals", [
    { type: "json", name: "targets", required: true, maxSize: 20000 },
  ]);
}, (app) => {
  for (const name of ["goals", "weight_entries", "water_entries", "log_entries", "foods"]) {
    app.delete(app.findCollectionByNameOrId(name));
  }
});
