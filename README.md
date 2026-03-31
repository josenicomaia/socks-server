# socks-server

A lightweight SOCKS5 proxy server built with Java 26 Virtual Threads.

## Features

- **SOCKS5 protocol** — `CONNECT` command with `NO_AUTH` authentication
- **Virtual Threads** — scales to thousands of concurrent connections
- **TUI Dashboard** — real-time metrics display (ngrok-style)
- **Update Checker** — notifies when a new version is available

## Requirements

- **JDK 26+** (recommended: [Azul Zulu](https://www.azul.com/downloads/))
- **Maven 3.9+**
- **Docker** (for integration tests)

## Quick Start

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
java -jar target/server-*.jar
```

By default, the server starts on port **1080** with the TUI dashboard enabled.

### Custom Port

```bash
java -jar target/server-*.jar 5353
```

## Flags

| Flag | Description |
|---|---|
| `--no-tui` | Disable the TUI dashboard (useful for Docker, CI, or piped output) |

### Examples

```bash
# Default: TUI dashboard enabled, port 1080
java -jar target/server-*.jar

# Custom port with TUI
java -jar target/server-*.jar 5353

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
docker run -p 1080:1080 socks-server
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
