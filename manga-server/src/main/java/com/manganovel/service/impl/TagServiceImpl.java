package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.Tag;
import com.manganovel.mapper.TagMapper;
import com.manganovel.service.ITagService;
import org.springframework.stereotype.Service;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {
}
