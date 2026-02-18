#!/usr/bin/env bash
set -euo pipefail

if [ $# -eq 0 ]; then
    echo "Usage: $0 <command>"
    echo "Example: $0 './gradlew run'"
    exit 1
fi

set -a
source /Users/borisosaulenko/Documents/Finances/env
set +a

echo "🔄 Environment loaded. Executing: $*"
eval "$@"