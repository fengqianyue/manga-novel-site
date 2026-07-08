package com.manganovel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("manga_page")
public class MangaPage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer pageNum;
    private String imageUrl;
    @TableLogic
    private Integer isDeleted;
}
