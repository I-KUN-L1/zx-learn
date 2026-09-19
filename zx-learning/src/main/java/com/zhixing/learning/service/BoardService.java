package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.Board;
import com.zhixing.learning.domain.po.BoardReply;
import com.zhixing.learning.mapper.BoardMapper;
import com.zhixing.learning.mapper.BoardReplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程讨论服务（学习通式课程内容页的「讨论」区）。
 * <p>
 * 参与讨论是积分的重要来源：发布话题 +{@link PointsService#POINTS_TOPIC_CREATE}，
 * 回复 +{@link PointsService#POINTS_REPLY_CREATE}，由本服务在落库后调用
 * {@link PointsService#award} 发放（幂等键为帖子/回复主键，天然不会重复计分）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    /** 用户类型：员工（管理员）/ 学员 / 教师，与 user.type 对齐 */
    private static final int TYPE_STAFF = 1;

    private static final int MAX_TITLE_LEN = 255;
    private static final int MAX_CONTENT_LEN = 2000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BoardMapper boardMapper;
    private final BoardReplyMapper boardReplyMapper;
    private final PointsService pointsService;
    private final UserClient userClient;

    /**
     * 课程讨论区话题分页（置顶优先，其余按时间倒序）。
     */
    public PageDTO<Map<String, Object>> page(Long courseId, PageQuery query) {
        LambdaQueryWrapper<Board> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Board::getCourseId, courseId);
        }
        // 置顶优先 + 时间倒序：讨论区最常见的排序诉求
        wrapper.orderByDesc(Board::getTop).orderByDesc(Board::getCreateTime);
        Page<Board> page = boardMapper.selectPage(query.toMpPage(), wrapper);
        return PageDTO.of(page, page.getRecords().stream().map(BoardService::toVo).collect(Collectors.toList()));
    }

    /**
     * 话题详情：话题本体 + 全部回复（按时间正序，贴合论坛阅读顺序）。
     */
    public Map<String, Object> detail(Long boardId) {
        Board board = boardMapper.selectById(boardId);
        if (board == null) {
            throw new BadRequestException("话题不存在或已删除");
        }
        Map<String, Object> vo = toVo(board);
        List<Map<String, Object>> replies = boardReplyMapper.selectList(
                        new LambdaQueryWrapper<BoardReply>()
                                .eq(BoardReply::getBoardId, boardId)
                                .orderByAsc(BoardReply::getCreateTime))
                .stream().map(BoardService::toReplyVo).collect(Collectors.toList());
        vo.put("replies", replies);
        return vo;
    }

    /**
     * 发布话题（学员）。
     *
     * @return 新话题 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createTopic(Board form) {
        if (form == null || StringUtils.isBlank(form.getTitle())) {
            throw new BadRequestException("话题标题不能为空");
        }
        if (form.getTitle().length() > MAX_TITLE_LEN) {
            throw new BadRequestException("话题标题不能超过 " + MAX_TITLE_LEN + " 个字符");
        }
        if (form.getContent() != null && form.getContent().length() > MAX_CONTENT_LEN) {
            throw new BadRequestException("话题内容不能超过 " + MAX_CONTENT_LEN + " 个字符");
        }
        if (form.getCourseId() == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        Long userId = UserContext.getUserId();
        Board board = new Board();
        board.setCourseId(form.getCourseId());
        board.setUserId(userId);
        // 昵称快照：列表展示不依赖跨服务调用，用户服务不可用时也不影响讨论区可用性
        board.setUserName(currentUserName(userId));
        board.setTitle(form.getTitle().trim());
        board.setContent(form.getContent());
        board.setReplyCount(0);
        board.setTop(0);
        boardMapper.insert(board);

        // 参与讨论自动加分（幂等键 = 话题 id）
        pointsService.award(userId, PointsService.SOURCE_DISCUSSION, PointsService.POINTS_TOPIC_CREATE,
                "发布讨论话题《" + abbreviate(board.getTitle()) + "》", String.valueOf(board.getId()));
        return board.getId();
    }

    /**
     * 回复话题（学员）：落库 + 回复数自增 + 积分发放。
     * <p>
     * 回复数与积分都以回复记录自身为准，避免"回复数不准导致积分规则误判"。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createReply(BoardReply form) {
        if (form == null || form.getBoardId() == null) {
            throw new BadRequestException("话题 id 不能为空");
        }
        if (StringUtils.isBlank(form.getContent())) {
            throw new BadRequestException("回复内容不能为空");
        }
        if (form.getContent().length() > MAX_CONTENT_LEN) {
            throw new BadRequestException("回复内容不能超过 " + MAX_CONTENT_LEN + " 个字符");
        }
        Board board = boardMapper.selectById(form.getBoardId());
        if (board == null) {
            throw new BadRequestException("话题不存在或已删除");
        }
        Long userId = UserContext.getUserId();
        BoardReply reply = new BoardReply();
        reply.setBoardId(form.getBoardId());
        reply.setUserId(userId);
        reply.setUserName(currentUserName(userId));
        reply.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        reply.setContent(form.getContent());
        boardReplyMapper.insert(reply);

        board.setReplyCount((board.getReplyCount() == null ? 0 : board.getReplyCount()) + 1);
        boardMapper.updateById(board);

        pointsService.award(userId, PointsService.SOURCE_REPLY, PointsService.POINTS_REPLY_CREATE,
                "参与讨论回复", String.valueOf(reply.getId()));
        return reply.getId();
    }

    /**
     * 删除话题：作者本人或管理员可删；级联清理回复，避免孤儿数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopic(Long boardId) {
        Board board = boardMapper.selectById(boardId);
        if (board == null) {
            return;
        }
        checkOwnerOrStaff(board.getUserId());
        boardReplyMapper.delete(new LambdaQueryWrapper<BoardReply>().eq(BoardReply::getBoardId, boardId));
        boardMapper.deleteById(boardId);
    }

    /**
     * 删除回复：作者本人或管理员可删，并回写话题回复数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long replyId) {
        BoardReply reply = boardReplyMapper.selectById(replyId);
        if (reply == null) {
            return;
        }
        checkOwnerOrStaff(reply.getUserId());
        boardReplyMapper.deleteById(replyId);
        Board board = boardMapper.selectById(reply.getBoardId());
        if (board != null) {
            int count = Math.max(0, (board.getReplyCount() == null ? 0 : board.getReplyCount()) - 1);
            board.setReplyCount(count);
            boardMapper.updateById(board);
        }
    }

    /* ==================== 私有 ==================== */

    /** 仅作者本人或员工（管理员）可操作 */
    private void checkOwnerOrStaff(Long ownerId) {
        Long current = UserContext.getUserId();
        if (ownerId != null && ownerId.equals(current)) {
            return;
        }
        if (UserContext.hasRole(TYPE_STAFF)) {
            return;
        }
        throw new ForbiddenException("只能操作自己发布的内容");
    }

    /**
     * 昵称快照。
     * 当前登录名由网关透传的 user-info 只含 id，不含昵称，
     * 因此生成一个稳定可读的占位名；用户服务可用时前端会以实时昵称覆盖展示。
     */
    private String currentUserName(Long userId) {
        return "学员 #" + userId;
    }

    private static Map<String, Object> toVo(Board b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("courseId", b.getCourseId());
        m.put("userId", b.getUserId());
        m.put("userName", b.getUserName());
        m.put("title", b.getTitle());
        m.put("content", b.getContent());
        m.put("replyCount", b.getReplyCount() == null ? 0 : b.getReplyCount());
        m.put("top", b.getTop() == null ? 0 : b.getTop());
        m.put("createTime", b.getCreateTime() == null ? null : TIME_FMT.format(b.getCreateTime()));
        return m;
    }

    private static Map<String, Object> toReplyVo(BoardReply r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("boardId", r.getBoardId());
        m.put("userId", r.getUserId());
        m.put("userName", r.getUserName());
        m.put("parentId", r.getParentId() == null ? 0L : r.getParentId());
        m.put("content", r.getContent());
        m.put("createTime", r.getCreateTime() == null ? null : TIME_FMT.format(r.getCreateTime()));
        return m;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }
}
