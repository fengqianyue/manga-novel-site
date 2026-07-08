package com.manganovel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.MangaPage;
import com.manganovel.mapper.MangaPageMapper;
import com.manganovel.service.IMangaPageService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MangaPageServiceImpl extends ServiceImpl<MangaPageMapper, MangaPage> implements IMangaPageService {

    @Override
    public List<MangaPage> listByChapterId(Long chapterId) {
        LambdaQueryWrapper<MangaPage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MangaPage::getChapterId, chapterId)
               .orderByAsc(MangaPage::getPageNum);
        return list(wrapper);
    }
}
