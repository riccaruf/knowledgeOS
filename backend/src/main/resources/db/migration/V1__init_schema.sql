-- KnowledgeOS — schema iniziale (coerente con /docs/03_DATABASE_DESIGN.md)
-- Dimensione vettore embedding: 768 (modello di default "nomic-embed-text").
-- Se il modello di embedding cambia, la colonna/dimensione va migrata con una nuova versione
-- Flyway dedicata (si veda 03_DATABASE_DESIGN.md §7 sulla rigenerazione degli embedding).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- Tenant & utenti
-- ============================================================

CREATE TABLE tenant (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(100) NOT NULL UNIQUE,
    status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    storage_bucket   VARCHAR(255) NOT NULL,
    keycloak_realm   VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenant (id),
    keycloak_subject   VARCHAR(255) NOT NULL UNIQUE,
    email              VARCHAR(255) NOT NULL,
    display_name       VARCHAR(255),
    status             VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_user_tenant ON app_user (tenant_id);

-- ============================================================
-- Documenti
-- ============================================================

CREATE TABLE document (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenant (id),
    title                VARCHAR(500) NOT NULL,
    category             VARCHAR(255),
    department           VARCHAR(255),
    tags                 TEXT[] NOT NULL DEFAULT '{}',
    current_version_id  UUID NULL,
    lifecycle_status     VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by           UUID REFERENCES app_user (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ NULL
);

CREATE INDEX idx_document_tenant ON document (tenant_id);

CREATE TABLE document_version (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenant (id),
    document_id          UUID NOT NULL REFERENCES document (id),
    version_label        VARCHAR(50) NOT NULL,
    file_object_key      VARCHAR(500) NOT NULL,
    file_mime_type       VARCHAR(150) NOT NULL,
    file_size_bytes      BIGINT NOT NULL,
    checksum_sha256      VARCHAR(64),
    author               VARCHAR(255),
    ingestion_status     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ingestion_error      TEXT,
    uploaded_by          UUID REFERENCES app_user (id),
    uploaded_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_version_tenant ON document_version (tenant_id);
CREATE INDEX idx_document_version_document ON document_version (document_id);

ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_version (id);

-- ============================================================
-- Chunk (unità semantica + embedding)
-- ============================================================

CREATE TABLE chunk (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES tenant (id),
    document_id            UUID NOT NULL REFERENCES document (id),
    document_version_id   UUID NOT NULL REFERENCES document_version (id),
    title                  VARCHAR(500),
    section                VARCHAR(500),
    page_number            INT,
    content                TEXT NOT NULL,
    metadata               JSONB NOT NULL DEFAULT '{}',
    embedding              VECTOR(768),
    embedding_model        VARCHAR(150),
    chunk_index            INT NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE chunk ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('italian', content)) STORED;

CREATE INDEX idx_chunk_tenant ON chunk (tenant_id);
CREATE INDEX idx_chunk_document_version ON chunk (document_version_id);
CREATE INDEX idx_chunk_embedding_hnsw ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunk_content_tsv ON chunk USING GIN (content_tsv);
CREATE INDEX idx_chunk_metadata_gin ON chunk USING GIN (metadata jsonb_path_ops);

-- ============================================================
-- Ingestion job (tracking del worker asincrono — estensione rispetto
-- allo schema documentato in 03_DATABASE_DESIGN.md, si veda il piano
-- di implementazione per la motivazione)
-- ============================================================

CREATE TABLE ingestion_job (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenant (id),
    document_version_id   UUID NOT NULL REFERENCES document_version (id),
    status                VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    step                  VARCHAR(50),
    error                 TEXT,
    attempts              INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at            TIMESTAMPTZ,
    finished_at           TIMESTAMPTZ
);

CREATE INDEX idx_ingestion_job_status ON ingestion_job (status);
CREATE INDEX idx_ingestion_job_tenant ON ingestion_job (tenant_id);

-- ============================================================
-- Knowledge Domain (predisposto, popolato/gestito da Milestone 2)
-- ============================================================

CREATE TABLE knowledge_entity (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenant (id),
    entity_type           VARCHAR(50) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    attributes            JSONB NOT NULL DEFAULT '{}',
    source_document_id    UUID REFERENCES document (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_entity_tenant ON knowledge_entity (tenant_id);

CREATE TABLE knowledge_relation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenant (id),
    source_entity_id      UUID NOT NULL REFERENCES knowledge_entity (id),
    target_entity_id      UUID NOT NULL REFERENCES knowledge_entity (id),
    relation_type         VARCHAR(50) NOT NULL,
    attributes            JSONB NOT NULL DEFAULT '{}',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_relation_tenant ON knowledge_relation (tenant_id);

-- ============================================================
-- Configurazione modelli (per tenant)
-- ============================================================

CREATE TABLE embedding_model_config (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenant (id),
    model_name         VARCHAR(150) NOT NULL,
    vector_dimension   INT NOT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT true,
    activated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_embedding_model_config_tenant ON embedding_model_config (tenant_id);

CREATE TABLE llm_model_config (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenant (id),
    provider     VARCHAR(50) NOT NULL DEFAULT 'ollama',
    model_name   VARCHAR(150) NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    parameters   JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_llm_model_config_tenant ON llm_model_config (tenant_id);

-- ============================================================
-- Agenti specializzati (predisposto, non popolato in MVP)
-- ============================================================

CREATE TABLE agent_config (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID NOT NULL REFERENCES tenant (id),
    name                      VARCHAR(255) NOT NULL,
    system_prompt             TEXT,
    knowledge_domain_filter   JSONB NOT NULL DEFAULT '{}',
    allowed_tools             JSONB NOT NULL DEFAULT '{}',
    required_role             VARCHAR(50),
    is_active                 BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_agent_config_tenant ON agent_config (tenant_id);

-- ============================================================
-- Audit
-- ============================================================

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant (id),
    user_id       UUID REFERENCES app_user (id),
    event_type    VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(100),
    entity_id     UUID,
    detail        JSONB NOT NULL DEFAULT '{}',
    ip_address    INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_tenant_created ON audit_log (tenant_id, created_at DESC);

CREATE TABLE query_log (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES tenant (id),
    user_id                UUID REFERENCES app_user (id),
    question               TEXT NOT NULL,
    answer                 TEXT,
    confidence_score       NUMERIC(4,3),
    retrieved_chunk_ids    UUID[] NOT NULL DEFAULT '{}',
    llm_model_config_id    UUID REFERENCES llm_model_config (id),
    latency_ms             INT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_log_tenant_created ON query_log (tenant_id, created_at DESC);

-- ============================================================
-- Row Level Security — seconda linea di difesa sull'isolamento tenant
-- (06_SECURITY_MODEL.md §3.2). Il backend imposta sempre
-- `SET LOCAL app.current_tenant_id = '<uuid>'` a inizio transazione.
-- Se non impostato, current_setting(...) restituisce NULL e la policy
-- blocca l'accesso (fail-closed) invece di esporre righe di altri tenant.
-- ============================================================

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'app_user', 'document', 'document_version', 'chunk', 'ingestion_job',
        'knowledge_entity', 'knowledge_relation', 'embedding_model_config',
        'llm_model_config', 'agent_config', 'audit_log', 'query_log'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        -- FORCE: la policy si applica anche al proprietario della tabella (il ruolo applicativo
        -- "knowledgeos" e' proprietario perche' esegue anche le migrazioni Flyway). Senza FORCE,
        -- RLS non protegge dal proprio ruolo applicativo e diventa un controllo puramente illustrativo.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant_id'', true)::uuid)',
            t
        );
    END LOOP;
END $$;
