package com.manganovel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.Work;

public interface IWorkService extends IService<Work> {
    Page<Work> pageByType(String type, int pageNum, int pageSize, String keyword);
    Page<Work> pageForAdmin(Integer status, String keyword, int pageNum, int pageSize);
    Page<Work> pageWithFilter(String type, String keyword, Integer publishYear, Integer completed,
                              Long tagId, Integer pageNum, Integer pageSize);
}
