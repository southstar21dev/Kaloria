/// Weigh-ins imported from Health Connect carry the Health Connect record id they came from, so
/// every device knows they're imports and never writes them back to Health Connect (Room schema 4).
migrate((app) => {
  const weights = app.findCollectionByNameOrId("weight_entries");
  if (weights.fields.getByName("healthConnectId")) return;
  weights.fields.add(new Field({ type: "text", name: "healthConnectId", max: 200 }));
  app.save(weights);
}, (app) => {
  const weights = app.findCollectionByNameOrId("weight_entries");
  weights.fields.removeByName("healthConnectId");
  app.save(weights);
});
