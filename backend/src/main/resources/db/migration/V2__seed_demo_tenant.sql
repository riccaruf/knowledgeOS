-- Tenant demo per MVP a singolo tenant (id allineato all'attributo
-- "tenant_id" impostato sugli utenti nel realm export Keycloak
-- infra/keycloak/knowledgeos-realm.json). Gli utenti applicativi (app_user)
-- vengono creati automaticamente al primo login (JIT provisioning), non
-- seminati qui, per non dover sincronizzare manualmente gli UUID generati
-- da Keycloak.

INSERT INTO tenant (id, name, slug, status, storage_bucket, keycloak_realm)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Cliente Demo',
    'cliente-demo',
    'ACTIVE',
    'tenant-demo-docs',
    'knowledgeos'
);

-- Le tabelle seguenti sono protette da RLS con FORCE (V1), quindi anche
-- l'utente proprietario (usato da Flyway) deve dichiarare il tenant corrente
-- per poter inserire righe che rispettino la policy tenant_isolation.
SELECT set_config('app.current_tenant_id', '11111111-1111-1111-1111-111111111111', false);

INSERT INTO embedding_model_config (tenant_id, model_name, vector_dimension, is_active)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'nomic-embed-text',
    768,
    true
);

INSERT INTO llm_model_config (tenant_id, provider, model_name, is_active, parameters)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'ollama',
    'qwen2.5:7b',
    true,
    '{"temperature": 0.2}'
);
