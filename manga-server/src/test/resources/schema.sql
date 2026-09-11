-- ==================== 测试环境建表 ====================
-- H2 内存数据库，MySQL 兼容模式

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `username`   VARCHAR(50)  NOT NULL,
  `email`      VARCHAR(100) NOT NULL,
  `password`   VARCHAR(255) NOT NULL,
  `avatar_url` VARCHAR(500),
  `role`       INT          NOT NULL DEFAULT 0,
  `is_deleted` INT          NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON `user` (`username`);
CREATE UNIQUE INDEX IF NOT EXISTS uk_email    ON `user` (`email`);

CREATE TABLE IF NOT EXISTS `work` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title`        VARCHAR(200) NOT NULL,
  `author`       VARCHAR(100),
  `cover_url`    VARCHAR(500),
  `summary`      TEXT,
  `type`         VARCHAR(20)  NOT NULL,
  `status`       INT          NOT NULL DEFAULT 0,
  `user_id`      BIGINT,
  `publish_year` INT,
  `completed`    INT          NOT NULL DEFAULT 0,
  `view_count`   BIGINT       NOT NULL DEFAULT 0,
  `is_deleted`   INT          NOT NULL DEFAULT 0,
  `created_at`   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `chapter` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`     BIGINT       NOT NULL,
  `title`       VARCHAR(200),
  `chapter_num` DOUBLE       NOT NULL DEFAULT 1,
  `status`      INT          NOT NULL DEFAULT 1,
  `is_deleted`  INT          NOT NULL DEFAULT 0,
  `created_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `manga_page` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `chapter_id` BIGINT       NOT NULL,
  `page_num`   INT          NOT NULL DEFAULT 1,
  `image_url`  VARCHAR(500) NOT NULL,
  `is_deleted` INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS `novel_content` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `chapter_id`   BIGINT NOT NULL,
  `page_num`     INT    NOT NULL DEFAULT 1,
  `text_content` TEXT,
  `is_deleted`   INT    NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS `comment` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id`    BIGINT    NOT NULL,
  `user_id`    BIGINT    NOT NULL,
  `content`    TEXT      NOT NULL,
  `is_deleted` INT       NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT    NOT NULL,
  `work_id`    BIGINT    NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_work ON `favorite` (`user_id`, `work_id`);

CREATE TABLE IF NOT EXISTS `reading_progress` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT    NOT NULL,
  `work_id`    BIGINT    NOT NULL,
  `chapter_id` BIGINT    NOT NULL,
  `page_num`   INT       NOT NULL DEFAULT 0,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rp_user_work ON `reading_progress` (`user_id`, `work_id`);

CREATE TABLE IF NOT EXISTS `tag` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name`       VARCHAR(50) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_name ON `tag` (`name`);

CREATE TABLE IF NOT EXISTS `work_tag` (
  `id`      BIGINT AUTO_INCREMENT PRIMARY KEY,
  `work_id` BIGINT NOT NULL,
  `tag_id`  BIGINT NOT NULL
);
