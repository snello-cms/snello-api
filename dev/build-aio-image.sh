#!/usr/bin/env bash
# Builds the all-in-one image WITH the admin SPA: bundle admin -> mvn package -> verify jar -> docker build.
# The SPA must be inside the app jar (META-INF/resources) before packaging, so it cannot be a late Dockerfile COPY:
# mutable-jar re-augmentation at container start resolves its inputs from quarkus-app/.
#
# Usage: dev/build-aio-image.sh [--skip-admin-build] [image-tag]   (default tag snellocms/snello-all-in-one:alpha)
set -euo pipefail
cd "$(dirname "$0")/.."
BUNDLE_ARGS=()
if [[ "${1:-}" == "--skip-admin-build" ]]; then BUNDLE_ARGS+=(--skip-build); shift; fi
TAG="${1:-snellocms/snello-all-in-one:alpha}"

echo "[aio-image] 1/4 bundle admin"
dev/bundle-admin.sh "${BUNDLE_ARGS[@]}"

echo "[aio-image] 2/4 mvn package"
mvn -pl snello-all-in-one-app -am package -DskipTests -DskipITs -q

echo "[aio-image] 3/4 verify the SPA is inside the app jar"
JAR=$(ls snello-all-in-one-app/target/quarkus-app/app/snello-all-in-one-app-*.jar | head -1)
LISTING=$(unzip -l "$JAR")  # not piped into grep -q: with pipefail its early exit SIGPIPEs unzip and false-fails
if ! grep -q "META-INF/resources/index.html" <<<"$LISTING"; then
  echo "[aio-image] ERROR: $JAR has no META-INF/resources/index.html; image would ship without the admin"; exit 1
fi

echo "[aio-image] 4/4 docker build $TAG"
docker build -f snello-all-in-one-app/src/main/docker/Dockerfile.jvm -t "$TAG" snello-all-in-one-app
echo "[aio-image] built $TAG (admin at /, API at /api)"
