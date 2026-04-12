#!/bin/bash
set -e
cd "$(dirname "$0")"
echo "Building uhabits-core JS target..."
(cd .. && ./gradlew :uhabits-core:jsProductionLibraryDistribution)
echo "Starting Vite dev server..."
npm run dev
