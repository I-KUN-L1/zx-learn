package com.zhixing.course.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.Constant;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.course.domain.dto.CourseFormDTO;
import com.zhixing.course.domain.po.CourseDraft;
import com.zhixing.course.domain.vo.CourseDraftVO;
import com.zhixing.course.domain.vo.CourseVO;
import com.zhixing.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程管理
 * <p>
 * <b>权限矩阵</b>（与 {@code CourseService} 类内注释、前端按钮可见性三处对齐）：
 * <ul>
 *   <li>员工(1)：课程工作台全部操作 + 全量可见（不受归属限制）；</li>
 *   <li>教师(3)：课程工作台全部操作，但<b>仅限本人名下课程</b>
 *       （列表按 {@code teacher_id} 收敛，上架/下架/删除/草稿均做归属校验）；</li>
 *   <li>学员(2) 与访客：只读，且只能看到<b>已上架</b>课程。</li>
 * </ul>
 * 注解层 {@code @RequireRole} 只回答"角色对不对"，业务层
 * {@code CourseService#assertCourseManageable / #assertDraftManageable} 回答
 * "这门课是不是你的"，二者缺一都会留下越权口子。
 * <p>
 * 下架（status=0）课程的可见性口径见 {@link #page} 与 {@link #getById}：
 * 员工/教师保留可见（工作台要能重新上架/删除），访客与学员侧的列表一律不可见。
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/baseInfo/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<CourseDraft> getBaseInfo(@PathVariable Long id) {
        return R.ok(courseService.getBaseInfo(id));
    }

    @PostMapping("/baseInfo/save")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Long> saveBaseInfo(@RequestBody CourseFormDTO form) {
        return R.ok(courseService.saveBaseInfo(form));
    }

    /**
     * 草稿箱分页（编辑态）。
     * <p>
     * 与 {@link #page} 的区别：本接口查**草稿表 course_draft**（submitted=0），
     * {@link #page} 查**正式表 course**。前端「草稿箱 / 已发布」两个 Tab 分别对应二者，
     * 不要再用 status 去同表里区分——草稿根本不在 course 表，那样查必然空。
     */
    @GetMapping("/draft/page")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<PageDTO<CourseDraftVO>> draftPage(PageQuery query,
                                              @RequestParam(required = false) String name) {
        return R.ok(courseService.draftPage(query, name));
    }

    /** 删除草稿箱中的草稿（逻辑删除，不影响正式课程） */
    @DeleteMapping("/draft/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> deleteDraft(@PathVariable Long id) {
        courseService.deleteDraft(id);
        return R.ok();
    }

    /**
     * 课程详情。
     * <p>
     * <b>下架可见性口径</b>：
     * <ul>
     *   <li>员工(1)/教师(3)：可见任意课程（工作台需要查看下架课程详情、重新上架）；</li>
     *   <li>访客（网关未注入 user-info）：<b>仅</b>可见已上架课程。下架课程对访客直接拒绝，
     *       避免已下架课程通过历史链接/爬虫继续曝光（未公开信息泄露）；</li>
     *   <li>登录学员：接口仍返回数据（学习页需要课程目录才能渲染章节目录，
     *       而课程服务无法在不引入 course↔learning 循环依赖的前提下判定"是否已购"），
     *       由前端按 {@code status} + 「是否已拥有」分流：未购直接呈现"已下架"空态并关闭购买入口，
     *       已购学员保留继续学习入口。</li>
     * </ul>
     */
    @GetMapping("/{id}")
    public R<CourseVO> getById(@PathVariable Long id) {
        CourseVO vo = courseService.getCourseById(id);
        // 访客（网关未注入 user-info，role 为空）不得看到已下架课程：
        // 统一按"课程不存在"处理，不区分"不存在/已下架"，避免暴露该课程曾经存在。
        // ⚠ 校验必须在控制器层做，不能下沉到 CourseService#getCourseById ——
        //   内部 Feign 端点 GET /course/{id} 也复用该方法，而服务间调用不带头，
        //   下沉会导致学习服务读取下架课程时被误判成访客而拒绝。
        if (UserContext.getUser() == null
                && vo.getStatus() != null
                && vo.getStatus() != Constant.COURSE_STATUS_ON_SHELF) {
            throw new BadRequestException("课程不存在");
        }
        return R.ok(vo);
    }

    @PostMapping("/upShelf")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> upShelf(@RequestBody CourseFormDTO form) {
        courseService.upShelf(form.getId());
        return R.ok();
    }

    @PostMapping("/downShelf")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> downShelf(@RequestBody CourseFormDTO form) {
        courseService.downShelf(form.getId());
        return R.ok();
    }

    @GetMapping("/checkBeforeUpShelf/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> checkBeforeUpShelf(@PathVariable Long id) {
        courseService.checkBeforeUpShelf(id);
        return R.ok();
    }

    /**
     * 删除正式课程（逻辑删除）。
     * <p>
     * <b>角色权限规则</b>（与本方法注解、{@code CourseService#delete} 业务校验双重把关）：
     * <ul>
     *   <li>员工(1)/教师(3)：均可调用；</li>
     *   <li>学员(2)与其他角色：403；</li>
     * </ul>
     * <b>生效范围</b>：教师(3) 仅能删除<b>本人名下</b>课程（{@code course.teacher_id} = 当前登录用户），
     * 员工(1) 不受归属限制。归属判定见 {@code CourseService#delete(Long)}，
     * 注解只负责"角色对不对"，业务层负责"这门课是不是你的"，二者缺一都会留下越权删除口子。
     */
    @DeleteMapping("/delete/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
    }

    /**
     * 课程分页：课程列表、首页热门、课程发现、管理端课程工作台共用同一端点。
     * <p>
     * <b>可见范围按角色收敛</b>（权限矩阵见 {@code CourseService} 类内注释）：
     * <ul>
     *   <li>员工(1)：全量可见，{@code status} 按原语义筛选，不传则含已下架（工作台需要能重新上架/删除）；</li>
     *   <li>教师(3)：<b>仅本人名下课程</b>（{@code course.teacher_id = 本人}），
     *       与「下架 / 删除仅限本人」保持同一归属口径；{@code status} 按原语义筛选；</li>
     *   <li>访客与学员：<b>一律强制只看已上架</b>，且不允许用 {@code status} 参数枚举未上架/已下架课程
     *       （原先只在"匿名"时强制，登录学员不传 status 就会拿到全量，下架课程因此残留可见）。</li>
     * </ul>
     * ⚠ 不用三元表达式 `cond ? Constant.COURSE_STATUS_ON_SHELF : status`：
     * 常量是 int 原始类型，与 Integer status 混用会触发「二元数值提升 + 自动拆箱」，
     * 工作台不传 status（null）时直接 NPE，表现为课程管理页 500。
     * 这里用分支赋值，把 null 语义完整保留给工作台。
     */
    @GetMapping("/page")
    public R<PageDTO<CourseVO>> page(PageQuery query,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer status) {
        Integer effectiveStatus;
        Long ownerScope;
        if (UserContext.hasRole(UserRole.STAFF.getCode())) {
            effectiveStatus = status;
            ownerScope = null;
        } else if (UserContext.hasRole(UserRole.TEACHER.getCode())) {
            effectiveStatus = status;
            ownerScope = UserContext.getUser();
        } else {
            effectiveStatus = Constant.COURSE_STATUS_ON_SHELF;
            ownerScope = null;
        }
        return R.ok(courseService.pageQuery(query, name, effectiveStatus, ownerScope));
    }

    @GetMapping("/checkName")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> checkName(@RequestParam String name) {
        courseService.checkName(name);
        return R.ok();
    }
}
