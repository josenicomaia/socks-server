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
    for i in $(seq 1 5); do
        if curl -s --proxy "$PROXY" --max-time 2 -o /dev/null http://example.com 2>/dev/null; then
            echo "✅ Server is ready!"
            return 0
        fi
        sleep 1
    done
    echo "❌ Server did not start within 5s"
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

# --- Test 8: Concurrent connections (virtual threads stress) ---
echo ""
echo "── Test 8: 20 concurrent connections ──"
CONCURRENT_OK=0
CONCURRENT_TOTAL=20
PIDS=""
TMPDIR_CONC=$(mktemp -d)

for i in $(seq 1 $CONCURRENT_TOTAL); do
    (
        code=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 15 "http://httpbin.org/get?req=$i" 2>/dev/null || echo "000")
        echo "$code" > "$TMPDIR_CONC/result_$i"
    ) &
    PIDS="$PIDS $!"
done

# Wait for all background processes
for pid in $PIDS; do
    wait "$pid" 2>/dev/null
done

for i in $(seq 1 $CONCURRENT_TOTAL); do
    code=$(cat "$TMPDIR_CONC/result_$i" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
        CONCURRENT_OK=$((CONCURRENT_OK + 1))
    fi
done
rm -rf "$TMPDIR_CONC"

if [ "$CONCURRENT_OK" -eq "$CONCURRENT_TOTAL" ]; then
    pass "All $CONCURRENT_TOTAL concurrent requests returned 200"
else
    fail "Concurrent connections" "$CONCURRENT_OK/$CONCURRENT_TOTAL succeeded"
fi

# --- Test 9: Unreachable host (graceful error handling) ---
echo ""
echo "── Test 9: Unreachable host (proxy should not crash) ──"
# Connect to a non-routable IP — proxy should handle gracefully
curl -4 -s -o /dev/null --proxy "$PROXY" --max-time 5 http://192.0.2.1/ 2>/dev/null || true
# If we can still make a request after, the proxy survived
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 http://httpbin.org/get 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "Proxy survived unreachable host and still works"
else
    fail "Unreachable host recovery" "proxy stopped responding (got $HTTP_CODE)"
fi

# --- Test 10: Header preservation ---
echo ""
echo "── Test 10: Custom headers pass through proxy ──"
BODY=$(curl -4 -s --proxy "$PROXY" --max-time 10 \
    -H "X-Custom-Test: socks5-proxy-check" \
    -H "X-Trace-Token: integration-42" \
    http://httpbin.org/headers 2>/dev/null || echo "")
HEADER_OK=0
if echo "$BODY" | grep -qi "socks5-proxy-check"; then
    HEADER_OK=$((HEADER_OK + 1))
fi
if echo "$BODY" | grep -qi "integration-42"; then
    HEADER_OK=$((HEADER_OK + 1))
fi
if [ "$HEADER_OK" -eq 2 ]; then
    pass "Both custom headers preserved through proxy"
else
    fail "Header preservation" "only $HEADER_OK/2 headers found in response"
fi

# ─── Negative Tests ───────────────────────────────────────

# --- Test 11: Dead proxy (wrong port) ---
echo ""
echo "── Test 11: Dead SOCKS proxy (wrong port) ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "socks5://socks-server:9999" --max-time 5 http://httpbin.org/get 2>/dev/null || true)
if [ "$HTTP_CODE" != "200" ]; then
    pass "Connection to dead proxy correctly failed (got $HTTP_CODE)"
else
    fail "Dead proxy" "expected connection failure, got HTTP 200"
fi

# --- Test 12: Non-existent proxy host ---
echo ""
echo "── Test 12: Non-existent SOCKS proxy host ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "socks5://ghost-proxy:5353" --max-time 5 http://httpbin.org/get 2>/dev/null || true)
if [ "$HTTP_CODE" != "200" ]; then
    pass "Connection to non-existent proxy correctly failed (got $HTTP_CODE)"
else
    fail "Non-existent proxy" "expected connection failure, got HTTP 200"
fi

# --- Test 13: Proxy alive but target refuses connection ---
echo ""
echo "── Test 13: Target server refuses connection ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 5 http://127.0.0.1:1 2>/dev/null || true)
if [ "$HTTP_CODE" != "200" ]; then
    pass "Refused connection handled gracefully (got $HTTP_CODE)"
else
    fail "Refused connection" "expected failure, got HTTP 200"
fi

# Verify proxy still works after negative tests
echo ""
echo "── Sanity check: proxy still alive after negative tests ──"
HTTP_CODE=$(curl -4 -s -o /dev/null -w "%{http_code}" --proxy "$PROXY" --max-time 10 http://httpbin.org/get 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "Proxy survived all negative tests"
else
    fail "Post-negative sanity" "proxy stopped responding (got $HTTP_CODE)"
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
