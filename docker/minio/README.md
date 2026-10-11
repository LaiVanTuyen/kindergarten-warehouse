# Local storage image

Compose builds MinIO from the official `github.com/minio/minio` source at
`RELEASE.2025-10-15T17-29-55Z`. This avoids depending on the unavailable
`minio/minio:latest` image. The first build needs Go dependencies and takes
longer; Docker caches subsequent builds. Data remains in the existing named
volume.

Source and AGPLv3 license:
https://github.com/minio/minio/tree/RELEASE.2025-10-15T17-29-55Z

Run `docker compose --env-file .env up -d --build` to build and start the local
stack. This image packaging change does not migrate storage contents or reset
the database.
