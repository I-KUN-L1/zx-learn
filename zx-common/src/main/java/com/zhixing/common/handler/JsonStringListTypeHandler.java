package com.zhixing.common.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code List<String>} ↔ JSON 字符串 的 MyBatis 类型处理器。
 * <p>
 * 使用方式：实体类上标注 {@code @TableName(autoResultMap = true)}，
 * 字段上标注 {@code @TableField(typeHandler = JsonStringListTypeHandler.class)}。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>读取时容错 —— 空串 / 非法 JSON / 非数组均返回空列表，绝不抛异常阻断查询；</li>
 *   <li>写入时容错 —— 空列表写入 null，避免存 "[]" 造成无意义数据；</li>
 *   <li>兼容纯文本历史数据 —— 若列中存的是非 JSON 文本（如旧数据），按单元素列表处理。</li>
 * </ul>
 */
@MappedTypes(List.class)
public class JsonStringListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter,
                                    JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setString(i, null);
            return;
        }
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            // 序列化失败时退化为按换行拼接，保证数据可落库而非整体失败
            ps.setString(i, String.join("\n", parameter));
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                List<String> list = MAPPER.readValue(text, TYPE);
                return list == null ? new ArrayList<>() : list;
            } catch (Exception ignored) {
                return new ArrayList<>();
            }
        }
        // 兼容历史纯文本数据
        List<String> fallback = new ArrayList<>();
        fallback.add(text);
        return fallback;
    }
}
