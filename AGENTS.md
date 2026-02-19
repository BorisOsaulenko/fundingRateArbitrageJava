# Repository Guidelines

## Project Structure & Module Organization

- Core source code lives in `src/main/java/com/boris/fundingarbitrage`, organized by domain:
    - `exchange/` for exchange clients and implementations (`impl/binance`, `impl/okx`, etc.).
    - `model/` for contract, websocket patch, exchange, and arbitrage DTOs.
    - `monitor/` for runtime monitoring and aggregation.
    - `util/` for HTTP, WS, crypto, logging, and helpers.
- Kotlin utilities are in `src/main/kotlin`.
- Tests are in `src/test/java/exchange` with per-exchange suites under `exchange/impl/*`.
- Operational scripts are in `scripts/` (`run.sh`, `monitor_metrics.sh`, `profile_jcmd.sh`).

## Build, Test, and Development Commands

- `./scripts/run.sh ./gradlew test`: starts provided command with local env vars.
- `./gradlew test/run/build` should not be used directly; use the above script instead.
- `./gradlew testRest` to test REST API only.
- `./gradlew testWebsocket` to test WS API only. May take up to 10 minutes in total; up to 3 minutes for one test class.

## Coding Style & Naming Conventions

- Java/Kotlin style is IntelliJ-driven via `config/idea/intellij-style.xml`.
- Use tabs with width `2`, keep max line length near `100`.
- Class names: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep exchange-specific code inside its `exchange/impl/<exchange>/` package.
- Use exact field naming provided by exchange API docs for request handling; do not use fallback or inferred field
  names.

## Testing Guidelines

- Framework: JUnit Jupiter (JUnit 5).
- Name tests `*Test.java`; keep abstract contract tests in `src/test/java/exchange` and exchange concrete tests in
  `src/test/java/exchange/impl/<exchange>/`.
- Some tests are integration-style (`@Tag("integration")`) and require network/API credentials; document prerequisites
  in PRs when touching them.

## Commit & Pull Request Guidelines

- Recent history includes many `WIP` commits; prefer descriptive, imperative subjects (example:
  `okx: fix private ws login payload`).
- Keep commits scoped to one concern (exchange adapter, ws handling, test updates, etc.).
- PRs should include:
    - What changed and why.
    - Exchanges/features affected.
    - Commands run (`./gradlew test`, targeted test class, or metrics/profile script).
    - Relevant logs/metrics when behavior or performance changed.

## Security & Configuration Tips

- Never commit API keys, secrets, or local env files.
- Keep credentials external; use local environment loading patterns (as in `scripts/run.sh`).
