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
`research.fetch.*` rows read at call time.
**Citations and the verifier (slice 2).** A grounded answer is `{answer, claims[], sources[]}`: each claim carries
citations, and each citation has a URL, a verbatim quote, a content hash and a fetch time. `CitationVerificationService`
is deterministic, with no model involved. A claim is `VERIFIED` only when one of its citations names a document **this
answer read** (the grounding stage records them in the `PromptContext`; the store is asked through that document's own
source), its quote — at most `research.quote.max-chars` — appears in that document after `QuoteNormalizer` (NFC, case
folding, French typography: « » “ ” ’, U+202F/U+00A0, œ/æ, dashes, the ellipsis), **and** every number, date and
reference the claim states appears in the quote, the document's title or the heading of the quote's section — a genuine
quote under a wrong number stays `TO_VALIDATE/UNSUPPORTED`. Any other claim is `TO_VALIDATE` with its reason
(`NO_SOURCE`, `URL_UNRESOLVED`, `QUOTE_NOT_FOUND`, `UNSUPPORTED`), or dropped in `STRICT`. Every URL, `owner/repo` or
statute stated in the model's prose that no verified claim covers becomes a `TO_VALIDATE` line (`UNLISTED_REFERENCE`).
**The served `answer` is rendered from the claims** — verified claims as they are, `[TO_VALIDATE]` prefixes in `MARK`,
verified claims only in `STRICT`, empty with no claims — never the model's prose, which is only scanned.
Numbers and dates match as **whole tokens** (« 14 » is not in « 140 » or « 2024 »), references **by identity**
(`art.` and `article` are one word, « art. 1 » is not « art. 12 », a URL only as the complete cited URL, case-sensitive).
A verified claim keeps **only the citations that passed**, and coverage trusts only those; unlisted references are added
in `MARK` only.
`CitationVerifierInterceptor` applies it to a citation-shaped answer and leaves any other answer untouched. It **fails
closed** (a verification that throws stops the turn), and it checks its own switches, because `Engine` hands every
interceptor bean to the output screen: `research.enabled` and its own `pipeline_interceptor_config` row, both off as
seeded — off means no rewrite. Slice 5's `grounding` flag turns it on.

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
| **Integrity (#89)** | A version is served only when its text, normalised again by the corpus's own rule (`ContentHash`: NFC, whitespace collapsed; the historical note is never hashed), still gives the `content_hash` recorded with its row. `JpaLawCorpusStore` carries that recorded hash (`LawArticleVersion.recordedHash`), never a recomputed one, and `LawArticleVersion.intact()` compares the two. A mismatch is `LAW_INTEGRITY_ERROR` with no text in `lookupStatute`, and an unknown article in a retrieval (never handed to the model); it is logged with the article's key and both hashes, never the text. ArchUnit `LawCorpusReadRulesTest` holds it: only `LawStatuteLookupService` and `LawCorpusResearchService` read an article by day (`[LAW-001]`), and no store builds a version from its text alone (`[LAW-002]`). |
| **Read API use case (#89)** | `LawArticleReadService.read(LawArticleQuery, actorId)` is what the host's `GET /api/v1/law/articles` calls (the controller lives in `orazaka-conversation-service`; this library has none). `asOf` is **required** (an ISO day; missing or unreadable is refused, never read from a clock); `language` is `fr` or `en`; an optional `quote` is checked against the article text under the same normalisation (`QuoteCheck`: `VERIFIED` or `NOT_IN_ARTICLE`, no model). Metered as one `RESEARCH` `CALL` on provider `law-corpus` through the `ResearchMeter` port: an invalid question and a switched-off corpus are answered before anything is held; then the call is held, **settled only on `FOUND`** (whatever the quote check), released on `LAW_VERSION_UNKNOWN`, `LAW_INTEGRITY_ERROR` or a failed read. No `ResearchMeter` bean means `LAW_METERING_UNAVAILABLE`: a read is never served unmetered. A budget that cannot hold the call is `LAW_BUDGET_EXCEEDED`. |

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

## Retrieving the law through grounded research, and its cache (ADR-089 §2 and §7, #81)

`LawRetrievalService` (`orazaka-tools`) gives a kind of document the articles it is judged by, as verified claims. The kind's data
names the articles (`targets`, from the pack's `kinds/*.json`) and the question; the engine names none.

1. **Off unless three switches are on**: `law.corpus.enabled`, `research.enabled` and the `law-corpus` research source (all seeded
   off). The wiring is off too: `orazaka.law.retrieval.enabled=true` registers `LawRetrievalConfiguration`. A disabled call reads
   nothing, asks no model and costs nothing (`DISABLED`).
2. **The `law-corpus` provider reads the corpus, never the network** (`LawCorpusResearchService`). For the instruments and references
   the kind targets it reads `law_article_version` at `asOf` and offers each article as a `research_document`
   (`{text, contentHash, inForceFrom, officialUrl, importId}`; URL = the official page, hash = the article's, title = « CCQ-1991, art. 1931 »),
   so the ADR-087 `CitationVerifier` checks every quote **unchanged**. An article with no version in force that day is
   `LAW_VERSION_UNKNOWN`, listed in `unknown`, never replaced by a later text; no article at all means `NO_ARTICLES` and no model call.
   `law.retrieval.max-articles` bounds what one retrieval keeps. **`asOf` is required** (a request without it is refused): the engine never reads "today" from a clock's own zone; the caller passes the document's date, or the calendar day in the jurisdiction's zone (`America/Toronto` for QC).
3. **The cache is `law_retrieval`**, keyed by jurisdiction, kind, **language** (claims written in French are never served for English) and the **as-of bucket** (the newest `in_force_from` among the articles).
   A row is served (`CACHED`: no model call, no charge) only when it is not `stale`, is younger than `law.retrieval.max-age-days`
   and cites exactly the article versions (`instrument`, `ref`, `language`, `contentHash`) the corpus holds for that day.
4. **A changed cited article marks the cache stale**: `JpaLawCorpusStore.replace` and `.close` set `stale` on every `law_retrieval` that
   cites the article (JSONB containment), in the same transaction as the change, and the next call recomputes. Saving a recomputed
   retrieval is one `INSERT … ON CONFLICT DO UPDATE`: two calls computing the same key leave one row and fail neither.
5. **Each computed retrieval is one metered grounded call**, a `RESEARCH` operation of unit `CALL`, priced by the pricebook row of the
   provider key (`ResearchMeter.hold`, then `settle`; `release` if the model fails, never masking the original error), and counted
   against the run budget (ADR-088). A budget that cannot hold the call refuses it before the model is asked (`BUDGET_EXHAUSTED`,
   nothing cached). A cache hit is free.
6. **The model is a port** (`GroundedClaimsModel`): the only place a retrieval calls one. Verified claims are cached with their
   citations; `TO_VALIDATE` ones are kept as such and are not used by the derivation stage.

The model and the meter come from the grounded-research capability (ADR-087 slice 5, `orazaka.core.research.grounded`) and the run
budget (ADR-088). Until they are wired the service **fails closed**: no meter means the call is refused (`BUDGET_EXHAUSTED`), no
model means an error, never an unmetered call. Tests: `LawRetrievalServiceTest` (the golden case: a visit notice brings art. 1931 to
1933 and no rent article; a cached retrieval makes no model call, asserted on the model port; a changed article recomputes; off by
default; metering and the budget), `JpaLawRetrievalCacheIT` (JSONB, the stale mark in the same transaction, concurrent saves),
`LawRetrievalConfigurationTest`.

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
