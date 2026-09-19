-- =====================================================================
-- 2026-09-16 修复：课程封面死链 + 教师端草稿箱保存
-- =====================================================================
-- 缺陷 1：部分课程封面无法显示
--   现象：课程 3009~3012 的封面是裂图（前端 CourseCover 的渐变兜底把它掩盖了，
--         只看页面不容易发现「图挂了」）。
--   根因：这 4 门课的 cover_url 指向文生图接口
--         https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=...&image_size=...
--         该接口是「按需生成」的服务端点，**不是静态图片资源**，现已返回
--         HTTP 404（实测 curl -I => 404 Not Found；同批次的 course-01~09 走 OSS 的
--         https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-0X.svg 均 200）。
--         ⚠ 注意：**不是** cover_url 列宽问题 —— 列早已加宽到 VARCHAR(512)，
--         实际地址长度 269~285 字符，写入并没有被截断。长度只是表象。
--   修复：把这 4 门课的封面切到 OSS 静态资源 covers/course-10~13.svg
--         （源文件：tmp-covers/course-1X.svg，已随本次修复上传到 OSS 并设为对象级公共读）。
--
-- 缺陷 2：教师端创建课程无法保存进草稿箱
--   现象：新建课程点「保存」提示成功，但「草稿箱（编辑态）」Tab 一直是空的。
--   根因（详见 zx-course / zx-web 代码注释）：
--     a. 草稿箱列表走的是 /courses/page?status=2，查的是**正式表 course**，
--        而草稿存在 course_draft，course.status 只有 0/1/2 → 草稿永远查不出来；
--     b. course_draft 没有存章节目录的列，前端第 2 步填的章节被静默丢弃。
--   修复：course_draft 增加 catalogue_json 列（草稿目录 JSON），
--         配合后端新增 /courses/draft/page、DELETE /courses/draft/{id}。
--
-- 全部语句幂等，可重复执行。
-- =====================================================================

USE `zx_course`;

-- ---------- A. 缺陷 2：course_draft 增加 catalogue_json（草稿章节目录 JSON） ----------
-- MySQL 8 没有 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA + PREPARE/EXECUTE 做幂等。
SET @ddl := (SELECT IF(COUNT(*) > 0, 'DO 0',
  'ALTER TABLE `course_draft` ADD COLUMN `catalogue_json` MEDIUMTEXT NULL COMMENT ''草稿章节目录(JSON)'' AFTER `description`')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'zx_course' AND TABLE_NAME = 'course_draft' AND COLUMN_NAME = 'catalogue_json');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- B. 缺陷 1：封面死链切到 OSS 静态资源 ----------
-- 按 id 精确映射；同一批里若还有其它行残留该失效域名，兜底到默认封面 covers/course-09.svg。
UPDATE `course`
SET `cover_url` = CASE `id`
        WHEN 3009 THEN 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-10.svg'
        WHEN 3010 THEN 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-11.svg'
        WHEN 3011 THEN 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-12.svg'
        WHEN 3012 THEN 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-13.svg'
        ELSE 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-09.svg'
    END,
    `update_time` = NOW()
WHERE `cover_url` LIKE '%trae-api-cn.mchost.guru%';

-- 草稿表同理（早期 mock 适配器把同一个失效域名当默认封面写进过草稿）。
UPDATE `course_draft`
SET `cover_url` = 'https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-09.svg',
    `update_time` = NOW()
WHERE `cover_url` LIKE '%trae-api-cn.mchost.guru%';

-- ---------- C. 校验（人工执行看结果，不属于迁移的一部分） ----------
-- SELECT id, status, cover_url FROM `course` WHERE `cover_url` LIKE '%trae-api-cn%';   -- 应返回 0 行
