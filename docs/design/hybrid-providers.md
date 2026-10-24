# Hybrid providers — a paid provider beside the sovereign stack

Status: **Phase 1 — design, awaiting « validé » from Claude (CTO).** No code is written until then.
Issue: krizaka/orazaka-ai-engine#66. Scope: engine v2 only (Oussama, 2026-10-23).

> Orazaka sells total governance, and can also plug into paid providers for someone who wants both.
> The first real use is the Instagram cut of the brand film (krizaka/krizaka-com#374): footage from a
> proprietary video model, reached *through* Orazaka and governed by the same engine — routing policy,
> budget, audit, metering — and never called directly by a product or a script.

## 0. Findings that change the brief (read first)

1. **GitHub Models is retired.** The official docs, read 2026-10-23, state: « As of July 30, 2026,
   GitHub Models has been fully retired. The playground, model catalog, inference API, and bring your
   own key (BYOK) are no longer available to any customer. »
   <https://docs.github.com/en/github-models> (also reached from
   `/en/rest/models/inference`, which redirects there). A web search the same day agrees
   (retirement 2026-07-30, brownouts 2026-07-16 and 07-23). So the paid text provider **cannot be
   GitHub Models**. The design below keeps the part that mattered — *one OpenAI-compatible adapter
   reusable for any OpenAI-compatible endpoint* — and needs a different first endpoint: see §5, and
   the decision requested from the CTO.
2. **`ai_providers.api_key` is a plain column.** `AiProviderEntity.java:25-26` maps it as `String`
   with no converter, and `CatalogModelManagerImpl.java:131-137` returns it as stored. The brief says
   credentials go to « the encrypted provider credentials ». Today there is no such store for
   provider rows: the encryption that exists is for assets (`orazaka-assets`, `EnvelopeCodec`) and a
   per-user key port (`UserCredentialsProvider.java:6-12`). Encrypting provider credentials is a
   **missing v2 piece** and is a prerequisite of phase 2 (§7).
3. **I could not read the video vendors' docs.** Every vendor domain (ai.google.dev,
   docs.dev.runwayml.com, docs.fal.ai, replicate.com) is blocked from the agent sandbox, and the web
   search tool timed out on each retry. §4 therefore contains **no price, no rights clause and no
   latency number that I did not read**: each is marked `UNVERIFIED` and must be read from the cited
   official page by the CTO (or by me once access is allowed) before the recommendation is final.
4. **Three v2 pieces named in the brief do not live in this repository**: the exact cache,
   `studio.call` / the step output contract, and resume-from-a-step (grep for them in the repo finds
   nothing; the studio side is reached through the `StudioRunClient` port,
   `StudioRunUseCase.java:4,24-40`). This design uses them as contracts and states what it needs from
   each (§3.4); it does not re-implement them here.

## 1. Inventory — what can reach a paid provider today (file:line)

Paths are relative to `orazaka-core/src/main/java/com/krizaka/orazaka/core/` unless stated.

| Capability | Can go to a paid provider today? | How, and where |
|---|---|---|
| **Chat / text** | **Yes**, through the DB-driven path | `DynamicModelRouter.chatModel` (`infrastructure/provider/DynamicModelRouter.java:53-61`) resolves a model name to a `ModelEndpoint` (provider, base-url, api-key, model — `domain/model/ModelEndpoint.java:15`) via `CatalogModelEndpointResolver` (`infrastructure/provider/model/CatalogModelEndpointResolver.java:31-58`), then builds an OpenAI-compatible client in `UniversalProxyChatProvider.chatModel` (`infrastructure/provider/UniversalProxyChatProvider.java:38-49`). A hosted OpenAI-compatible endpoint is therefore **a row in `ai_providers` + a row in `orazaka_models`**, no code. Wrapped in `GuardedChatModel` (`DynamicModelRouter.java:98-107`) which calls `SpendGuard.authorize(CHAT)` (`infrastructure/spend/GuardedChatModel.java:25,33`). |
| **Embeddings** | **Yes** | `EmbeddingClientImpl` resolves a `ModelEndpoint` and calls `spendGuard.authorize(EMBEDDING, endpoint)` (`infrastructure/adapter/ai/EmbeddingClientImpl.java:89`). Same rows. |
| **Image** | **Only the local dialect** | `ImageGeneratorClientImpl` speaks the stable-diffusion.cpp dialect of the OpenAI images API (`infrastructure/adapter/ai/ImageGeneratorClientImpl.java:30-41`). No `SpendGuard` call in the file. A paid image provider needs its own adapter. |
| **Audio (TTS)** | Via the routed chat/TTS model, **not guarded** | `RoutedTextToSpeechModel` (`infrastructure/provider/model/RoutedTextToSpeechModel.java`); `AudioGeneratorClientImpl` delegates to `Engine` (`infrastructure/adapter/ai/AudioGeneratorClientImpl.java:15-19`). No `SpendGuard` call. |
| **Video generation** | **No** | There is **no video-generation port or adapter**. Only the config shape (`VideoGenerationConfig(provider, baseUrl)`, `infrastructure/config/CoreProperties.java:119-120`), read by the health prober (`infrastructure/host/InfrastructureProber.java:54`), and `SpendGuard.Kind.VIDEO` with a flat `0.60` USD estimate (`infrastructure/spend/SpendGuard.java:36-40,61`) that nothing calls. Video *analysis* (`LocalVideoProcessor`) is local keyframe extraction, unrelated. |

