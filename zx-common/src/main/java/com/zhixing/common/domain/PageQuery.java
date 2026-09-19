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
            // 关键：OrderItem 必须携带排序列名与方向，空 OrderItem 会导致排序静默失效
            OrderItem item = new OrderItem();
            item.setColumn(sortBy);
            item.setAsc(Boolean.TRUE.equals(isAsc));
            page.addOrder(item);
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
