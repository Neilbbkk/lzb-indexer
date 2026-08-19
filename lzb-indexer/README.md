# LZB Indexer

[![Java](https://img.shields.io/badge/Java-8-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)](https://spring.io/projects/spring-boot)
[![Web3j](https://img.shields.io/badge/Web3j-4.9.8-blue)](https://docs.web3j.io/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**Multi-chain EVM event indexer** — scans Ethereum, Arbitrum, and Sepolia for on-chain events and stores them in PostgreSQL. Built with **Spring Boot + Web3j + JPA**.

---

## Architecture

```mermaid
graph TB
    subgraph "Blockchain"
        SEP[Sepolia<br/>ERC20 Transfer]
        ETH[Ethereum<br/>Uniswap V2 Swap]
        ARB[Arbitrum<br/>GMX V2 Positions]
    end

    subgraph "Scanner Layer"
        BS1[BlockScanner<br/>sepolia]
        BS2[BlockScanner<br/>ethereum-uniswap]
        BS3[BlockScanner<br/>arbitrum-gmx]
        SCH[ScannerScheduler]
    end

    subgraph "Decoding"
        ED[EventDecoder]
    end

    subgraph "Persistence"
        PG[(PostgreSQL)]
        REPO[Spring Data JPA<br/>7 Repositories]
    end

    subgraph "Observability"
        PROM[Prometheus]
        GRAF[Grafana]
        ACT[/actuator/prometheus]
    end

    subgraph "API & UI"
        REST[REST API<br/>10 Endpoints]
        WS[WebSocket STOMP<br/>/ws → /topic/events]
        DASH[Dashboard<br/>index.html]
    end

    SEP --> BS1
    ETH --> BS2
    ARB --> BS3
    BS1 & BS2 & BS3 --> SCH
    SCH --> ED
    ED --> REPO
    REPO --> PG
    PG --> REST
    REST --> DASH
    WS --> DASH
    ACT --> PROM --> GRAF
```

**Design decisions:**
- **Event Sourcing** for GMX positions — raw events stored in `gmx_position_history`, current state computed by replay
- **Centralized decoding** - EventDecoder dispatches by protocol (ERC20 / Uniswap V2 / GMX V2)
- **StaticEventPublisher** bridge — enables non-Spring beans (`BlockScanner`) to publish Spring ApplicationEvents
- **Reorg detection** via `scanned_blocks` table (block hash comparison)
- **RPC retry** with exponential backoff (1s → 2s → 4s, max 3 attempts)

---

## Supported Chains & Protocols

| Chain | Protocol | Contract | Events Indexed |
|---|---|---|---|
| Sepolia | ERC20 | `0x8f15...1Be8` | `Transfer(address,address,uint256)` |
| Ethereum | Uniswap V2 | `0xB4e1...C9Dc` (WETH/USDC) | `Swap(address,uint256,uint256,uint256,uint256,address)` |
| Arbitrum | GMX V2 | `0xC8ee...22Fb` (EventEmitter) | `PositionIncrease` / `PositionDecrease` / `LiquidatePosition` |

---

## Quick Start

### Prerequisites
- **Java 8+**, Maven 3.8+
- **Docker Desktop** (for PostgreSQL, Prometheus, Grafana)
- **No API key needed** - public RPC endpoints (publicnode) by default; override via SEPOLIA_RPC_URL / ETHEREUM_RPC_URL / ARBITRUM_RPC_URL

### 1. Clone & Build

```bash
git clone https://github.com/Neilbbkk/lzb-indexer.git
cd lzb-indexer
mvn clean package -DskipTests
```

### 2. Start Infrastructure

```bash
docker compose up -d postgres prometheus grafana pgadmin
```

### 3. Configure RPC URLs

Edit `src/main/resources/application.yml`:
```yaml
app:
  chains:
    - name: sepolia
      rpc-url: "${SEPOLIA_RPC_URL:https://ethereum-sepolia.publicnode.com}"
      # ...
    - name: ethereum-uniswap
      rpc-url: "${ETHEREUM_RPC_URL:https://ethereum-rpc.publicnode.com}"
      # ...
    - name: arbitrum-gmx-vault
      rpc-url: "${ARBITRUM_RPC_URL:https://arb1.arbitrum.io/rpc}"
      # ...
```

### 4. Run

```bash
java -jar target/lzb-indexer-1.0.0.jar
```

> Optional: on IPv6-only networks add `-Djava.net.preferIPv4Stack=true` (run in cmd/bash; PowerShell mangles this flag).

### 5. Open Dashboard

```
http://localhost:8080
```

---

## API Reference

### Dashboard

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/dashboard/overview` | All chains status + total event counts |

### Swaps (Uniswap V2)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/swaps?chain=&sender=&page=&size=` | Paginated swap events |
| `GET` | `/api/swaps/recent?chain=&limit=` | Most recent N swaps |

### Transfers (ERC20)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/transfers?address=&chain=&page=&size=` | Transfers by address |
| `GET` | `/api/transfers/recent?chain=&limit=` | Most recent N transfers |

### GMX Positions

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/gmx/positions/{address}?chain=` | Positions by account |
| `GET` | `/api/gmx/positions/open?chain=` | All open positions |
| `GET` | `/api/gmx/stats?chain=` | Position statistics |
| `GET` | `/api/gmx/history/recent?chain=&limit=` | Most recent position events |

### Indexer Status

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/indexer/status?chain=` | Scanner status per chain |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

### WebSocket

```
STOMP over SockJS: ws://localhost:8080/ws
Subscribe: /topic/events
```

---

## Monitoring

### Grafana
```
URL: http://localhost:3100
User: admin / Password: admin
Dashboard: "LZB Indexer"
```

### Prometheus
```
URL: http://localhost:9090
Scrapes /actuator/prometheus every 5s
```

### pgAdmin
```
URL: http://localhost:5050
Email: admin@admin.com / Password: admin
```

---

## Integration Tests (Anvil)

> Actuator endpoints are protected by Basic Auth (`admin / admin123`, overridable via `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD`); `/actuator/health` stays open. WebSocket `/ws` accepts only `localhost` / `127.0.0.1` origins.

`mvn test` runs 8 integration tests (ERC20 / GMX) that require a local Anvil test chain:

- Anvil listens on `http://localhost:8545`, chain id `31337`
- Restart Anvil before every test run; otherwise the block height keeps accumulating past the scan window (page-size=100) and new transactions are not found

```powershell
# Restart Anvil (run before each test run)
Get-Process -Name anvil -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Process -FilePath 'D:\lzkcomp\foundry\anvil.exe' -ArgumentList '--port','8545','--chain-id','31337' -WindowStyle Hidden
```

Or use the helper script (restarts Anvil + compiles Solidity + runs the integration tests):

```powershell
powershell -File scripts/run-integration-test.ps1
```

## Database Schema

| Table | Purpose |
|---|---|
| `token_transfers` | ERC20 Transfer events |
| `swap_events` | Uniswap V2 Swap events |
| `gmx_position_history` | Raw GMX position events (event sourcing) |
| `gmx_positions` | Aggregated current position state |
| `scanned_blocks` | Block hashes for reorg detection |
| `sync_checkpoints` | Per-chain scan progress |
| `sync_errors` | RPC/DECODE/DB error log |

---

## Project Structure

```
src/main/java/com/lzb/indexer/
  config/          — WebSocketConfig, ChainProperties
  controller/      — REST API (6 controllers, 10+ endpoints)
  domain/
    entity/        — JPA entities (TokenTransfer, SwapEvent, GmxPosition*, ...)
    repository/    — Spring Data JPA repositories (7 total)
  dto/             — TransferResponse, NewEventsEvent
  scanner/         — BlockScanner, EventDecoder, ScannerScheduler
  service/         — TokenService, TransferQueryService, GmxPositionService, EventPushListener
```

---

## Tech Stack

- **Java 8** + **Spring Boot 2.7.18**
- **Web3j 4.9.8** — Ethereum JSON-RPC client
- **PostgreSQL 16** — primary database
- **H2** — test database
- **Docker Compose** — infrastructure orchestration
- **Prometheus + Grafana** — metrics & monitoring
- **Maven** — build & dependency management

---

## License

MIT