### The pieces a paid call already goes through

- **Routing rows.** `RuntimeStepRouteProvider.candidates` reads `routing.<capability>.<POLICY>` in
  `orazaka_runtime_config` — a JSON array of model names, primary first
  (`infrastructure/adapter/routing/RuntimeStepRouteProvider.java:23-25,43-63`). A model is a
  *sovereign* candidate only when its catalogue row says `runs_on = local` (`:59`); available only
  when `status = available` (`:60`). A missing key yields **no candidate and a refusal**, never a
  catalogue default (`:46-48`).
- **The router.** `PolicyStepRouter.execute` (`application/routing/PolicyStepRouter.java:32-61`)
  skips unavailable candidates and, under `SOVEREIGN_LOCAL_ONLY`, every non-sovereign one (`:41-46`);
  it records the chosen model **before** each attempt (`:48`) through `StepModelRecorder.recordModel`
  (`domain/ports/outbound/StepModelRecorder.java:8-14`); retries only on
  `StepModelUnavailableException` (`:51-52`); otherwise fails closed (`:56-60`).
  `RoutingPolicy` has three values: `SOVEREIGN_LOCAL_ONLY`, `COST_OPTIMIZED`, `LATENCY_FIRST`
  (`domain/model/RoutingPolicy.java:4-11`); a missing policy defaults to sovereign
  (`domain/model/StepRoutingRequest.java:24-26`).
- **Credentials.** Provider key: `ai_providers.api_key`, plaintext (§0.2). Per-user commercial keys:
  `UserCredentialsProvider.getDecryptedApiKey` (`domain/ports/outbound/UserCredentialsProvider.java:19`),
  used by the **legacy** `AbstractEngine.resolveActiveModel` (`application/engine/AbstractEngine.java:197-213`),
  which classifies by provider name and **silently falls back to the local model** when no key exists.
  That path is *not* extended (§3.5).
- **Pricing and metering.** Catalogue `price_cents` and `typical_seconds` exist on the model row
  (`CatalogModelManagerImpl.java:88-98`). The pricebook, holds and settlement are in
  `krizaka-billing`: `EntitlementInterceptor` takes a hold (`.../governance/EntitlementInterceptor.java:112-127`;
  a Studio run takes one hold for the whole run, `:100-108`), `ChatSettlementListener` settles it
  against measured usage or releases it when no usage is reported
  (`.../governance/ChatSettlementListener.java:43-61`), with an idempotency key from the hold
  (`:74`). `BillableCapability` is the billing enum (`EntitlementInterceptor.java:4`); `VIDEO`
  exists in the business `Capability` (`orazaka-business/.../api/Capability.java:11`).
- **The day ceiling.** `SpendGuard` is an **in-memory, per-instance, global** daily USD ceiling with
  flat per-kind estimates (`infrastructure/spend/SpendGuard.java:16-29,79-107`; locality guessed from names/hosts at `:122`). It is a circuit
  breaker, not an accounting, and has no notion of workspace, capability or run.

### What is missing for « paid beside sovereign »

1. No way to *allow* a non-sovereign candidate: `RoutingPolicy` cannot say « paid allowed ».
2. No per-workspace, per-capability setting; routing keys are global (`routing.<capability>.<POLICY>`).
3. No per-run / per-month cap, only the global daily `SpendGuard`.
4. No data-class check at routing time (`SENSITIVE`/`REGULATED` appear only as pack controls —
   `ScopeGuardInterceptor`, `AbstractEngine.java:132` — never as a routing input).
