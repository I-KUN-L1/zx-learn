package com.zhixing.learning.service;

import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.Note;
import com.zhixing.learning.mapper.NoteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习笔记服务单测：内容校验与笔记归属校验
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private NoteService service;

    @BeforeEach
    void setUp() {
        UserContext.setUser(10L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void addBlankContentRejected() {
        Note note = new Note();
        note.setContent("  ");
        assertThrows(BadRequestException.class, () -> service.add(note));
    }

    @Test
    void addSuccessSetsOwner() {
        Note note = new Note();
        note.setContent("Mybatis 复习要点");
        note.setLessonId(1L);

        service.add(note);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
    }

    @Test
    void updateOwnedNoteAllowed() {
        Note owned = new Note();
        owned.setId(5L);
        owned.setUserId(10L);
        owned.setContent("旧内容");
        when(noteMapper.selectById(5L)).thenReturn(owned);

        Note update = new Note();
        update.setId(5L);
        update.setContent("新内容");
        service.update(update);

        verify(noteMapper).updateById(any(Note.class));
        assertEquals("新内容", owned.getContent());
    }

    @Test
    void deleteOtherUserNoteRejected() {
        Note other = new Note();
        other.setId(5L);
        other.setUserId(99L);
        when(noteMapper.selectById(5L)).thenReturn(other);

        assertThrows(BadRequestException.class, () -> service.delete(5L));
    }
}