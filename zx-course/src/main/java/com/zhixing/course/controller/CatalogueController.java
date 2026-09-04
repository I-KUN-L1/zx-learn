package com.zhixing.course.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.course.domain.po.CourseCatalogue;
import com.zhixing.course.mapper.CourseCatalogueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程目录
 */
@RestController
@RequestMapping("/catalogues")
@RequiredArgsConstructor
public class CatalogueController {

    private final CourseCatalogueMapper catalogueMapper;

    @GetMapping("/batchQuery")
    @NoWrapper
    public Map<Long, CourseCatalogue> batchQuery(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return catalogueMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CourseCatalogue::getId, c -> c));
    }

    @GetMapping("/querySectionInfoById/{id}")
    public R<CourseCatalogue> querySectionInfo(@PathVariable Long id) {
        return R.ok(catalogueMapper.selectById(id));
    }

    @GetMapping("/course/{courseId}")
    public R<List<CourseCatalogue>> listByCourse(@PathVariable Long courseId) {
        return R.ok(catalogueMapper.selectList(new LambdaQueryWrapper<CourseCatalogue>()
                .eq(CourseCatalogue::getCourseId, courseId)
                .orderByAsc(CourseCatalogue::getIndex)));
    }
}
