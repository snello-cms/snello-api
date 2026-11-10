#!/usr/bin/env bash
# Runs the control-app tests against postgresql (compose dev services), h2 (in-memory) and mysql (throwaway container).
# Usage: dev/test-all-dbs.sh [-Dtest=Pattern]   (default: every test)
set -uo pipefail
cd "$(dirname "$0")/.."
EXTRA=("$@")
MVN=(mvn -o -pl snello-control-app -am test -Dsurefire.failIfNoSpecifiedTests=false "${EXTRA[@]}")
results=()

run() { # name, args...
  local name=$1; shift
  echo "=== $name ==="
  if "${MVN[@]}" "$@" > "target/test-$name.log" 2>&1; then results+=("$name OK"); else results+=("$name FAILED (target/test-$name.log)"); fi
}
mkdir -p target

run postgresql

run h2 -Dquarkus.compose.devservices.enabled=false -Dquarkus.datasource.db-kind=h2 -Dsnello.dbtype=h2 \
  "-Dquarkus.datasource.jdbc.url=jdbc:h2:mem:snello;DB_CLOSE_DELAY=-1;MODE=MySQL;IGNORECASE=TRUE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" \
  -Dquarkus.datasource.username=sa -Dquarkus.datasource.password=

docker rm -f snello-mysql-test >/dev/null 2>&1
docker run -d --name snello-mysql-test -e MYSQL_ROOT_PASSWORD=snello -e MYSQL_DATABASE=snello \
  -e MYSQL_USER=snello -e MYSQL_PASSWORD=snello -p 3307:3306 mysql:8.4 >/dev/null
for _ in $(seq 1 40); do docker exec snello-mysql-test mysqladmin ping -usnello -psnello --silent 2>/dev/null && break; sleep 3; done
run mysql -Dquarkus.compose.devservices.enabled=false -Dquarkus.datasource.db-kind=mysql -Dsnello.dbtype=mysql \
  -Dquarkus.datasource.jdbc.url=jdbc:mysql://localhost:3307/snello -Dquarkus.datasource.username=snello -Dquarkus.datasource.password=snello
docker rm -f snello-mysql-test >/dev/null 2>&1

printf '\n%s\n' "${results[@]}"
[[ " ${results[*]} " != *FAILED* ]]
