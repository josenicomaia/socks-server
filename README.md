# socks-server

A lightweight SOCKS5 proxy server built with Java 26 Virtual Threads.

## Features

- **SOCKS5 protocol** — `CONNECT` command with mandatory username/password authentication (RFC 1929)
- **Virtual Threads** — scales to thousands of concurrent connections
- **TUI Dashboard** — real-time metrics display (ngrok-style)
- **Update Checker** — notifies when a new version is available

## Requirements

- **JDK 26+** (recommended: [Azul Zulu](https://www.azul.com/downloads/))
- **Maven 3.9+**
- **Docker** (for integration tests)

## Authentication

The server requires SOCKS5 username/password authentication (RFC 1929) — plain `NO_AUTH`
negotiations are rejected. Credentials are read once at startup from environment variables and
the process refuses to start if either is missing:

| Variable | Description |
|---|---|
| `SOCKS_USERNAME` | Required username |
| `SOCKS_PASSWORD` | Required password |

Each value must be at most **255 bytes** in UTF-8 (the RFC 1929 field limit); longer values are
rejected at startup.

```bash
export SOCKS_USERNAME=myuser
export SOCKS_PASSWORD=mypassword
java -jar target/server-*.jar
```

Clients must be configured to use SOCKS5 with username/password auth (not "no authentication").
Note that RFC 1929 sends credentials in cleartext over the TCP connection — combine this with
network-level controls (firewall/VPN) if the proxy is reachable over an untrusted network.

Clients must send their whole handshake (greeting, credentials and command request) within
**10 seconds** in total — a deadline across all reads, so dripping bytes doesn't extend it;
connections that miss it are closed. Connecting to the destination is bounded separately (10 s).

To slow down online brute force, each client — an IPv4 address, or an IPv6 /64 — gets at most
**5 password checks per minute**, counting checks still in progress, so opening connections in
parallel doesn't buy extra guesses. Once 5 have failed, the client is refused until a minute has
passed since its first failure. Each client may also have at most **64 handshakes in progress**
at once. Clients behind the same NAT address share these limits. Rejected handshakes, blocked
clients and clients hitting the concurrency cap are logged as warnings with the client address
(never the submitted username or password), so they can also feed tools such as fail2ban.

> **Upgrading from 1.0.x:** authentication used to be disabled (`NO_AUTH` was always accepted).
> Existing deployments must now set `SOCKS_USERNAME` and `SOCKS_PASSWORD` — including
> `docker run` — or the server will refuse to start, and every client must be reconfigured to
> send those credentials. To keep the old behavior in a strictly controlled environment, start
> the server with `--no-auth` (see below).

### Running without authentication (`--no-auth`)

> [!CAUTION]
> `--no-auth` turns the server into an **open proxy**: anyone who can reach the port can use it
> to open connections on your behalf. Only use it in **strictly controlled environments** (local
> development, isolated test networks) and never on a network reachable by untrusted hosts.

```bash
java -jar target/server-*.jar --no-auth
docker run -p 5353:5353 socks-server --no-auth
```

In this mode only `NO_AUTH` clients are accepted and the server logs a security warning at
startup (and keeps one on the TUI dashboard). Combining `--no-auth` with `SOCKS_USERNAME` or
`SOCKS_PASSWORD` is a contradictory configuration, so the server refuses to start instead of
silently ignoring the credentials.

## Quick Start

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
export SOCKS_USERNAME=myuser
export SOCKS_PASSWORD=mypassword
java -jar target/server-*.jar
```

The credentials are required for every invocation below unless `--no-auth` is passed (see
[Authentication](#authentication)).
By default, the server starts on port **5353** with the TUI dashboard enabled.

### Custom Port

```bash
java -jar target/server-*.jar 1080
```

## Flags

| Flag | Description |
|---|---|
| `--no-tui` | Disable the TUI dashboard (useful for Docker, CI, or piped output) |
| `--no-auth` | Disable authentication — **strictly controlled environments only** (see [Running without authentication](#running-without-authentication---no-auth)) |

### Examples

```bash
# Default: TUI dashboard enabled, port 5353
java -jar target/server-*.jar

# Custom port with TUI
java -jar target/server-*.jar 1080

# Headless mode (no TUI) — logs go to stdout
java -jar target/server-*.jar --no-tui

# Headless on custom port
java -jar target/server-*.jar 5353 --no-tui
```

## Logging

When the TUI is **enabled**, logs are redirected to rotating files to avoid cluttering the dashboard:

| File | Description |
|---|---|
| `socks-server.0.log` | Current log file |
| `socks-server.1.log` | Previous rotation |
| `socks-server.2.log` | Oldest rotation |

Each file rotates at **5 MB**, keeping up to **3 files**.

When the TUI is **disabled** (`--no-tui`), logs go to **stdout** as usual.

## Docker

```bash
# Build
docker build -t socks-server .

# Run (TUI is disabled automatically via --no-tui in Dockerfile)
docker run -p 5353:5353 \
  -e SOCKS_USERNAME=myuser \
  -e SOCKS_PASSWORD=mypassword \
  socks-server
```

## Testing

### Unit Tests

```bash
mvn clean test
```

### Integration Tests

```bash
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from integration-tests
```

## CI/CD

| Workflow | Trigger | Jobs |
|---|---|---|
| **PR** (`ci.yml`) | Pull request to `master` | Unit tests → Integration tests |
| **Master** (`master.yml`) | Push to `master` | Format code → Bump version → Unit tests → Integration tests |

Shared test logic lives in `tests.yml` (reusable workflow).

## Architecture

```
Main
 ├── ServerConfig          — CLI args parsing
 ├── ConnectionHandler     — accepts TCP connections (Virtual Threads)
 │    ├── AuthHandler      — SOCKS5 authentication
 │    └── ConnectHandler   — CONNECT command
 │         └── ClientServerTransfer — bidirectional data relay
 ├── Metrics               — thread-safe connection/traffic stats
 ├── Dashboard             — ANSI TUI renderer
 └── LogConfig             — rotating file handler
```

## License

MIT
