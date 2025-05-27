#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATIC="$ROOT/src/main/resources/static"

(cd "$ROOT/frontend/web" && pnpm install --frozen-lockfile && pnpm build)
(cd "$ROOT/frontend/admin" && pnpm install --frozen-lockfile && pnpm build)

rm -rf "$STATIC"
mkdir -p "$STATIC/admin"
cp -R "$ROOT/frontend/web/dist/." "$STATIC/"
cp -R "$ROOT/frontend/admin/dist/." "$STATIC/admin/"

echo "Frontend assets copied to $STATIC"
