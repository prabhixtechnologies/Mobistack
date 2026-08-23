#!/usr/bin/env bash
set -euo pipefail
# Prints KEY=set|empty. Never prints values.
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" == \#* ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  if [[ -n "$val" ]]; then
    echo "${key}=set"
  else
    echo "${key}=empty"
  fi
done < /opt/mobistack/.env
