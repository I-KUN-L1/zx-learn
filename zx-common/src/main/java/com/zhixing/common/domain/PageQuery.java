package com.zhixing.common.domain;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;

/**
 * 分页请求参数
 */
@Data
public class PageQuery implements Serializable {

    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final Integer MAX_PAGE_SIZE = 200;

    private Integer pageNo = 1;
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    private String sortBy;
    private Boolean isAsc = true;

    public <T> Page<T> toMpPage(OrderItem... orders) {
        Page<T> page = Page.of(pageNo, pageSize);
        if (StringUtils.hasText(sortBy)) {
            page.addOrder(new OrderItem());
        }
        if (orders != null) {
            for (OrderItem order : orders) {
                page.addOrder(order);
            }
        }
        return page;
    }

    public <T> Page<T> toMpPage(String defaultSortBy, boolean defaultAsc) {
        if (!StringUtils.hasText(sortBy)) {
            sortBy = defaultSortBy;
            isAsc = defaultAsc;
        }
        return toMpPage();
    }
}
