package com.zhixing.common.domain;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应
 */
@Data
public class PageDTO<T> implements Serializable {

    private Long total;
    private Long pages;
    private List<T> list;

    public static <T> PageDTO<T> empty(Long total, Long pages) {
        PageDTO<T> dto = new PageDTO<>();
        dto.setTotal(total);
        dto.setPages(pages);
        dto.setList(List.of());
        return dto;
    }

    public static <T> PageDTO<T> of(Page<T> page) {
        PageDTO<T> dto = new PageDTO<>();
        dto.setTotal(page.getTotal());
        dto.setPages(page.getPages());
        dto.setList(page.getRecords());
        return dto;
    }

    public static <PO, VO> PageDTO<VO> of(Page<PO> page, Function<PO, VO> converter) {
        PageDTO<VO> dto = new PageDTO<>();
        dto.setTotal(page.getTotal());
        dto.setPages(page.getPages());
        dto.setList(page.getRecords().stream().map(converter).collect(Collectors.toList()));
        return dto;
    }

    public static <PO, VO> PageDTO<VO> of(Page<PO> page, List<VO> list) {
        PageDTO<VO> dto = new PageDTO<>();
        dto.setTotal(page.getTotal());
        dto.setPages(page.getPages());
        dto.setList(list);
        return dto;
    }
}
