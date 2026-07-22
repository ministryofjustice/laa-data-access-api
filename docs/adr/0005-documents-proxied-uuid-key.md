# Proxy document uploads through this service; use a UUID as the SDS key

The lead architect vetoed direct client-to-SDS uploads. All document uploads go through this service (`POST /applications/{id}/documents`), which proxies bytes to SDS and — only on success — emits `DocumentAttached(documentId)`.

SDS's visible contract echoes back a filename-derived key (`{appId}/{filename}`), not an opaque object id. If we used that key, the original filename (PII) would appear in the SDS key, in events, and in the aggregate. We chose to **mint a UUID** and use it as the SDS object name, making the UUID the opaque `documentId`. The original filename and checksum are stored only in the deletable `documents` table.

## Consequences

- The aggregate tracks only opaque `documentId` values — no filenames, no SDS paths.
- `documentId` is the SDS object key; no separate id-mapping table is needed.
- The aggregate has zero SDS dependency; SDS I/O is confined to the infrastructure layer.
- Pending verification: whether the real SDS `/save_file` contract accepts a client-specified object key and whether it returns any additional id beyond the echoed key.
