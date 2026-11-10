# A community image of the official PocketBase release, pinned by digest (the multi-arch index) so a
# re-pushed tag can't change what gets built. To update: bump the tag and replace the digest with
# the one `docker buildx imagetools inspect ghcr.io/muchobien/pocketbase:<tag>` prints.
FROM ghcr.io/muchobien/pocketbase:0.40.4@sha256:9390b7b63ce114dbab577be72e6ef75f718a19083866607fcbdd1b915632b943

COPY pb_migrations /pb_migrations
COPY pb_hooks /pb_hooks
COPY start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8090/api/health || exit 1

ENTRYPOINT ["/usr/local/bin/start.sh"]
