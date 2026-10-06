# Orazaka AI Engine

> The Orazaka cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption.

**Layer:** Orazaka AI engine · **Version:** `1.0.0-SNAPSHOT` · **License:** Apache-2.0 ·
part of the [Orazaka platform](https://github.com/krizaka/orazaka) by [Krizaka](https://krizaka.com)

## What it provides

| Module | Tier | Role |
|:---|:---|:---|
| `orazaka-core` | SDK | Cognitive engine: `AiClient`, provider mesh (DB-driven routing), agent loop, interceptor contract. Web/DB-agnostic. |
| `orazaka-interceptors` | SDK | Ordered, short-circuitable pipeline: security, token, context, translation, enrichment, validation, governance. |
| `orazaka-tools` | SDK | MCP client, RAG, sandbox. |
| `orazaka-business` | Owned | App factory: `Intention` → `UseCase`, deterministic DAG or saga. |
| `orazaka-persistence-app` | Owned | CQRS persistence of the AI state (chat, jobs, models, configs, outbox). |
| `orazaka-persistence-bridge` | Owned | Adapters from core outbound ports to persistence-app. |
| `orazaka-assets` | SDK | Envelope encryption of generated assets. |
| `infra/initdb/60-governance.sql` | — | Interceptor policies (config plane). |

Hosts: [orazaka-conversation-service](https://github.com/krizaka/orazaka-conversation-service) (interactive) and
[orazaka-job-service](https://github.com/krizaka/orazaka-job-service) (async).

## Use it

```xml
<dependency>
    <groupId>com.orazaka</groupId>
    <artifactId>orazaka-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Position in the platform

| | |
|:---|:---|
| Depends on | [`orazaka-build`](https://github.com/krizaka/orazaka-build) · [`orazaka-contracts`](https://github.com/krizaka/orazaka-contracts) · [`orazaka-billing`](https://github.com/krizaka/orazaka-billing) · [`orazaka-studio`](https://github.com/krizaka/orazaka-studio) |
| Used by | [`orazaka-conversation-service`](https://github.com/krizaka/orazaka-conversation-service) · [`orazaka-job-service`](https://github.com/krizaka/orazaka-job-service) |
| Workspace path | `orazaka-libs/orazaka-ai-engine` |

## Build

**Inside the Orazaka workspace** (recommended — every dependency is built from source):

```bash
git clone https://github.com/krizaka/orazaka.git && cd orazaka
node scripts/workspace.mjs clone          # clones every repository at its workspace path
./mvnw -f orazaka-libs/orazaka-ai-engine/pom.xml verify
```

**Standalone** — upstream artifacts must be in `~/.m2` (built by the workspace) or resolvable from
GitHub Packages (`https://maven.pkg.github.com/krizaka/<repository>`, see the
[workspace README](https://github.com/krizaka/orazaka#consuming-packages)):

```bash
./mvnw verify
```

Requirements: JDK 21, Docker (Testcontainers integration tests).

## Governance

This repository follows the Orazaka governance contract — [AGENTS.md](https://github.com/krizaka/orazaka/blob/main/AGENTS.md)
in the workspace is normative; the local [AGENTS.md](AGENTS.md) only scopes it to this repository.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
