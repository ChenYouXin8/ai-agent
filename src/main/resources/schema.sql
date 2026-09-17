CREATE TABLE IF NOT EXISTS chen_tasks (
    task_id VARCHAR(64) PRIMARY KEY,
    prompt CLOB NOT NULL,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    result CLOB,
    error CLOB,
    plan_summary CLOB,
    owner_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    review_passed BOOLEAN,
    review_feedback CLOB,
    review_missing_items CLOB
);

CREATE TABLE IF NOT EXISTS chen_task_steps (
    step_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    seq INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description CLOB,
    status VARCHAR(32) NOT NULL,
    output CLOB,
    error CLOB,
    started_at BIGINT NOT NULL,
    completed_at BIGINT NOT NULL,
    retry_count INT NOT NULL,
    parallelizable BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_task_step_task FOREIGN KEY (task_id) REFERENCES chen_tasks(task_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chen_task_artifacts (
    artifact_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    path CLOB NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT fk_task_artifact_task FOREIGN KEY (task_id) REFERENCES chen_tasks(task_id) ON DELETE CASCADE
);
