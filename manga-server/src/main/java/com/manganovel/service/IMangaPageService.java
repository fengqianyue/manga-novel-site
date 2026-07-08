package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.MangaPage;
import java.util.List;

public interface IMangaPageService extends IService<MangaPage> {
    List<MangaPage> listByChapterId(Long chapterId);
}
