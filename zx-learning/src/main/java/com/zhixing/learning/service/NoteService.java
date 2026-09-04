package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.Note;
import com.zhixing.learning.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学习笔记服务
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteMapper noteMapper;

    /**
     * 新增笔记
     */
    public Long add(Note note) {
        if (note == null || StringUtils.isBlank(note.getContent())) {
            throw new BadRequestException("笔记内容不能为空");
        }
        note.setUserId(UserContext.getUserId());
        noteMapper.insert(note);
        return note.getId();
    }

    /**
     * 更新自己的笔记
     */
    public void update(Note note) {
        Note exist = getOwned(note.getId());
        exist.setContent(note.getContent());
        exist.setPrivacy(note.getPrivacy());
        noteMapper.updateById(exist);
    }

    /**
     * 删除自己的笔记
     */
    public void delete(Long id) {
        getOwned(id);
        noteMapper.deleteById(id);
    }

    /**
     * 分页查询当前用户笔记
     */
    public List<Note> page() {
        return noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, UserContext.getUserId())
                .orderByDesc(Note::getCreateTime));
    }

    /**
     * 校验笔记归属当前用户
     */
    private Note getOwned(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(UserContext.getUserId())) {
            throw new BadRequestException("笔记不存在或无权操作");
        }
        return note;
    }
}