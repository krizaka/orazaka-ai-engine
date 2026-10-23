<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/orazaka-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/orazaka-light.svg" alt="Orazaka" width="96">
</picture>

# Orazaka AI Engine

**The AI that never leaves home.**

The Orazaka cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption.

[![CI](https://github.com/krizaka/orazaka-ai-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/orazaka-ai-engine/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/orazaka-ai-engine/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/orazaka-ai-engine)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Orazaka](https://img.shields.io/badge/part%20of-Orazaka-f67e23)](https://github.com/krizaka/orazaka#repositories)
[![Docs](https://img.shields.io/badge/docs-krizaka.com-f67e23)](https://www.krizaka.com/en/products/orazaka)

[Documentation](https://www.krizaka.com/en/products/orazaka) · [Website](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

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
    <groupId>com.krizaka.orazaka</groupId>
    <artifactId>orazaka-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Route a step by policy

`StepRouter` takes a `StepRoutingRequest(stepId, capability, policy)`, a mandatory
`StepModelRecorder`, and a synchronous model invocation. Omitting the policy defaults to
`SOVEREIGN_LOCAL_ONLY`; only catalog rows explicitly declaring `runs_on = 'local'` qualify.
`COST_OPTIMIZED` and `LATENCY_FIRST` use the order the admin declares, not a hardcoded cost or
latency formula.

The existing `orazaka_runtime_config` table stores one row per capability/policy:

| `config_key` | `config_value` (JSON array, primary first) |
|:---|:---|
| `routing.text.generate.SOVEREIGN_LOCAL_ONLY` | `["local-primary","local-secondary"]` |
| `routing.text.generate.COST_OPTIMIZED` | `["economical-model","local-secondary"]` |
| `routing.text.generate.LATENCY_FIRST` | `["fast-model","local-secondary"]` |

These names are examples: the admin supplies names from `orazaka_models`. An empty array
disables a route. Missing/malformed configuration fails closed; it never uses a global default
or another policy. Policy rows and catalog eligibility are read on each execution, so an admin
edit needs no restart. Only `status = 'available'` models are attempted.

```java
var step = new StepRoutingRequest(stepId, "text.generate"); // sovereign by default
var result = stepRouter.execute(step, stepModelRecorder, modelName -> invokeModel(modelName));
```

The invocation must use the supplied model name and report provider unavailability as
`StepModelUnavailableException`. The synchronous `RoutedChatModel` adapter translates connection
failures, timeouts, and HTTP 502/503/504 into this portable signal. Only that signal advances to the next declared candidate;
validation, safety, billing, and recording failures propagate without retrying another model.
The recorder writes the selected name before each attempt, including the secondary on fallback.
Never wrap a lazy stream in this synchronous API: fallback must happen before output is emitted.

**Host integration:** this SDK does not own Studio's step table. The host supplies
`StepModelRecorder` to persist the selected `model_name` on its step and invokes `StepRouter`
from its executor. Existing `AiClient` calls do not implicitly opt into step routing; wiring
Studio/job execution to this new port belongs to those repositories. Interactive chat routing
is unchanged.

Public routing contracts carry Javadoc for the workspace documentation generator. Regenerate
the reference inside the assembled workspace with `orazaka docs build` and `orazaka docs sync`;
the generator and generated documentation are not present in this standalone repository.

## External content and secrets

- **Text the engine did not write is data, never an instruction.** What an MCP server returns and what the knowledge
  sources hold reach the model in a delimited user-role block (`ExternalContent`), cleaned of scripts and invisible
  characters, with one fixed rule of our own saying a block cannot change the instructions. No `SystemMessage` is built
  from external content (orazaka-ai-engine#57, ADR-087).
- **MCP server tokens are sealed** for their owner with the platform's master keyring (`SecretSealer`, ADR-054) before
  they reach `user_mcp_servers` / `platform_mcp_servers`; rows written in clear are sealed at startup, and without a
  keyring a token is refused, never stored in clear (#58).
- **`searchKnowledge`** searches the organisation's knowledge base, not the web (it was called `searchWeb`, #59).
  Searching and reading the web is ADR-087's grounded research.

## Grounded research: reading a page safely (ADR-087 slice 1)

`PageFetcher` (`orazaka-tools`, `PageFetchService`) reads one page for grounded research through an
administrator-declared `research_source`, with every guard of ADR-087 §4, in order:

1. research on (`research.enabled`) and the source enabled;
2. https, or http where the source allows it;
3. the source's allow and deny patterns, paths compared in canonical form (`/%70rivate` is `/private`);
4. the cache (`research_document`): a document read through **this** source, younger than the source's **current**
   freshness, whose final URL the source's current policy still allows — a hit never crosses sources;
5. the source's rate, then, on every hop, the host's rate;
6. robots.txt, always honoured, cached at most 24 h per origin and product token (RFC 9309); its own request follows
   redirects across authorities, and a robots.txt that cannot be read — a refused address included — allows nothing.

`PinnedHttpClient` then resolves each hop **once** and refuses any non-public address (loopback,
private, link-local and the metadata endpoint, CGNAT, IPv6 ULA, mapped forms…). It connects to exactly the address it
checked, so DNS rebinding cannot reach the connection. It re-checks every redirect, sends no cookies, caps the size,
and holds one deadline for the whole call (`research.fetch.timeout-ms`), so a body that trickles in stops with it.
`HtmlTextExtractor` decodes the bytes as the server said (the HTTP charset, else a BOM or `<meta charset>`), walks the
visible text in reading order — one paragraph per block, nothing hidden by `hidden`, `aria-hidden` or inline CSS —
and keeps the main content. A document is stored under its source, the URL asked for and the content hash, with its
final URL. Every call writes one `research_fetch` audit line, with a path hash, never the query. The limits are
`research.fetch.*` rows read at call time. Nothing calls the fetcher yet: the citations and the verifier are slice 2.

## Position in the platform

| | |
|:---|:---|
| Depends on | [`orazaka-build`](https://github.com/krizaka/orazaka-build) · [`orazaka-contracts`](https://github.com/krizaka/orazaka-contracts) · [`krizaka-billing`](https://github.com/krizaka/krizaka-billing) · [`orazaka-studio`](https://github.com/krizaka/orazaka-studio) |
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

---

<sub>A Krizaka product · [krizaka.com](https://www.krizaka.com) · Apache-2.0</sub>
