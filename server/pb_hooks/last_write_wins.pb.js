/// Last-write-wins on the server: an update carrying an older clientUpdatedAt than the stored
/// record is refused with 409, so a device that was offline for a week can't overwrite newer
/// edits. The client then pulls the newer version instead.
onRecordUpdateRequest((e) => {
  const incoming = e.record.getInt("clientUpdatedAt");
  const stored = e.record.original().getInt("clientUpdatedAt");
  if (incoming < stored) {
    throw new ApiError(409, "A newer version of this record exists.", {});
  }
  e.next();
}, "foods", "log_entries", "weight_entries", "goals");