5. No consent record consulted by the router.
6. No video port, adapter, pricebook row or async-job handling.
7. Provider credentials are not encrypted.

## 2. The hybrid model

### 2.1 Admin policy per workspace and per capability

A new value of the existing runtime settings (admin runtime settings of engine v2,
`orazaka_runtime_config`, read through `RuntimeConfigProvider`), **not** a code heuristic and not a
new table for the policy itself:

```
hybrid.<workspace>.<capability>.mode      = SOVEREIGN_ONLY | PAID_ALLOWED | PAID_PREFERRED
hybrid.<workspace>.<capability>.runCapUsd   = decimal   (PAID_* only; required)
hybrid.<workspace>.<capability>.monthCapUsd = decimal   (PAID_* only; required)
hybrid.default.<capability>.*             = the platform default (mode defaults to SOVEREIGN_ONLY)
```

`<capability>` ∈ `chat, text, image, video, audio, embeddings` (the brief's six). Resolution order:
workspace key → `hybrid.default` key → `SOVEREIGN_ONLY`. **An absent key is sovereign**, the same
fail-closed rule as `RuntimeStepRouteProvider.java:46-48`. A `PAID_*` mode without both caps is
rejected when saved *and* treated as `SOVEREIGN_ONLY` when read (a bad row can never open the door).

| Mode | Candidates the router may use | Order |
|---|---|---|
| `SOVEREIGN_ONLY` (default) | `runs_on = local` only — today's `SOVEREIGN_LOCAL_ONLY` | declared order |
| `PAID_ALLOWED` | sovereign first; a paid candidate only as a **declared fallback** after the sovereign ones are unavailable | sovereign, then paid |
| `PAID_PREFERRED` | paid first; sovereign remains the fallback | paid, then sovereign |

Mapping to the router: `RoutingPolicy` gains **`HYBRID_SOVEREIGN_FIRST`** and
**`HYBRID_PAID_FIRST`** to express routing order. Authorization is a separate candidate-eligibility
check: a non-sovereign candidate requires an effective workspace/capability mode other than
`SOVEREIGN_ONLY`, plus the consent and reservation gates below, whatever the routing policy is.
`SOVEREIGN_LOCAL_ONLY` remains an unconditional restriction and rejects every non-sovereign
candidate. `COST_OPTIMIZED` and `LATENCY_FIRST` keep their existing declared order, but a declared
hosted candidate is eligible only when workspace/capability authorization and all paid gates pass.
The routing rows stay in the DB (`routing.<capability>.<POLICY>` = ordered model names),
preserving « routing belongs in the database »; hybrid policies specify the order, not permission.

### 2.2 The data-class rule (Loi 25)

A run has a data class (`PUBLIC`/`INTERNAL`/`SENSITIVE`/`REGULATED`, the pack's `regulatory_class`
that already drives `ScopeGuardInterceptor` and the safety controls). The router receives it in
`StepRoutingRequest` (new field `dataClass`, default `SENSITIVE`: unknown is treated as sensitive).
The paid-provider disclosure check also considers whether the prompt or other submitted inputs contain
personal information; data class alone is not a proxy for that check. Use ADR-087's disclosure
classification rather than an ad-hoc string heuristic, and treat an indeterminate classification as
requiring consent.

> A `SENSITIVE` or `REGULATED` run, or any run whose disclosure contains personal information,
> **never** goes to a non-sovereign candidate unless recorded consent and its purpose cover
> *(workspace, capability, provider, disclosure)* — as in ADR-087. This is true for `PUBLIC` and
> `INTERNAL` data too. No applicable consent → the candidate is skipped; if no eligible candidate
> remains the step fails closed with `StepModelUnavailableException` carrying `consent_required`.

Consent is a read-only port backed by the consent record ADR-087 defines (owned by the Orazaka/billing
side; this repo only *asks*). It checks the applicable data-class and personal-information disclosure
requirements **at routing time, before cache lookup or any reservation/provider call**, and returns the
grant id and purpose stored with the routing reason. Revoking consent takes effect on the next step; it
never re-runs history. `PUBLIC` and `INTERNAL` runs without personal information need no consent but
still need the mode and the caps.

### 2.3 What the run shows (ADR-088 folded timeline)

`StepModelRecorder.recordModel(stepId, modelName)` is called *before* each attempt and a failure
aborts the step (`StepModelRecorder.java:8-14`). The design widens the record, keeping that
contract (mandatory, before inference, outside the fallback catch):

```
recordRoute(stepId, RouteDecision{
  provider, model, sovereign (bool), policy, reason, consentId?, consentPurpose?,
  estimatedUsd, paidSpendReservationId? })
```

`reason` is an enum-like string written by the router and never free text from a model:
`sovereign_default`, `sovereign_unavailable_fallback_paid`, `paid_preferred`, `consent_required`
(refusal), `run_cap_exceeded` (refusal), `month_cap_exceeded` (refusal), `policy_sovereign_only`
(refusal). The existing single-argument `recordModel` stays as a default method delegating to
`recordRoute` so current callers and tests do not change. The timeline folds consecutive attempts of
a step into one line with the winning provider/model and expands to the refusals. A paid step records
its consent purpose and paid-spend reservation id; the Studio shared credit hold is not a step
reservation.

### 2.4 Caps, hold, and actual cost

All money is **BigDecimal USD cents in the pricebook**, never a double (today's `SpendGuard` uses
doubles — it stays as the independent global circuit breaker and is *also* consulted).

1. **Price = a pricebook row.** One row per *(provider, model, unit)* in the billing pricebook
   (`krizaka-billing`), unit ∈ `token_in`, `token_out`, `video_second`, `image`, `audio_second`,
   `1k_embedding_tokens`. Seeded **disabled** (`enabled=false`). `price_cents` / `typical_seconds` on
   the catalogue row (`CatalogModelManagerImpl.java:88-98`) remain a *display* hint, not the billing
   source.
2. **Estimate** = `unit price × expected units` (video: requested seconds × `video_second`; text:
   `max_tokens` × `token_out` + prompt tokens × `token_in`). Never `SpendGuard`'s flat estimate.
3. **Policy and approval before reservation.** An approval gate (ADR-088) applies when the estimate
   exceeds the run's approval threshold. No provider call or spend reservation starts until any
   required approval is recorded.
4. **Atomic cap check-and-reserve.** A billing-side operation checks and reserves the estimate in one
   transaction for both the run and month caps. For each scope, available headroom is the cap minus
   settled paid-provider spend and outstanding paid-provider step allocations; concurrent workers
   must serialize on the same ledger scope (or use an equivalent atomic conditional update). A
   duplicate reservation key is idempotent. Failure to fit either cap refuses with
   `run_cap_exceeded` / `month_cap_exceeded`. The reservation is a paid-provider spend allocation,
   not a credit hold. It is counted once in each applicable cap, regardless of whether Studio also has
   its run-level credit hold.
5. **Keep Studio's shared credit hold saga-owned.** A standalone call may use its own ordinary billing
   hold, which that call settles or releases. A Studio run has one shared credit hold: individual steps
   never settle or release it. Each paid Studio step records measured usage/cost and finalizes only its
   separate provider-spend allocation; the run saga persists step measurements and alone performs the
   terminal `settleAggregate`/release for the shared hold, idempotently by run id (ADR-041). Do not
   count that shared credit hold as another paid-provider allocation.
6. **Finalize only known outcomes.** On a confirmed completed call, record provider-reported usage
   (video: delivered duration) × the pinned pricebook row, settle a standalone call's own hold when
   applicable, and replace the paid-spend allocation with actual spend, releasing unused headroom.
   Record actual spend even if it exceeds the estimate/cap and block later reservations; the provider
   charge cannot be undone. Release a provider-spend allocation, or a standalone call's hold, only
   when the provider confirms the operation is terminal and non-billable. A timeout, missing usage,
   cancellation request, or lost response is not proof of zero charge: keep the allocation open and
   reconcile/recover the operation. No step-level path may settle or release Studio's shared hold.
7. Month and run cap totals come from the **ledger** (settled spend + outstanding paid-step
   allocations), not memory, so a restart or a second instance cannot reset them (the weakness
   `SpendGuard.java:23-26` documents). `SpendGuard` remains an independent global circuit breaker.

### 2.5 Exact cache, resume, and `studio.call`

- **Exact cache.** Key = authenticated `(actorId, workspaceId)` plus hash of *(provider, model,
  canonicalised request, seed, data class, output contract version)*. Lookup is always scoped to the
  same actor and workspace; it never crosses either boundary. Check policy and consent eligibility
  first, then the exact cache, and only on a miss enforce caps and reserve spend. A hit costs nothing
  and records `reason=cache_hit`, even if a cap is now exhausted. Sensitive, regulated, or
  personal-information entries only hit under the same applicable consent grant and purpose.
- **Resume from a step.** Before submission, persist a stable submission identity and its paid-spend
  reservation on the step. Pass that identity to the provider as its idempotency key and use it to
  recover the original job after a lost response or worker crash. Resume polls the recovered/stored
  job rather than submitting a second billable operation. If the vendor supports neither idempotent
  submission nor lookup by that identity, an unknown outcome requires reconciliation and must never
  trigger automatic resubmission. Keep the reservation open until the outcome is known; release only
  a confirmed non-billable operation. Completed siblings still use their stored outputs.
- **`studio.call` and the step output contract.** The video step declares output
  `{assetId, mimeType, durationMs, width, height, provider, model, costCents}`; the clip is written
  through `orazaka-assets` (encrypted, `DurableAssetStore`) — the provider's temporary URL is
  downloaded immediately and never stored or returned to a product.

### 2.6 Needed from the v2 pieces not in this repo

If any is absent when phase 2 starts, it is **added to v2** (never routed around):
(a) consent port data (ADR-087), (b) per-run budget and approval gate (ADR-088), (c) exact cache
keyed as above, (d) step-level stored job id for resume, (e) a pricebook with the units in §2.4.1,
(f) the widened route record of §2.3 on the studio timeline, (g) atomic paid-spend reservations and
their run/month ledger, distinct from the Studio credit hold, (h) persisted submission identity plus
vendor idempotency/lookup or manual reconciliation for unknown outcomes.

## 3. Architecture

### 3.1 Ports (domain, in `orazaka-core`)

```java
public interface VideoGenerationProvider {
  VideoJobHandle submit(VideoRequest request, String submissionId);
  SubmissionRecovery recover(String submissionId);
  VideoJobStatus poll(VideoJobHandle handle);
  void cancel(VideoJobHandle handle);
}
enum SubmissionRecoveryStatus { FOUND, CONFIRMED_NOT_FOUND, UNSUPPORTED, UNKNOWN }
record SubmissionRecovery(SubmissionRecoveryStatus status, VideoJobHandle handle) {}
record VideoRequest(String prompt, int durationSeconds, AspectRatio aspect /* 9:16 */, Long seed, String model) {}
```

`submissionId` is minted and persisted before `submit` and is stable across retries. Adapters use it
with vendor-supported idempotency or lookup. `recover` distinguishes a found job, a vendor-confirmed
absence, an unsupported lookup, and an unknown outcome; unsupported or unknown is never treated as
absence. If neither recovery mechanism exists, the orchestrator marks the step for reconciliation and
does not resubmit. Poll and cancel timeouts leave the reservation open; a cancel request alone does
not establish a non-billable outcome.

An orchestrator in the application layer (`PaidVideoStep`) does: route and consent eligibility →
owner/workspace-scoped exact cache → approval and atomic cap reservation → persist submission identity
→ `submit`/recover → poll with back-off (or webhook callback where the provider offers it) → download
→ store asset → record cost and finalize only the provider-spend allocation (and a standalone call's
own hold, if applicable). A Studio step never settles/releases the shared run hold; only its saga does
that terminally. Provider adapters sit in `infrastructure/adapter/video/` and contain no policy.
`ImageGeneratorClient` / `AudioGeneratorClient` already show the port pattern
(`domain/ports/outbound/ImageGeneratorClient.java`, `.../AudioGeneratorClient.java`).

### 3.2 Provider and model rows

`ai_providers(name, base_url, api_key)` + `orazaka_models(provider_name, runs_on, status, …)`.
Seed: `runs_on = remote`, `status = disabled`. A video provider also needs its protocol
(`openai`, `veo`, `runway`, …) so the router can pick the adapter from the row; add a nullable
`protocol` column rather than infer it from the name (today `SpendGuard.LOCAL_PROVIDERS` infers
locality from names, `SpendGuard.java:42-43` — we do not add more of that).

### 3.3 Sovereignty is a fact from the row, not the name

A candidate is sovereign only when `runs_on = local` (as `RuntimeStepRouteProvider.java:59`). The
paid seed rows have `runs_on = remote`. `SpendGuard.isLocal` (name/host heuristic) is **not** used
to decide sovereignty; it keeps only its circuit-breaker role.

### 3.4 One OpenAI-compatible adapter (text)

The existing `UniversalProxyChatProvider` already *is* that adapter (`:38-49`): any
OpenAI-compatible endpoint is a provider row whose `base_url` ends in `/v1` (appended when absent,
`:60-66`). Phase 2 adds **no new text adapter**; it adds a seed row, the caps, the consent check and
the pricebook rows, and one test proving a non-`/v1` endpoint path works. Two cautions found while
reading: the `com.openai` client appends `/chat/completions` after the base URL (`:51-59`), so an
endpoint whose path is not `<base>/chat/completions` (as GitHub Models' was) needs a
`chat_path` override on the provider row; and `GuardedChatModel` charges `SpendGuard` per call with a
flat estimate — for hybrid it is complemented, not replaced.

### 3.5 What must not gain a provider

- `AbstractEngine.resolveActiveModel` (`:197-213`) and `ProviderClassifier` — the pre-v2 per-user
  commercial path that silently falls back to local. It is left as is and **not** used for hybrid.
- Direct SDK calls from a service, product BFFs, scripts. The video adapter is reachable only through
  the router + orchestrator; a `GovernanceTest` ArchUnit rule (existing rules style, no
  `allowEmptyShould`) asserts no class outside `infrastructure.adapter.video` imports a vendor video
  SDK/HTTP client, and that `VideoGenerationProvider` is only injected by the orchestrator.

## 4. First paid video provider — comparison

Required facts per option: API shape, price per second, output rights, 9:16, latency, async jobs and
webhooks. **Every vendor fact below is `UNVERIFIED`**: the official pages could not be fetched from
the sandbox on 2026-10-23 (DNS blocked; see §0.3). The URLs are the pages that must be read; I will
not quote a number I did not read. Rights and price are the two facts that decide the choice.

| | A. Google Veo via the Gemini API | B. Runway API | C. Hosted aggregator (fal.ai queue API) |
|---|---|---|---|
| Official docs (to read) | <https://ai.google.dev/gemini-api/docs/video> | <https://docs.dev.runwayml.com/> | <https://docs.fal.ai/model-apis/model-endpoints/queue> |
| Price page (to read) | <https://ai.google.dev/gemini-api/docs/pricing> | <https://docs.dev.runwayml.com/guides/pricing/> | the model's page on <https://fal.ai/models> |
| Price / second | UNVERIFIED | UNVERIFIED (credit-based) | UNVERIFIED (per model) |
| Output rights / commercial use | UNVERIFIED (check terms + any watermark) | UNVERIFIED | UNVERIFIED — **inherits the underlying model's terms**; an extra party in the data path |
| 9:16 vertical | UNVERIFIED | UNVERIFIED | UNVERIFIED (per model) |
| Latency | UNVERIFIED | UNVERIFIED | UNVERIFIED |
| Async jobs / webhooks | UNVERIFIED | UNVERIFIED | UNVERIFIED |
| Governance fit (qualitative, from the architecture, not a vendor claim) | A single vendor under a single contract; one fewer party seeing the prompt than C | Single vendor; video-first API | Fastest to add *several* models behind one adapter, but adds an intermediary subprocessor, which weighs against Loi 25 posture |

**Recommendation (provisional, pending the UNVERIFIED cells):** go **direct to a model vendor
(Option A or B) rather than the aggregator**, because the data-class rule is easier to defend with
one fewer subprocessor, and because the port (§3.1) makes the choice reversible — a second adapter
is a new row plus a class. The CTO should read the two price pages above, confirm 9:16 and the
commercial-use terms for the brand film, and name the winner; I then fill the table with the date
read. The adapter ships with a recorded-answer contract test either way (§6).

## 5. First paid text provider

**GitHub Models cannot be the provider** (§0.1; retired 2026-07-30). Consequently the brief's
« fine-grained token with only `Models: read` » has no target either. The design keeps what survives:

- One provider row + the existing OpenAI-compatible adapter (§3.4), reusable for any
  OpenAI-compatible endpoint (including a self-hosted one, which is `runs_on=local` and sovereign).
- A credential scoped to the minimum the provider offers; never a full-scope token.
- Its docs (rate limits, paid usage, data terms) are read and cited when the provider is chosen.

**Decision requested from the CTO/Oussama:** choose the replacement first paid text endpoint
(GitHub's own pointers are Azure AI Foundry, `https://ai.azure.com/`, or a direct OpenAI-compatible
API). Until then the phase-2 text work is limited to the generic row + gates, which need no vendor.

## 6. Credentials

1. Entered by the founder through the **admin console**; never in a file, env var, test, log or the
   repository (`MediaStorageProperties.toString` masks its key, `MediaStorageProperties.java:55`; the
   same rule: no `toString` or log of a key).
2. Stored **encrypted** (§0.2 — the missing piece). Proposal: reuse the envelope scheme of
   `orazaka-assets` (`EnvelopeCodec`, `EncryptedAssetService`) behind a small
   `ProviderCredentialCipher` port and an `AttributeConverter` on `AiProviderEntity.apiKey`, a
   migration that encrypts existing rows, and key material from the deployment's KMS/secret store.
   `ModelEndpoint.apiKey` stays in memory only, in the request path.
3. A key is never returned by an admin read API (write-only + `last4`).
4. Rotation is an edit; `DynamicModelRouter.evictAll()` (`:93-96`) is called after it.

## 7. Phase 2 tests-first plan (after « validé »)

**Tests first — recorded API answers only, no network, no key** (recorded fixtures under
`**/fixture/**`). Add these failing regression tests before implementation:

- Candidate eligibility is independent of routing order: workspace authorization permits declared
  paid candidates under `COST_OPTIMIZED`/`LATENCY_FIRST`; `SOVEREIGN_LOCAL_ONLY` still rejects them;
  missing/invalid settings remain sovereign.
- Consent is required for `SENSITIVE`/`REGULATED` and independently for personal information in a
  `PUBLIC`/`INTERNAL` disclosure; missing purpose/grant prevents cache use and provider contact, while
  a `PUBLIC`/`INTERNAL` disclosure with no personal information needs no consent.
- With a $3 run cap, an outstanding $2 step allocation makes another $2 reservation fail; concurrent
  workers cannot exceed either run or month cap, and duplicate reservation requests are idempotent.
- A Studio step finalizes its provider-spend allocation and persists measured usage without settling
  or releasing the shared credit hold; only the run saga's terminal `settleAggregate`/release closes
  that hold. A standalone call still settles/releases its own hold.
- A paid step checks the owner- and workspace-scoped exact cache after policy/consent eligibility but
  before cap enforcement: an eligible hit succeeds with an exhausted cap and performs no reservation;
  another actor or workspace cannot hit it.
- A lost submit response/worker crash recovers the original job by stable submission identity and
  does not submit twice; when the vendor lacks idempotency and lookup, unknown outcomes remain
  reserved for reconciliation. Poll/cancel timeout does not release a reservation; only a confirmed
  non-billable terminal outcome does.
- An async job polls `QUEUED → RUNNING → SUCCEEDED`; confirmed non-billable failures release only the
  paid-spend allocation (and a standalone call's own hold), never a Studio shared hold. Provider
  charges and missing/unknown usage remain recorded/reconciled rather than silently released.
- A key never appears in logs or `toString`. `./mvnw verify` and Spotless stay green.

Then implement:

1. Encrypt provider credentials (§6) — prerequisite, own PR.
2. `RoutingPolicy` hybrid values, `StepRoutingRequest.dataClass`, `RouteDecision` recording,
   `PaidProviderConsent` port, runtime-settings keys (§2).
3. Atomic paid-spend reservations, Studio's separate shared hold, standalone settlement and the
   pricebook rows seeded disabled (§2.4).
4. `VideoGenerationProvider` port, orchestrator, first adapter, asset write-through (§3).
5. Provider/model seed rows (`enabled=false`, `runs_on=remote`), generic OpenAI-compatible row.
6. ArchUnit governance rule (§3.5); frozen-store untouched.
7. README + generated docs updated.

**First live call**: made by the CTO with Oussama's key, estimated first, **capped at 3 USD**
(`runCapUsd=3`), through the admin console settings — not a code path.

## 8. Open questions for the CTO

1. Confirm the video vendor after reading the two price pages and the commercial-use terms (§4).
2. Replacement for GitHub Models as the first paid text endpoint (§5).
3. Where ADR-087's consent record lives and its read API; and the ADR-088 approval-threshold setting
   name, so §2.2 and §2.4.3 bind to them rather than to my guess.
4. Agreement that `hybrid.*` is stored in `orazaka_runtime_config` (the v2 admin runtime settings)
   and that `RoutingPolicy` is extended, rather than a new policy table.
