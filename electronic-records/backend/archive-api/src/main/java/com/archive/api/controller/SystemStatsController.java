package com.archive.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.archive.common.dto.StatsVO;
import com.archive.common.response.Result;
import com.archive.core.facade.SystemAdminFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-运行统计接口。
 */
@RestController
@RequestMapping("/api/v1/system/stats")
@Tag(name = "系统管理-运行统计", description = "用户/案件/文件/归档/借阅/API密钥等业务统计")
@SaCheckRole("ADMIN")
public class SystemStatsController {

    private final SystemAdminFacade systemAdminFacade;

    public SystemStatsController(SystemAdminFacade systemAdminFacade) {
        this.systemAdminFacade = systemAdminFacade;
    }

    @GetMapping
    @Operation(summary = "系统运行统计", description = "业务数据总量统计，供监控页展示")
    public Result<StatsVO> stats() {
        return Result.success(systemAdminFacade.systemStats());
    }
}
