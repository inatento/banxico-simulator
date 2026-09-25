#!/usr/bin/env bash
# Wrapper de arranque del MCP -- instala dependencias si hace falta y luego
# corre el servidor. Existe para que un repo recién clonado funcione con solo
# abrir Claude Code ahí, sin que nadie tenga que acordarse de correr
# `npm install` a mano primero.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ ! -d node_modules ]; then
  echo "[banxico-simulator-mcp] node_modules no existe -- instalando dependencias..." >&2
  npm install --no-fund --no-audit --silent
fi

exec node index.js
