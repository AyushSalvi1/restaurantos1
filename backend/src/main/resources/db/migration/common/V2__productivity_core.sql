-- ============================================================================
-- LIFEOS :: V2 :: Productivity core: projects, goals, tasks, habits, calendar,
-- focus sessions, metrics, insights, predictions and the life graph.
-- ============================================================================

CREATE TABLE projects (
    id          VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    status      VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    color       VARCHAR(16),
    target_date DATETIME(3),
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    deleted_at  DATETIME(3),
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_projects_user ON projects (user_id, deleted_at);

CREATE TABLE goals (
    id             VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    parent_goal_id VARCHAR(36),
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    category       VARCHAR(48),
    goal_type      VARCHAR(24)  NOT NULL DEFAULT 'LONG_TERM',
    target_date    DATETIME(3),
    progress       INT          NOT NULL DEFAULT 0,
    priority       VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status         VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    color          VARCHAR(16),
    source         VARCHAR(16)  NOT NULL DEFAULT 'USER',
    ai_confirmed   BOOLEAN      NOT NULL DEFAULT FALSE,
    position       INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(3)  NOT NULL,
    updated_at     DATETIME(3)  NOT NULL,
    deleted_at     DATETIME(3),
    CONSTRAINT pk_goals PRIMARY KEY (id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_goals_parent FOREIGN KEY (parent_goal_id) REFERENCES goals (id) ON DELETE SET NULL
);
CREATE INDEX idx_goals_user ON goals (user_id, deleted_at, status);
CREATE INDEX idx_goals_target ON goals (user_id, target_date);

CREATE TABLE goal_milestones (
    id          VARCHAR(36)  NOT NULL,
    goal_id     VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    due_date    DATETIME(3),
    completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    progress    INT          NOT NULL DEFAULT 0,
    position    INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_goal_milestones PRIMARY KEY (id),
    CONSTRAINT fk_goal_milestones_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE
);
CREATE INDEX idx_goal_milestones_goal ON goal_milestones (goal_id, position);

CREATE TABLE tasks (
    id                 VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    notes              TEXT,
    status             VARCHAR(24)  NOT NULL DEFAULT 'TODO',
    priority           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    category           VARCHAR(48),
    deadline           DATETIME(3),
    estimated_minutes  INT,
    actual_minutes     INT NOT NULL DEFAULT 0,
    difficulty         VARCHAR(16),
    energy_requirement VARCHAR(16),
    tags               TEXT,
    goal_id            VARCHAR(36),
    milestone_id       VARCHAR(36),
    project_id         VARCHAR(36),
    recurrence_rule    VARCHAR(120),
    recurrence_parent_id VARCHAR(36),
    recurrence_end_date DATETIME(3),
    position           INT          NOT NULL DEFAULT 0,
    completed_at       DATETIME(3),
    created_at         DATETIME(3)  NOT NULL,
    updated_at         DATETIME(3)  NOT NULL,
    deleted_at         DATETIME(3),
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_milestone FOREIGN KEY (milestone_id) REFERENCES goal_milestones (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL
);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, deleted_at, status);
CREATE INDEX idx_tasks_user_deadline ON tasks (user_id, deadline);
CREATE INDEX idx_tasks_user_goal ON tasks (user_id, goal_id);
CREATE INDEX idx_tasks_user_board ON tasks (user_id, position);

CREATE TABLE task_dependencies (
    id                  VARCHAR(36) NOT NULL,
    task_id             VARCHAR(36) NOT NULL,
    depends_on_task_id  VARCHAR(36) NOT NULL,
    created_at          DATETIME(3) NOT NULL,
    CONSTRAINT pk_task_dependencies PRIMARY KEY (id),
    CONSTRAINT uk_task_dependency UNIQUE (task_id, depends_on_task_id),
    CONSTRAINT fk_task_dependencies_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_dependencies_depends_on FOREIGN KEY (depends_on_task_id) REFERENCES tasks (id) ON DELETE CASCADE
);
CREATE INDEX idx_task_dependencies_depends ON task_dependencies (depends_on_task_id);

CREATE TABLE habits (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    category        VARCHAR(48),
    frequency_type  VARCHAR(16)  NOT NULL DEFAULT 'DAILY',
    times_per_period INT         NOT NULL DEFAULT 1,
    target_days     VARCHAR(16),
    reminder_time   TIME,
    color           VARCHAR(16),
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    CONSTRAINT pk_habits PRIMARY KEY (id),
    CONSTRAINT fk_habits_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_habits_user ON habits (user_id, archived);

CREATE TABLE habit_logs (
    id         VARCHAR(36)  NOT NULL,
    habit_id   VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    log_date   DATE         NOT NULL,
    completed  BOOLEAN      NOT NULL DEFAULT TRUE,
    quantity   DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    note       VARCHAR(500),
    created_at DATETIME(3)  NOT NULL,
    CONSTRAINT pk_habit_logs PRIMARY KEY (id),
    CONSTRAINT uk_habit_logs_day UNIQUE (habit_id, log_date),
    CONSTRAINT fk_habit_logs_habit FOREIGN KEY (habit_id) REFERENCES habits (id) ON DELETE CASCADE
);
CREATE INDEX idx_habit_logs_user_date ON habit_logs (user_id, log_date);

CREATE TABLE calendar_events (
    id               VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(36)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    event_type       VARCHAR(24)  NOT NULL DEFAULT 'EVENT',
    start_at         DATETIME(3)  NOT NULL,
    end_at           DATETIME(3)  NOT NULL,
    all_day          BOOLEAN      NOT NULL DEFAULT FALSE,
    recurrence_rule  VARCHAR(120),
    task_id          VARCHAR(36),
    goal_id          VARCHAR(36),
    habit_id         VARCHAR(36),
    location         VARCHAR(200),
    color            VARCHAR(16),
    external_source  VARCHAR(48),
    external_id      VARCHAR(190),
    created_at       DATETIME(3)  NOT NULL,
    updated_at       DATETIME(3)  NOT NULL,
    CONSTRAINT pk_calendar_events PRIMARY KEY (id),
    CONSTRAINT fk_calendar_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_events_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL
);
CREATE INDEX idx_calendar_events_user_range ON calendar_events (user_id, start_at, end_at);

CREATE TABLE focus_sessions (
    id                VARCHAR(36)  NOT NULL,
    user_id           VARCHAR(36)  NOT NULL,
    task_id           VARCHAR(36),
    goal_id           VARCHAR(36),
    mode              VARCHAR(32)  NOT NULL DEFAULT 'POMODORO_25_5',
    planned_minutes   INT          NOT NULL DEFAULT 25,
    actual_minutes    INT          NOT NULL DEFAULT 0,
    started_at        DATETIME(3)  NOT NULL,
    ended_at          DATETIME(3),
    completed         BOOLEAN      NOT NULL DEFAULT FALSE,
    interrupted_count INT          NOT NULL DEFAULT 0,
    outcome           VARCHAR(500),
    notes             TEXT,
    rating            INT,
    created_at        DATETIME(3)  NOT NULL,
    CONSTRAINT pk_focus_sessions PRIMARY KEY (id),
    CONSTRAINT fk_focus_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_focus_sessions_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL
);
CREATE INDEX idx_focus_sessions_user_start ON focus_sessions (user_id, started_at);
CREATE INDEX idx_focus_sessions_user_task ON focus_sessions (user_id, task_id);

CREATE TABLE productivity_metrics (
    id                 VARCHAR(36) NOT NULL,
    user_id            VARCHAR(36) NOT NULL,
    metric_date        DATE        NOT NULL,
    tasks_created      INT         NOT NULL DEFAULT 0,
    tasks_completed    INT         NOT NULL DEFAULT 0,
    tasks_missed       INT         NOT NULL DEFAULT 0,
    tasks_planned      INT         NOT NULL DEFAULT 0,
    focus_minutes      INT         NOT NULL DEFAULT 0,
    study_minutes      INT         NOT NULL DEFAULT 0,
    habits_completed   INT         NOT NULL DEFAULT 0,
    habits_planned     INT         NOT NULL DEFAULT 0,
    events_count       INT         NOT NULL DEFAULT 0,
    transactions_count INT         NOT NULL DEFAULT 0,
    planned_minutes    INT         NOT NULL DEFAULT 0,
    completed_minutes  INT         NOT NULL DEFAULT 0,
    productivity_score INT         NOT NULL DEFAULT 0,
    created_at         DATETIME(3) NOT NULL,
    updated_at         DATETIME(3) NOT NULL,
    CONSTRAINT pk_productivity_metrics PRIMARY KEY (id),
    CONSTRAINT uk_productivity_metrics_day UNIQUE (user_id, metric_date)
);

CREATE TABLE insights (
    id            VARCHAR(36)   NOT NULL,
    user_id       VARCHAR(36)   NOT NULL,
    insight_type  VARCHAR(32)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    body          VARCHAR(1000) NOT NULL,
    severity      VARCHAR(16)   NOT NULL DEFAULT 'INFO',
    confidence    INT           NOT NULL DEFAULT 60,
    factors       TEXT,
    period_start  DATE,
    period_end    DATE,
    metric_date   DATE,
    source        VARCHAR(16)   NOT NULL DEFAULT 'RULE',
    dedupe_key    VARCHAR(190),
    dismissed     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(3)   NOT NULL,
    CONSTRAINT pk_insights PRIMARY KEY (id),
    CONSTRAINT fk_insights_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_insights_user_date ON insights (user_id, created_at);
CREATE UNIQUE INDEX uk_insights_dedupe ON insights (user_id, dedupe_key);

CREATE TABLE predictions (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    prediction_type VARCHAR(32)  NOT NULL,
    subject_type    VARCHAR(32)  NOT NULL,
    subject_id      VARCHAR(36)  NOT NULL,
    label           VARCHAR(200) NOT NULL,
    probability     INT          NOT NULL,
    confidence      INT          NOT NULL DEFAULT 50,
    factors         TEXT,
    horizon_days    INT          NOT NULL DEFAULT 7,
    created_at      DATETIME(3)  NOT NULL,
    expires_at      DATETIME(3)  NOT NULL,
    CONSTRAINT pk_predictions PRIMARY KEY (id),
    CONSTRAINT fk_predictions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_predictions_user_type ON predictions (user_id, prediction_type, expires_at);

CREATE TABLE life_graph_edges (
    id          VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id   VARCHAR(36) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id   VARCHAR(36) NOT NULL,
    relation    VARCHAR(32) NOT NULL,
    weight      DOUBLE      NOT NULL DEFAULT 1.00,
    created_at  DATETIME(3) NOT NULL,
    CONSTRAINT pk_life_graph_edges PRIMARY KEY (id),
    CONSTRAINT uk_life_graph_edge UNIQUE (user_id, source_type, source_id, target_type, target_id, relation),
    CONSTRAINT fk_life_graph_edges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_life_graph_source ON life_graph_edges (user_id, source_type, source_id);
CREATE INDEX idx_life_graph_target ON life_graph_edges (user_id, target_type, target_id);