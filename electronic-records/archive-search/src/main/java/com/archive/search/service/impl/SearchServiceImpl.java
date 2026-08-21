package com.archive.search.service.impl;

import com.archive.common.dto.FileVO;
import com.archive.common.dto.PageResult;
import com.archive.common.dto.SearchResultVO;
import com.archive.common.exception.BusinessException;
import com.archive.common.response.ResultCode;
import com.archive.search.service.SearchService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全文检索实现：基于 H2 FULLTEXT 索引与 FT_SEARCH_DATA 相关度查询。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final int FT_FETCH_LIMIT = 10000;
    private static final int KEYWORD_MAX_LENGTH = 100;

    private final JdbcTemplate jdbcTemplate;

    public SearchServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<SearchResultVO> search(String keyword, long page, long size) {
        validateKeyword(keyword);
        if (keyword.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "检索关键字过长");
        }
        page = Math.max(page, 1);
        size = Math.max(size, 1);
        long offset = (page - 1) * size;
        List<SearchResultVO> records = jdbcTemplate.query(
                baseSelect(keyword) + "WHERE f.is_deleted = false "
                        + "ORDER BY ft.SCORE DESC, f.created_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> toResult(rs, keyword),
                size, offset);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + ftCall(keyword) + " ft "
                        + "JOIN sys_file f ON f.id = ft.KEYS[1] "
                        + "WHERE f.is_deleted = false",
                Long.class);
        return new PageResult<>(records, total == null ? 0 : total, page, size);
    }

    @Override
    public SearchResultVO detail(Long id, String keyword) {
        validateKeyword(keyword);
        if (keyword.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "检索关键字过长");
        }
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件ID不能为空");
        }
        List<SearchResultVO> list = jdbcTemplate.query(
                baseSelect(keyword) + "WHERE f.id = ? AND f.is_deleted = false",
                (rs, rowNum) -> toResult(rs, keyword), id);
        if (list.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return list.get(0);
    }

    private String baseSelect(String keyword) {
        return "SELECT f.id, f.case_id, c.case_no, f.file_name, f.file_size, f.mime_type, f.storage_bucket, "
                + "f.stage, f.version, f.is_latest, f.created_at, f.updated_at, f.content "
                + "FROM " + ftCall(keyword) + " ft "
                + "JOIN sys_file f ON f.id = ft.KEYS[1] "
                + "JOIN sys_case c ON c.id = f.case_id ";
    }

    /**
     * 关键字内联进 FT_SEARCH_DATA 调用（H2 对表函数参数与 JDBC 参数混用支持不佳），
     * 单引号转义防止 SQL 注入。
     */
    private String ftCall(String keyword) {
        return "FT_SEARCH_DATA('" + keyword.replace("'", "''") + "', " + FT_FETCH_LIMIT + ", 0)";
    }

    private void validateKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "检索关键字不能为空");
        }
    }

    private SearchResultVO toResult(ResultSet rs, String keyword) throws SQLException {
        FileVO file = new FileVO();
        file.setId(rs.getLong("id"));
        file.setCaseId(rs.getLong("case_id"));
        file.setCaseNo(rs.getString("case_no"));
        file.setFileName(rs.getString("file_name"));
        file.setFileSize(rs.getLong("file_size"));
        file.setMimeType(rs.getString("mime_type"));
        file.setStorageBucket(rs.getString("storage_bucket"));
        file.setStage(rs.getString("stage"));
        if (rs.getObject("version") != null) {
            file.setVersion(rs.getInt("version"));
        }
        file.setIsLatest(rs.getBoolean("is_latest"));
        if (rs.getTimestamp("created_at") != null) {
            file.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        if (rs.getTimestamp("updated_at") != null) {
            file.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        }
        return new SearchResultVO(file, buildSnippet(rs.getString("content"), keyword));
    }

    private String buildSnippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return null;
        }
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + keyword.length() + 30);
        String snippet = (start > 0 ? "..." : "") + content.substring(start, end)
                + (end < content.length() ? "..." : "");
        return snippet.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "<em>$1</em>");
    }
}
