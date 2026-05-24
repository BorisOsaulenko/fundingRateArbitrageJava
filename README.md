# FundingArbitrage

> Status: still in development. Expect incomplete features, rough edges, and breaking changes.

FundingArbitrage is a Java/Kotlin trading bot focused on cross-exchange funding arbitrage. It collects market data from
multiple exchanges, filters tradable coins, monitors live spot and futures books, evaluates arbitrage opportunities, and
runs trade logic around entry, funding capture, and exit.

## What It Does

At a high level the application:

- loads exchange clients and credentials
- fetches available coins and exchange-specific market metadata
- filters out markets that do not meet volume, affordability, or trading-state requirements
- subscribes to live market data streams
- analyzes cross-exchange opportunities
- applies pre-trade rules to decide whether an opportunity is worth entering
- applies in-trade rules to decide when an open position should be closed

The current `App` wiring is centered around futures funding arbitrage and uses `FuturesPreTradeStrategy` together with
`ClassicInTradeStrategy`.

## Environment Variables

The project reads API credentials from environment variables. Set only the exchanges you plan to enable.

### Binance

- `BINANCE_API_KEY`
- `BINANCE_PRIVATE_KEY`
- `BINANCE_PASSPHRASE`

`BINANCE_PRIVATE_KEY` is expected to contain the private key PEM content used by the Binance client.

### Bybit

- `BYBIT_API_KEY`
- `BYBIT_SECRET`

### Bitget

- `BITGET_API_KEY`
- `BITGET_SECRET`
- `BITGET_PASSPHRASE`

### Gate

- `GATE_API_KEY`
- `GATE_SECRET`
- `GATE_USER_ID`

### KuCoin

- `KUCOIN_API_KEY`
- `KUCOIN_SECRET`
- `KUCOIN_PASSPHRASE`

### OKX

- `OKX_API_KEY`
- `OKX_SECRET`
- `OKX_PASSPHRASE`

### WhiteBIT

- `WHITEBIT_API_KEY`
- `WHITEBIT_SECRET`

## Strategy Package Summary

The strategy layer lives under `src/main/java/com/boris/fundingarbitrage/strategy` and is split into two phases:
pre-trade selection and in-trade management.

### `pretradestrategy`

This package decides whether a candidate opportunity should be entered.

- `PreTradeStrategy` defines the interface for entry evaluation.
- `FuturesPreTradeStrategy` is the current production-oriented implementation for futures-vs-futures trades.

`FuturesPreTradeStrategy` computes:

- `OSpread`: the opening spread between long ask and short bid
- `FSpread`: the expected funding edge at the nearest settlement
- total taker fees for opening and closing both legs
- `expectedGain`: `OSpread + FSpread - fees`

It only permits futures/futures combinations. It also requires the trade to be close enough to the funding event and
above internal spread thresholds before `goodToEnter(...)` returns `true`.

### `intradestrategy`

This package manages a position after entry.

- `InTradeStrategy` is the abstract base type for funding-event tracking and exit decisions.
- `ClassicInTradeStrategy` is the current implementation used by `ProductionInTradeStrategyFactory`.

`ClassicInTradeStrategy`:

- records entry cost and entry fees
- updates running PnL when funding events occur
- checks current bid/ask and closing fees to decide whether the trade should be exited
- exits when estimated PnL clears a minimum threshold based on trade notional

### `TradeMarket` and direction modeling

- `TradeMarket` distinguishes `SPOT` and `FUTURES`
- `TradeDirections` is a simple record describing long/short market direction metadata

## Running

This repository uses Gradle. Typical commands:

```bash
./gradlew build
./gradlew test
```

The main application entrypoint is `src/main/java/com/boris/fundingarbitrage/App.java`.

## Notes

- Exchange connectivity and websocket behavior depend on valid credentials and exchange availability.
- Some test failures may be unrelated to README changes and can depend on current exchange/mock setup.
