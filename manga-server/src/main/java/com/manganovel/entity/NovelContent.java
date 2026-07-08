package com.manganovel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("novel_content")
public class NovelContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer pageNum;
    private String textContent;
    @TableLogic
    private Integer isDeleted;
}
