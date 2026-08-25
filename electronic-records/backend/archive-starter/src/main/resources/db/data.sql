-- ============================================================
-- 种子数据（幂等，可重复执行）
-- 角色编码与 codeplan.md / AGENTS.md 保持一致
-- ============================================================

-- 预置角色：ADMIN / SECRETARY / ARCHIVIST / CASE_HANDLER
INSERT INTO sys_role (role_code, role_name, description)
SELECT 'ADMIN', '管理员', '系统管理员，拥有全部权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN');

INSERT INTO sys_role (role_code, role_name, description)
SELECT 'SECRETARY', '仲裁秘书', '案件仲裁秘书，负责借阅申请初审'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SECRETARY');

INSERT INTO sys_role (role_code, role_name, description)
SELECT 'ARCHIVIST', '档案管理员', '负责一键归档与借阅申请终审'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ARCHIVIST');

INSERT INTO sys_role (role_code, role_name, description)
SELECT 'CASE_HANDLER', '办案人员', '案件承办人，管理案件与中转站文件'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'CASE_HANDLER');

-- 预置管理员账号：admin / admin123（BCrypt）
INSERT INTO sys_user (username, password, real_name, status)
SELECT 'admin', '$2a$10$g0w/dYz4RJxounJKS5d86uF5TNfvDRYBEnPUedB2iYmHJnH1113WG', '系统管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- 管理员账号绑定 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role ur
      INNER JOIN sys_user u2 ON u2.id = ur.user_id
      INNER JOIN sys_role r2 ON r2.id = ur.role_id
      WHERE u2.username = 'admin' AND r2.role_code = 'ADMIN'
  );

-- 结构化文书识别关键词（归档时按文件名匹配统计 struct_doc_count，可在字典管理页维护）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'TRANSCRIPT', '笔录', '笔录', 1, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'TRANSCRIPT');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'INDICTMENT', '起诉书', '起诉书', 2, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'INDICTMENT');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'ARBITRAL_AWARD', '裁决书', '裁决书', 3, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'ARBITRAL_AWARD');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'MEDIATION', '调解书', '调解书', 4, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'MEDIATION');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'DECISION', '决定书', '决定书', 5, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'DECISION');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'NOTICE', '通知书', '通知书', 6, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'NOTICE');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'REPLY', '答辩状', '答辩状', 7, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'REPLY');

INSERT INTO sys_dict (dict_type, dict_code, dict_label, dict_value, sort_order, remark)
SELECT 'STRUCTURED_DOC', 'APPLICATION', '申请书', '申请书', 8, '结构化文书文件名关键词'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = 'STRUCTURED_DOC' AND dict_code = 'APPLICATION');
