package com.archive.api.controller;

import com.archive.common.dto.DictVO;
import com.archive.common.response.Result;
import com.archive.core.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典查询接口（所有登录用户可用，供下拉选项使用）。
 */
@RestController
@RequestMapping("/api/v1/dicts")
@Tag(name = "数据字典", description = "按类型查询字典选项，供前端下拉使用")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/type/{dictType}")
    @Operation(summary = "按类型查询字典", description = "返回该类型全部字典项（按 sort_order 升序）")
    public Result<List<DictVO>> listByType(@PathVariable String dictType) {
        return Result.success(dictService.listByType(dictType));
    }
}
