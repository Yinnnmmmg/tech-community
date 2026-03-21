package com.ying.tech.community.core.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CursorPageResult<T> {
    private Long nextCursor; // 下一页的游标（如果为空，说明到底了）
    private List<T> list;    // 数据列表
}
