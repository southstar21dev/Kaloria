// Kaloria publishes APKs, not npm packages; see package.json. Loading this means .releaserc.json
// lost its explicit plugin list, which would also try to publish to npm.
throw new Error("@semantic-release/npm is not used by Kaloria: keep the plugins list in .releaserc.json");
