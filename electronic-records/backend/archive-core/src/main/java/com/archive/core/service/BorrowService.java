package com.archive.core.service;

import com.archive.common.dto.BorrowApplyRequest;
import com.archive.common.dto.BorrowApplyVO;
import com.archive.common.dto.BorrowApprovalRequest;
import com.archive.common.dto.BorrowDetailVO;
import com.archive.common.dto.BorrowDownloadRequest;
import com.archive.common.dto.BorrowTokenVO;
import com.archive.common.dto.FileStreamVO;
import com.archive.common.dto.PageResult;

/**
 * 借阅服务：申请、双重审批、授权 Token、借阅下载、归还、到期回收。
 */
public interface BorrowService {

    /**
     * 提交借阅申请（仅归档区文件）。
     */
    BorrowApplyVO apply(BorrowApplyRequest request);

    /**
     * 我的借阅申请列表。
     */
    PageResult<BorrowApplyVO> myList(long page, long size);

    /**
     * 待我审批列表：按当前用户角色返回对应待审状态（SECRETARY→初审，ARCHIVIST/ADMIN→终审，ADMIN 两者可见）。
     */
    PageResult<BorrowApplyVO> pendingList(long page, long size);

    /**
     * 借阅申请详情（本人或审批人可查看），含借阅文件与审批记录。
     */
    BorrowDetailVO detail(Long id);

    /**
     * 审批：PENDING_SECRETARY 由 SECRETARY、PENDING_ADMIN 由 ARCHIVIST 处理。
     */
    BorrowApplyVO approve(Long id, BorrowApprovalRequest request);

    /**
     * 获取借阅授权 Token（仅本人且借阅生效后）。
     */
    BorrowTokenVO token(Long id);

    /**
     * 借阅下载（校验 Token、有效期、下载权限、授权文件；图片/PDF 返回带水印流）。
     */
    FileStreamVO download(Long id, BorrowDownloadRequest request);

    /**
     * 借阅 HLS 播放列表（校验 Token 与授权文件，供借阅视频预览）。
     */
    FileStreamVO hlsPlaylist(Long id, String tokenValue, Long fileId);

    /**
     * 借阅 HLS 分片（校验 Token 与授权文件）。
     */
    FileStreamVO hlsSegment(Long id, String tokenValue, Long fileId, String segment);

    /**
     * 归还借阅并撤销 Token。
     */
    BorrowApplyVO returnFile(Long id);

    /**
     * 到期权限回收：扫描过期 Token 置 EXPIRED、撤销并清缓存（由 archive-job 定时触发）。
     */
    int expireExpiredBorrows();
}
