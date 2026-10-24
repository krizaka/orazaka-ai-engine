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

## The law corpus: importers, manual import and `lookupStatute` (ADR-089 §2, #400)

The corpus is the dated, hashed text of the law the engine derives its checks from. It lives in the
**job-service tables** (`law_instrument`, `law_article_version`, `law_import`, created by
[orazaka-job-service](https://github.com/krizaka/orazaka-job-service) `infra/initdb/30-jobs-config.sql`), and the
engine reads and writes them through Spring Data JPA on its own data source, like the research tables: no second
connection pool (ADR-083). Nothing here runs while the setting `law.corpus.enabled` is false (the seeded default).

| | |
|:---|:---|
| **Importers** | `QuebecOpenDataSource` (the Données Québec statutes zip, conditional on its ETag, SHA-256 of the zip recorded) and `JusticeLawsXmlSource` (Justice Canada's `/eng/XML/<code>.xml` and `/fra/XML/<code>.xml`). Both implement `LawSource`, both use `LawStatuteXmlParser`, both send an honest user agent (`OrazakaLawWatcher/0.1`). Légis Québec and CanLII are not read. |
| **Watcher** | `LawWatchService` runs weekly (`orazaka.law.watcher.cron`, Monday 06:00 UTC) when `orazaka.law.watcher.enabled=true`: it imports every instrument of `law.import.instruments`, and every difference with the corpus opens one pull request in `krizaka/orazaka-packs` (a review file with the diff, both hashes and the citation). The first import of an instrument and language stores silently; an unchanged article opens nothing; a removed article is flagged. |
| **Manual import** | `LawManualImportService.importFile(ManualLawFile)`: the same parser and comparison, `mode = MANUAL`. `imported_by`, the address the file came from and the **SHA-256 of the file** are required (the service refuses the upload, and the table refuses the row, without them). Only an instrument of `law.import.instruments` is stored. The HTTP endpoint (`POST /api/v1/admin/law/imports`) belongs to the host that authenticates the administrator; it calls this service. |
| **`lookupStatute`** | `LawStatuteLookupService.lookupStatute(jurisdiction, instrument, ref, language, asOf)` returns the version in force on `asOf` (`in_force_from <= asOf < in_force_to`; no day means today) as `{text, contentHash, inForceFrom, officialUrl, importId}`, or `LAW_VERSION_UNKNOWN` (also: the article is unknown, the oldest text we hold took effect after `asOf`, or it was not in force then), or `LAW_CORPUS_DISABLED`. A later text is never read in place of an earlier one. As a model tool (`LookupStatuteTool`, arguments `jurisdiction`, `instrument`, `reference`, `asOf`, `language`) it is registered in the one `ToolRegistry` only with `orazaka.law.lookup.enabled=true`, because a registered tool is attached to every non-streaming request. |

- **Every import is a `law_import` row**: source, `AUTOMATED` or `MANUAL`, dataset address, ETag, SHA-256, the day the
  consolidation is current to, who, start and end, counts (`articles_new`, `articles_changed` (a removal counts here),
  `articles_unchanged`) and `status` (`RUNNING`, then `SUCCEEDED`, or `FAILED` when nothing could be read or a change
  was refused). A source that cannot be read leaves its `FAILED` row.
- **A changed text closes the old row (`in_force_to` = the new in-force day) and inserts the new one, in one
  transaction**, with a row lock on the open version: two imports cannot both replace it. A **reverted** text is a new
  dated row (the key is `(instrument_id, ref, language, in_force_from)`, krizaka/orazaka#401), never the old row
  reopened. A text that changes without a later in-force date is refused and opens no request.
- **A change is applied only once its review request is open** (the watcher), so a failed request is detected again by the
  next run. Without a request port (no watcher), changes are applied directly and the import row is the trace.
- **Settings are the `law.*` rows of `orazaka_runtime_config`**, read at each call: `law.corpus.enabled`,
  `law.import.instruments` (`QC:CCQ-1991,CA:E-15,...`; whole instruments, nothing else is stored) and
  `law.import.max-article-chars` (a longer article fails the whole import before anything is written).
  Static wiring only: `orazaka.law.watcher.enabled|cron|quebec.dataset-url|github.token|github.owner|repository|base-branch`
  (the krizaka-agents App token comes from the deployment's secrets, never from code) and `orazaka.law.lookup.enabled`.
- **`official_url_template`**: an instrument's citation address, with the placeholders `{ref}`, `{lang}`, `{lang3}`,
  `{acts}` and `{section}` (Justice Canada's paths differ by language). The importer writes it when it creates the
  instrument; the administrator can edit it.

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
