# orazaka-ai-engine — Governance scope (agent-neutral)

> This repository is one component of the **Orazaka platform**. The normative contract is
> [`AGENTS.md`](https://github.com/krizaka/orazaka/blob/main/AGENTS.md) at the root of the Orazaka workspace
> ([`krizaka/orazaka`](https://github.com/krizaka/orazaka)), together with its `.agent/rules/*`. When this repository
> is cloned inside the workspace (`orazaka-libs/orazaka-ai-engine`), that contract is loaded first and applies
> without exception. **No rule lives here** — this file only scopes it.

## Scope of this repository

- **Role:** The Orazaka cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption.
- **Layer:** Orazaka AI engine
- **Depends on:** orazaka-build, orazaka-contracts, orazaka-billing, orazaka-studio — never on another repository's Tier-3 implementation (AGENTS.md §2, [SEAM-002]).
- **Workspace path:** `orazaka-libs/orazaka-ai-engine`

## Definition of done

1. `./mvnw verify` is green (unit + Testcontainers ITs + this repository's `*GovernanceTest`).
2. Inside the workspace, the cross-repository rules are green as well
   (`./mvnw -f orazaka-libs/orazaka-ai-engine/pom.xml verify` from the workspace root).
3. Spotless (google-java-format) passes — it is bound to `validate`.
