-- ==================== 漫画小说阅读平台 - 数据库初始化 ====================
-- Docker 首次启动时自动执行，已有数据时跳过

CREATE DATABASE IF NOT EXISTS manga_novel
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE manga_novel;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
  `email`      VARCHAR(100) NOT NULL COMMENT '邮箱',
  `password`   VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
  `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `role`       INT          NOT NULL DEFAULT 0 COMMENT '角色 0=用户 1=作者 2=管理员',
  `is_deleted` INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=删除',
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 作品表
CREATE TABLE IF NOT EXISTS `work` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  `title`        VARCHAR(200) NOT NULL COMMENT '标题',
  `author`       VARCHAR(100) DEFAULT NULL COMMENT '作者',
  `cover_url`    VARCHAR(500) DEFAULT NULL COMMENT '封面URL',
  `summary`      TEXT         DEFAULT NULL COMMENT '简介',
  `type`         VARCHAR(20)  NOT NULL COMMENT '类型 manga/novel',
  `status`      INT          NOT NULL DEFAULT 0 COMMENT '状态 0=下架/草稿 1=上架 2=待审核 3=已驳回',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '上传者ID',
  `is_public`   INT          NOT NULL DEFAULT 0 COMMENT '0=私人书架作品 1=公开发布作品',
  `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核驳回理由',
  `publish_year` INT         DEFAULT NULL COMMENT '出版年份',
  `completed`    INT          NOT NULL DEFAULT 0 COMMENT '是否完结 0=否 1=是',
  `view_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览数',
  `is_deleted`   INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品表';

-- 章节表
CREATE TABLE IF NOT EXISTS `chapter` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  `work_id`     BIGINT       NOT NULL COMMENT '作品ID',
  `title`       VARCHAR(200) DEFAULT NULL COMMENT '章节标题',
  `chapter_num` DOUBLE       NOT NULL DEFAULT 1 COMMENT '章节号(支持如1.5)',
  `status`      INT          NOT NULL DEFAULT 1 COMMENT '状态',
  `is_deleted`  INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_id` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节表';

-- 漫画页表
CREATE TABLE IF NOT EXISTS `manga_page` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '页面ID',
  `chapter_id` BIGINT       NOT NULL COMMENT '章节ID',
  `page_num`   INT          NOT NULL DEFAULT 1 COMMENT '页码',
  `image_url`  VARCHAR(500) NOT NULL COMMENT '图片URL',
  `is_deleted` INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_chapter_id` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漫画页表';

-- 小说内容表
CREATE TABLE IF NOT EXISTS `novel_content` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `chapter_id`   BIGINT NOT NULL COMMENT '章节ID',
  `page_num`     INT    NOT NULL DEFAULT 1 COMMENT '页码',
  `text_content` TEXT   DEFAULT NULL COMMENT '文本内容',
  `is_deleted`   INT    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_chapter_id` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小说内容表';

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
  `id`         BIGINT    NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `work_id`    BIGINT    NOT NULL COMMENT '作品ID',
  `user_id`    BIGINT    NOT NULL COMMENT '用户ID',
  `content`    TEXT      NOT NULL COMMENT '评论内容',
  `is_deleted` INT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME  DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 收藏表
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
  `work_id`    BIGINT   NOT NULL COMMENT '作品ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`, `work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- 阅读进度表
CREATE TABLE IF NOT EXISTS `reading_progress` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '进度ID',
  `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
  `work_id`    BIGINT   NOT NULL COMMENT '作品ID',
  `chapter_id` BIGINT   NOT NULL COMMENT '章节ID',
  `page_num`   INT      NOT NULL DEFAULT 0 COMMENT '页码',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`, `work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阅读进度表';

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name`       VARCHAR(50) NOT NULL COMMENT '标签名',
  `created_at` DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- 作品-标签关联表
CREATE TABLE IF NOT EXISTS `work_tag` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `work_id` BIGINT NOT NULL COMMENT '作品ID',
  `tag_id`  BIGINT NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品标签关联表';
