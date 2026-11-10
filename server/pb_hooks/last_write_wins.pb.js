/// Last-write-wins on the server: an update carrying an older clientUpdatedAt than the stored
/// record is refused with 409, so a device that was offline for a week can't overwrite newer
/// edits. The client then pulls the newer version instead.
///
/// The request hook turns away stale and far-future updates early, from the copy the request
/// loaded. That copy can be out of date by the time the row is written: two updates of the same
/// record arriving together could both pass, and the older one land last. So the decisive check
/// runs again just before the UPDATE, below.
onRecordUpdateRequest((e) => {
  const incoming = e.record.getInt("clientUpdatedAt");
  // Handlers can't see variables outside themselves, so the limit is repeated in each hook.
  if (incoming > Date.now() + 60 * 60 * 1000) {
    throw new ApiError(422, "clientUpdatedAt is more than an hour in the future. Check the device's clock.", {});
  }
  if (incoming < e.record.original().getInt("clientUpdatedAt")) {
    throw new ApiError(409, "A newer version of this record exists.", {});
  }
  e.next();
}, "foods", "log_entries", "weight_entries", "goals");

/// The atomic half: re-reads the stored row and writes the update in one transaction. PocketBase
/// writes through a single SQLite connection, so no other write can land between this read and
/// this write. The save is sent through the transaction by handing it to e.next() as e.app.
/// Applies to every save of these collections, including edits in the admin UI.
onRecordUpdateExecute((e) => {
  e.app.runInTransaction((txApp) => {
    const stored = txApp.findRecordById(e.record.collection(), e.record.id).getInt("clientUpdatedAt");
    if (e.record.getInt("clientUpdatedAt") < stored) {
      throw new ApiError(409, "A newer version of this record exists.", {});
    }
    e.app = txApp;
    e.next();
  });
}, "foods", "log_entries", "weight_entries", "goals");

/// A clientUpdatedAt far in the future would win every conflict until real time caught up with it,
/// so a device whose clock runs ahead is refused instead. Its rows stay unsynced on the device
/// (shown in the app as refused) and go through once its clock is right.
onRecordCreateRequest((e) => {
  if (e.record.getInt("clientUpdatedAt") > Date.now() + 60 * 60 * 1000) {
    throw new ApiError(422, "clientUpdatedAt is more than an hour in the future. Check the device's clock.", {});
  }
  e.next();
}, "foods", "log_entries", "weight_entries", "goals");
