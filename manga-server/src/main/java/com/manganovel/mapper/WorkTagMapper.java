package com.manganovel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manganovel.entity.WorkTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkTagMapper extends BaseMapper<WorkTag> {

    @Select("SELECT w.id, w.title, w.author, w.cover_url, w.type, w.summary, w.publish_year, w.completed, w.view_count, " +
            "COUNT(wt2.tag_id) AS match_count " +
            "FROM work_tag wt1 " +
            "JOIN work_tag wt2 ON wt1.tag_id = wt2.tag_id AND wt1.work_id != wt2.work_id " +
            "JOIN work w ON wt2.work_id = w.id " +
            "WHERE wt1.work_id = #{workId} AND w.status = 1 AND w.is_deleted = 0 AND w.user_id IS NULL " +
            "AND w.type = #{type} " +
            "GROUP BY w.id, w.title, w.author, w.cover_url, w.type, w.summary, w.publish_year, w.completed, w.view_count " +
            "ORDER BY match_count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectSimilarWorks(Long workId, String type, int limit);
}
