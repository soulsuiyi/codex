-- ============================================================
-- 无纸化办案电子档案系统 库表结构（H2 File Mode 兼容）
-- 依据 codeplan.md 第四章与 AGENTS.md 库表设计规范编写
-- 全部使用 IF NOT EXISTS，可重复执行
-- ============================================================

-- 4.1 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password        VARCHAR(128) NOT NULL,
    real_name       VARCHAR(64)  NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(100),
    dept_id         BIGINT,
    status          TINYINT      NOT NULL DEFAULT 1,
    last_login_time TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username ON sys_user(username);

-- 4.2 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_role_code ON sys_role(role_code);

-- 4.3 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 4.4 菜单权限表
CREATE TABLE IF NOT EXISTS sys_menu (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id  BIGINT      NOT NULL DEFAULT 0,
    menu_name  VARCHAR(64) NOT NULL,
    menu_type  VARCHAR(10) NOT NULL,
    perms      VARCHAR(128),
    path       VARCHAR(255),
    component  VARCHAR(255),
    sort_order INT         NOT NULL DEFAULT 0,
    visible    TINYINT     NOT NULL DEFAULT 1,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4.5 案件表
CREATE TABLE IF NOT EXISTS sys_case (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no     VARCHAR(64)  NOT NULL,
    case_name   VARCHAR(255) NOT NULL,
    category_id BIGINT       NOT NULL,
    case_type   VARCHAR(32),
    handler_id  BIGINT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    remark      VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_case_case_no ON sys_case(case_no);

-- 4.6 案件分类表（树形）
CREATE TABLE IF NOT EXISTS sys_case_category (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    level      INT          NOT NULL DEFAULT 1,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4.7 文件表（中转站+归档区统一存储）
-- is_deleted 为 AGENTS.md 规定的中转站逻辑删除标记
CREATE TABLE IF NOT EXISTS sys_file (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id       BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    storage_bucket VARCHAR(50) NOT NULL,
    storage_path  VARCHAR(512) NOT NULL,
    file_hash     VARCHAR(64)  NOT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    mime_type     VARCHAR(100) NOT NULL,
    stage         VARCHAR(20)  NOT NULL DEFAULT 'STAGING',
    version       INT          NOT NULL DEFAULT 1,
    is_latest     BOOLEAN      NOT NULL DEFAULT TRUE,
    preview_path  VARCHAR(512),
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_sys_file_case_stage ON sys_file(case_id, stage);

-- 4.8 文件版本表
CREATE TABLE IF NOT EXISTS sys_file_version (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id      BIGINT       NOT NULL,
    version      INT          NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    change_desc  VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_file_version_file_id ON sys_file_version(file_id);

-- 4.9 归档记录表
CREATE TABLE IF NOT EXISTS sys_archive (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id          BIGINT      NOT NULL,
    case_no          VARCHAR(64) NOT NULL,
    operator_id      BIGINT      NOT NULL,
    file_count       INT         NOT NULL DEFAULT 0,
    struct_doc_count INT         NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    remark           VARCHAR(500),
    archived_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_archive_case_id ON sys_archive(case_id);

-- 4.10 借阅申请表
CREATE TABLE IF NOT EXISTS sys_borrow_apply (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id        BIGINT       NOT NULL,
    case_no        VARCHAR(64)  NOT NULL,
    applicant_id   BIGINT       NOT NULL,
    file_ids       VARCHAR(1000) NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    need_download  BOOLEAN      NOT NULL DEFAULT FALSE,
    expire_time    TIMESTAMP    NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING_SECRETARY',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_borrow_apply_applicant ON sys_borrow_apply(applicant_id);
CREATE INDEX IF NOT EXISTS idx_sys_borrow_apply_status ON sys_borrow_apply(status);

-- 4.11 借阅审批记录表
CREATE TABLE IF NOT EXISTS sys_borrow_approval (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    apply_id       BIGINT      NOT NULL,
    approver_id    BIGINT      NOT NULL,
    approval_step  VARCHAR(30) NOT NULL,
    result         VARCHAR(20) NOT NULL,
    comment        VARCHAR(500),
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_borrow_approval_apply ON sys_borrow_approval(apply_id);

-- 4.12 借阅授权 Token 表
CREATE TABLE IF NOT EXISTS sys_borrow_token (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    apply_id       BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    token_value    VARCHAR(64)  NOT NULL,
    file_ids       VARCHAR(1000) NOT NULL,
    allow_download BOOLEAN      NOT NULL DEFAULT FALSE,
    expire_time    TIMESTAMP    NOT NULL,
    is_revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_borrow_token_value ON sys_borrow_token(token_value);
CREATE INDEX IF NOT EXISTS idx_sys_borrow_token_user ON sys_borrow_token(user_id);

-- 4.13 操作审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(64),
    module      VARCHAR(50) NOT NULL,
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   VARCHAR(64),
    ip          VARCHAR(50) NOT NULL,
    user_agent  VARCHAR(500),
    detail      TEXT,
    result      VARCHAR(20),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_audit_log_created ON sys_audit_log(created_at);

-- 4.14 系统字典表
CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type  VARCHAR(50)  NOT NULL,
    dict_code  VARCHAR(50)  NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100),
    sort_order INT          NOT NULL DEFAULT 0,
    remark     VARCHAR(255)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_type_code ON sys_dict(dict_type, dict_code);

-- 4.15 开放 API 密钥表
CREATE TABLE IF NOT EXISTS sys_api_key (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name     VARCHAR(100) NOT NULL,
    api_key      VARCHAR(64)  NOT NULL,
    api_secret   VARCHAR(128) NOT NULL,
    ip_whitelist VARCHAR(500),
    status       TINYINT      NOT NULL DEFAULT 1,
    expire_time  TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_api_key_api_key ON sys_api_key(api_key);
