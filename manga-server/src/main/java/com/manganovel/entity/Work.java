package com.manganovel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("work")
public class Work {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
    private String type;
    private Integer status;
    private Long userId;
    private Integer publishYear;
    private Integer completed;
    private Long viewCount;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
