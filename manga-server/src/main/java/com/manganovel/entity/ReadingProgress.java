package com.manganovel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reading_progress")
public class ReadingProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long workId;
    private Long chapterId;
    private Integer pageNum;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
