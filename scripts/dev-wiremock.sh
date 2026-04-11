#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# dev-wiremock.sh — Run the full budget-sortbot UI against WireMock stubs
#
# Usage (from repo root):
#   ./scripts/dev-wiremock.sh
#
# What it does:
#   1. Starts the Spring Boot backend (port 8080) backed by WireMock (port 9090)
#      — no real API calls are made
#   2. Starts the Vite dev server (port 5173) which proxies /api → 8080
#   3. Prints the documented API keys you should enter in the UI
# ─────────────────────────────────────────────────────────────────────────────

set -e
cd "$(dirname "$0")/.."          # repo root

# ── Pretty colours ────────────────────────────────────────────────────────────
BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
RESET='\033[0m'

banner() {
  echo ""
  echo -e "${CYAN}${BOLD}╔══════════════════════════════════════════════════════════════════╗${RESET}"
  echo -e "${CYAN}${BOLD}║           budget-sortbot  —  WireMock Dev Environment            ║${RESET}"
  echo -e "${CYAN}${BOLD}╚══════════════════════════════════════════════════════════════════╝${RESET}"
  echo ""
}

keys_table() {
  echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${RESET}"
  echo -e "${BOLD}║  VALID API KEYS  (paste into Configuration → API Keys)   ║${RESET}"
  echo -e "${BOLD}╠══════════════════════════════════════════════════════════╣${RESET}"
  echo -e "${BOLD}║${RESET}  YNAB Token:          ${GREEN}ynab-valid-dev-token${RESET}              ${BOLD}║${RESET}"
  echo -e "${BOLD}║${RESET}  FastMail Token:      ${GREEN}fastmail-valid-dev-token${RESET}          ${BOLD}║${RESET}"
  echo -e "${BOLD}║${RESET}  Gemini Key:          ${GREEN}gemini-valid-dev-key${RESET}              ${BOLD}║${RESET}"
  echo -e "${BOLD}║${RESET}  YNAB Budget ID:      ${GREEN}budget-dev-001${RESET} (from dropdown)    ${BOLD}║${RESET}"
  echo -e "${BOLD}╠══════════════════════════════════════════════════════════╣${RESET}"
  echo -e "${BOLD}║  INVALID KEYS  (any other value triggers error states)   ║${RESET}"
  echo -e "${BOLD}╠══════════════════════════════════════════════════════════╣${RESET}"
  echo -e "${BOLD}║${RESET}  YNAB wrong token  → ${RED}401${RESET} on Test, budgets, categories, txns ${BOLD}║${RESET}"
  echo -e "${BOLD}║${RESET}  FastMail wrong    → ${RED}401${RESET} on Test + email search              ${BOLD}║${RESET}"
  echo -e "${BOLD}║${RESET}  Gemini wrong      → ${RED}400 INVALID_ARGUMENT${RESET} on Test + classify  ${BOLD}║${RESET}"
  echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${RESET}"
  echo ""
}

cleanup() {
  echo ""
  echo -e "${YELLOW}Shutting down dev servers...${RESET}"
  # Kill the background Gradle process and the Vite dev server
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM

# ── Pre-flight ─────────────────────────────────────────────────────────────────
if ! command -v node >/dev/null 2>&1; then
  echo -e "${RED}Node.js not found. Install Node 20+ and re-run.${RESET}"
  exit 1
fi

if [[ ! -d frontend/node_modules ]]; then
  echo -e "${YELLOW}Installing frontend dependencies...${RESET}"
  (cd frontend && npm ci --silent)
fi

# ── Start backend (WireMock + Spring Boot) ────────────────────────────────────
banner
echo -e "${BOLD}Starting backend (Spring Boot + WireMock)...${RESET}"
./gradlew runDevServer --console=plain 2>&1 &
BACKEND_PID=$!

# Wait until Spring Boot is ready
echo -n "Waiting for backend on :8080 "
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1 || \
     curl -sf http://localhost:8080/api/config/keys >/dev/null 2>&1; then
    echo -e " ${GREEN}ready!${RESET}"
    break
  fi
  echo -n "."
  sleep 2
done

echo ""

# ── Start frontend (Vite dev server) ──────────────────────────────────────────
echo -e "${BOLD}Starting frontend (Vite on :5173)...${RESET}"
(cd frontend && npm run dev -- --port 5173) &
FRONTEND_PID=$!

sleep 3   # give Vite a moment to bind

# ── Print keys and URLs ────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}✔  Dev environment is up!${RESET}"
echo ""
echo -e "  UI:       ${CYAN}http://localhost:5173${RESET}"
echo -e "  Backend:  ${CYAN}http://localhost:8080${RESET}"
echo -e "  WireMock: ${CYAN}http://localhost:9090/__admin/mappings${RESET}  (inspect stubs)"
echo ""
keys_table

echo -e "${YELLOW}Press Ctrl+C to stop both servers.${RESET}"
echo ""

# Keep script alive until Ctrl+C
wait
