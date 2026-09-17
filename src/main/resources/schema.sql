CREATE TABLE IF NOT EXISTS chen_tasks (
    task_id VARCHAR(64) PRIMARY KEY,
    prompt TEXT NOT NULL,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    started_at BIGINT NOT NULL DEFAULT 0,
    completed_at BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    estimated_input_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    result TEXT,
    error TEXT,
    plan_summary TEXT,
    tenant_id VARCHAR(128) NOT NULL DEFAULT 'default',
    owner_id VARCHAR(128) NOT NULL DEFAULT 'anonymous',
    session_id VARCHAR(128) NOT NULL DEFAULT 'default',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    review_passed BOOLEAN,
    review_feedback TEXT,
    review_missing_items TEXT
);

ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS started_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS completed_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS duration_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS estimated_input_tokens BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS estimated_output_tokens BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS owner_id VARCHAR(128) NOT NULL DEFAULT 'anonymous';
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS session_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS review_passed BOOLEAN;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS review_feedback TEXT;
ALTER TABLE chen_tasks ADD COLUMN IF NOT EXISTS review_missing_items TEXT;

CREATE TABLE IF NOT EXISTS chen_task_steps (
    step_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    seq INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    output TEXT,
    error TEXT,
    started_at BIGINT NOT NULL DEFAULT 0,
    completed_at BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    parallelizable BOOLEAN NOT NULL DEFAULT FALSE,
    depends_on VARCHAR(255) NOT NULL DEFAULT '',
    estimated_input_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_step_task FOREIGN KEY (task_id) REFERENCES chen_tasks(task_id) ON DELETE CASCADE
);

ALTER TABLE chen_task_steps ADD COLUMN IF NOT EXISTS duration_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_task_steps ADD COLUMN IF NOT EXISTS parallelizable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chen_task_steps ADD COLUMN IF NOT EXISTS depends_on VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE chen_task_steps ADD COLUMN IF NOT EXISTS estimated_input_tokens BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_task_steps ADD COLUMN IF NOT EXISTS estimated_output_tokens BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS chen_task_artifacts (
    artifact_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    path TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    media_type VARCHAR(255) NOT NULL DEFAULT 'application/octet-stream',
    CONSTRAINT fk_task_artifact_task FOREIGN KEY (task_id) REFERENCES chen_tasks(task_id) ON DELETE CASCADE
);

ALTER TABLE chen_task_artifacts ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE chen_task_artifacts ADD COLUMN IF NOT EXISTS size_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chen_task_artifacts ADD COLUMN IF NOT EXISTS media_type VARCHAR(255) NOT NULL DEFAULT 'application/octet-stream';
