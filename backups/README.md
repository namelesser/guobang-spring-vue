# Production database backups

These archives contain production transport and weighbridge data. Keep the
repository and its release attachments private. Never copy them to a public
repository.

## Current backup

- Release: `transport-db-20260904T2242`
- Files: `transport-production-20260904T2242.dump.part-00` through `part-07`
- Source: JD Cloud PostgreSQL 17.11, database `transport`
- Format: PostgreSQL custom archive with Zstandard compression
- Size: 650278458 bytes
- SHA-256: `3e57bce908fce728c2348c494ccd64b28ae2a305c0dd872279c9f191928040bf`
- Options: no owner and no privileges

Download all eight release attachments, then join them in filename order:

```sh
cat transport-production-20260904T2242.dump.part-* \
  > transport-production-20260904T2242.dump
```

Verify the archive before restoring:

```sh
shasum -a 256 transport-production-20260904T2242.dump
pg_restore --list transport-production-20260904T2242.dump >/dev/null
```

Restore into a newly created PostgreSQL database:

```sh
createdb transport_restore
pg_restore --exit-on-error --no-owner --no-privileges \
  --dbname=transport_restore \
  transport-production-20260904T2242.dump
```

## Part checksums

```text
3c10801ec5496553b6431aac164c01c1c7f0d830fa496932286a76847aff9df7  transport-production-20260904T2242.dump.part-00
88a6e3625e1a47419f823cc1022927649b47ae66e0cfb3ff8f900aacd9249116  transport-production-20260904T2242.dump.part-01
09891e5067f5cb60dca83dbdcbf8fc90a9a062fb813d3cb4243ca8d94a047c24  transport-production-20260904T2242.dump.part-02
f855f85b96fd95901ed31fac70b4e49d720c7db98760fff7ada116fda36426c0  transport-production-20260904T2242.dump.part-03
bd09d031fda3fd11ce671f41c720735ebaca716fdc375b9543e079a390aaa9bc  transport-production-20260904T2242.dump.part-04
4cc80650146e5164cf473c871674c901fbdfd61ea6400e887af57e35035fcb4d  transport-production-20260904T2242.dump.part-05
68c26ed35e0407178fc3a0fdbc33a8eb0bf475d4c7e15ae34188b74c925232b4  transport-production-20260904T2242.dump.part-06
192a3317e4e3f5f4e6b4a7e60985e3ffd00f1cab961bfbdf34ed687c32a736f1  transport-production-20260904T2242.dump.part-07
```
