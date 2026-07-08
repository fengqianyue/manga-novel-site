package com.manganovel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkSaveDTO {
    private Long id;

    @NotBlank(message = "作品标题不能为空")
    private String title;

    @NotBlank(message = "作者不能为空")
    private String author;

    private String coverUrl;
    private String summary;

    @NotBlank(message = "作品类型不能为空")
    private String type;

    private Integer status;
    private Integer publishYear;
    private Integer completed;
}
