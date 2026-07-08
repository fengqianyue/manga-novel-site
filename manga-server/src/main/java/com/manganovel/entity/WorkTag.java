package com.manganovel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("work_tag")
public class WorkTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Long tagId;
}
