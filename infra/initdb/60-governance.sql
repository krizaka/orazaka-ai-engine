-- ============================================================================
-- ORAZAKA — Local DB bootstrap · 60 — INTERCEPTOR POLICY GOVERNANCE
-- ----------------------------------------------------------------------------
-- Future owner: the config plane (hosted by the Job Orchestration service;
-- extraction deferred). Sovereign Intent Mesh interceptor policies, predicates
-- (pure data — zero SpEL/scripts), and the audit/rollback version history.
-- ============================================================================

CREATE TABLE interceptor_policy (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interceptor_name VARCHAR(255)  NOT NULL,
    execution_order  INT           NOT NULL DEFAULT 0,
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_interceptor_policy_name UNIQUE (interceptor_name)
);

CREATE TABLE interceptor_policy_predicate (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID         NOT NULL REFERENCES interceptor_policy(id) ON DELETE CASCADE,
    field     VARCHAR(255) NOT NULL,
    operator  VARCHAR(50)  NOT NULL,
    value     VARCHAR(512) NOT NULL
);

CREATE INDEX idx_policy_predicate_policy_id
    ON interceptor_policy_predicate(policy_id);

CREATE TABLE policy_version_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id   UUID         NOT NULL REFERENCES interceptor_policy(id) ON DELETE CASCADE,
    snapshot    JSONB        NOT NULL,
    sha256_hash VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_policy_version_history_policy_id
    ON policy_version_history(policy_id);
CREATE INDEX idx_policy_version_history_created_at
    ON policy_version_history(created_at DESC);

-- ============================================================================
-- BASELINE INTERCEPTOR POLICY SEED DATA
-- Maps to AGENTS.md §5 Context-Matrix Pipeline. Zero "guest" references.
-- ============================================================================

INSERT INTO interceptor_policy (id, interceptor_name, execution_order, enabled) VALUES
('a0000001-0000-0000-0000-000000000001', 'UserContextResolver',            1,  TRUE),
('a0000001-0000-0000-0000-000000000002', 'SystemContextInjector',          2,  TRUE),
('a0000001-0000-0000-0000-000000000003', 'LanguageAlignmentInterceptor',   3,  TRUE),
('a0000001-0000-0000-0000-000000000005', 'MemoryInterceptor',              5,  TRUE),
('a0000001-0000-0000-0000-000000000006', 'RefinerInterceptor',             6,  TRUE),
('a0000001-0000-0000-0000-000000000007', 'RouterInterceptor',              7,  TRUE),
('a0000001-0000-0000-0000-000000000009', 'CostShieldInterceptor',          9,  TRUE),
('a0000001-0000-0000-0000-000000000099', 'ClosedLoopValidationInterceptor',       99, TRUE),
('a0000001-0000-0000-0000-000000000004', 'BrandContextInterceptor',        4,  TRUE)
ON CONFLICT (interceptor_name) DO NOTHING;

-- ScopeGuardInterceptor is deliberately absent from this table. It is a CORE_INTERCEPTOR_KEY in
-- PipelineRegistry, so it runs first in the non-bypassable Phase 1 whichever rows live here: a
-- control of the regulatory class is not an admin preference (ADR-051). Listing it would publish a
-- toggle the executor ignores for core keys, and an off switch that does not switch off is worse
-- than no switch at all. Running first is what makes a refusal free — after RefinerInterceptor and
-- RouterInterceptor the turn it refuses has already been paid for, and after MemoryInterceptor the
-- question is written into a history the pack said it would not hold.

-- BrandContextInterceptor: only for a request that carries an installed Studio (ADR-034 §9.1).
-- Predicate-activated rather than always-on so ordinary chat never pays for the lookup, and
-- so an admin can disable a Studio's brand voice without touching the blueprint.
INSERT INTO interceptor_policy_predicate (id, policy_id, field, operator, value) VALUES
('b0000001-0000-0000-0000-000000000004', 'a0000001-0000-0000-0000-000000000004',
 'preference.exists', 'EQUALS', 'orazaka.studio.brand.tone')
ON CONFLICT DO NOTHING;

-- CostShieldInterceptor: activate when budget < 50%
INSERT INTO interceptor_policy_predicate (id, policy_id, field, operator, value) VALUES
('b0000001-0000-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000009',
 'context.budgetRemaining', 'LESS_THAN', '0.50')
ON CONFLICT DO NOTHING;

-- CostShieldInterceptor: activate when memory pressure > 85%
INSERT INTO interceptor_policy_predicate (id, policy_id, field, operator, value) VALUES
('b0000001-0000-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000009',
 'context.memoryPressure', 'GREATER_THAN', '0.85')
ON CONFLICT DO NOTHING;

-- MemoryInterceptor: only when memory is enabled for the user
INSERT INTO interceptor_policy_predicate (id, policy_id, field, operator, value) VALUES
('b0000001-0000-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000005',
 'context.memoryEnabled', 'EQUALS', 'true')
ON CONFLICT DO NOTHING;
