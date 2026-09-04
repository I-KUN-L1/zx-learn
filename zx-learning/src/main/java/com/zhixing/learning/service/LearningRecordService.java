package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.dto.LearningProgressDTO;
import com.zhixing.learning.domain.po.LearningRecord;
import com.zhixing.learning.mapper.LearningRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习记录服务
 */
@Service
@RequiredArgsConstructor
public class LearningRecordService {

    private final LearningRecordMapper learningRecordMapper;

    /**
     * 提交学习进度。
     * 核心校验：学习进度不允许倒退（同一课程课时下只允许单调递增）。
     */
    public Long submitProgress(LearningProgressDTO form) {
        if (form.getCourseId() == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        if (form.getLessonId() == null) {
            throw new BadRequestException("课时 id 不能为空");
        }
        if (form.getProgress() == null || form.getProgress() < 0 || form.getProgress() > 100) {
            throw new BadRequestException("学习进度需在 0-100 之间");
        }
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<LearningRecord> query = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getCourseId, form.getCourseId())
                .eq(LearningRecord::getLessonId, form.getLessonId());
        LearningRecord record = learningRecordMapper.selectOne(query);
        if (record == null) {
            record = new LearningRecord();
            record.setUserId(userId);
            record.setCourseId(form.getCourseId());
            record.setLessonId(form.getLessonId());
            record.setProgress(form.getProgress());
            record.setFinished(record.getProgress() >= 100);
            record.setLearnDuration(form.getLearnDuration() == null ? 0 : form.getLearnDuration());
            record.setLastLearnTime(LocalDateTime.now());
            learningRecordMapper.insert(record);
            return record.getId();
        }
        // 进度不允许倒退
        if (form.getProgress() < record.getProgress()) {
            throw new BizIllegalException("学习进度不能倒退：当前 " + record.getProgress() + "，提交 " + form.getProgress());
        }
        record.setProgress(form.getProgress());
        record.setFinished(form.getProgress() >= 100);
        int existingDuration = record.getLearnDuration() == null ? 0 : record.getLearnDuration();
        record.setLearnDuration(existingDuration + (form.getLearnDuration() == null ? 0 : form.getLearnDuration()));
        record.setLastLearnTime(LocalDateTime.now());
        learningRecordMapper.updateById(record);
        return record.getId();
    }

    /**
     * 查询指定用户全部学习记录（内部 Feign 接口）
     */
    public List<LearningRecordDTO> listRecords(Long userId) {
        List<LearningRecord> records = learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .orderByDesc(LearningRecord::getLastLearnTime));
        return BeanUtils.copyList(records, LearningRecordDTO.class);
    }

    /**
     * 查询指定用户学习总时长（秒）（内部 Feign 接口）
     */
    public Long sumDuration(Long userId) {
        return learningRecordMapper.selectList(
                        new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId))
                .stream()
                .mapToLong(r -> r.getLearnDuration() == null ? 0L : r.getLearnDuration())
                .sum();
    }
}