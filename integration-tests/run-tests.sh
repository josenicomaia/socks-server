#!/bin/sh
set -e

PROXY="socks5://socks-server:5353"
PASSED=0
FAILED=0
TOTAL=0

# ─── Helpers ──────────────────────────────────────────────

pass() {
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
    echo "  ✅ PASS: $1"
}

fail() {
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
    echo "  ❌ FAIL: $1 — $2"
}

wait_for_server() {
    echo "⏳ Waiting for socks-server to be ready..."
    for i in $(seq 1 30); do
        if curl -s --proxy "$PROXY" --max-time 2 -o /dev/null http://example.com 2>/dev/null; then
            echo "✅ Server is ready!"
            return 0
        fi
        sleep 1
    done
    echo "❌ Server did not start within 30s"
    exit 1
}

# ─── Tests ────────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║     SOCKS5 Integration Tests (via curl)      ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

wait_for_server

# --- Test 1: HTTP request through SOCKS5 proxy ---
echo ""
echo "── Test 1: HTTP GET via SOCKS5 proxy ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 http://httpbin.org/get 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "HTTP GET returned 200"
else
    fail "HTTP GET" "expected 200, got $HTTP_CODE"
fi

# --- Test 2: HTTPS request through SOCKS5 proxy ---
echo ""
echo "── Test 2: HTTPS GET via SOCKS5 proxy ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 https://httpbin.org/get 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "HTTPS GET returned 200"
else
    fail "HTTPS GET" "expected 200, got $HTTP_CODE"
fi

# --- Test 3: Verify response body content ---
echo ""
echo "── Test 3: Response body contains expected data ──"
BODY=$(curl -4 -s --proxy "$PROXY" --max-time 10 http://httpbin.org/get 2>/dev/null || echo "")
if echo "$BODY" | grep -q '"url"'; then
    pass "Response body contains JSON data"
else
    fail "Response body" "missing expected JSON fields"
fi

# --- Test 4: POST request through SOCKS5 ---
echo ""
echo "── Test 4: HTTP POST via SOCKS5 proxy ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 \
    -X POST -H "Content-Type: application/json" -d '{"test":"socks5"}' \
    http://httpbin.org/post 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "HTTP POST returned 200"
else
    fail "HTTP POST" "expected 200, got $HTTP_CODE"
fi

# --- Test 5: Domain name resolution via SOCKS5 ---
echo ""
echo "── Test 5: Domain resolution through proxy ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 http://example.com 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "Domain resolution works"
else
    fail "Domain resolution" "expected 200, got $HTTP_CODE"
fi

# --- Test 6: HTTPS via SOCKS5h (DNS resolved by proxy) ---
echo ""
echo "── Test 6: HTTPS via socks5h (proxy-side DNS) ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "socks5h://socks-server:5353" --max-time 10 https://httpbin.org/get 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "HTTPS via socks5h works"
else
    fail "HTTPS via socks5h" "expected 200, got $HTTP_CODE"
fi

# --- Test 7: Large response handling ---
echo ""
echo "── Test 7: Large response (multiple bytes) ──"
BYTES=$(curl -4 -s --proxy "$PROXY" --max-time 15 -o /dev/null -w "%{size_download}" http://httpbin.org/bytes/4096 2>/dev/null || echo "0")
if [ "$BYTES" -ge 4000 ]; then
    pass "Received $BYTES bytes (expected ~4096)"
else
    fail "Large response" "received only $BYTES bytes"
fi

# ─── Summary ──────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════"
echo "  Results: $PASSED passed, $FAILED failed, $TOTAL total"
echo "════════════════════════════════════════════════"
echo ""

if [ "$FAILED" -gt 0 ]; then
    exit 1
fi

exit 0
