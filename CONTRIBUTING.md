# 贡献指南（Contributing to zx-learn）

感谢你对 zx-learn 的关注！本项目虽为个人学习项目，同样欢迎 Issue 反馈、文档纠错与功能 PR。请花一分钟阅读以下约定。

## 🌿 分支命名

`main` 为唯一长期分支，功能开发请从最新 `main` 拉出**短生命周期**分支，命名 `<type>/<主题>`：

| 分支前缀 | 用途 | 示例 |
|---|---|---|
| `feat/` | 新功能 | `feat/seckill-rate-limit` |
| `fix/` | 缺陷修复 | `fix/gateway-401-forward` |
| `docs/` | 文档 | `docs/api-reference` |
| `refactor/` | 重构（不改行为） | `refactor/common-response` |
| `chore/` | 构建 / 依赖 / CI | `chore/jacoco-report` |

## 📝 提交规范（Conventional Commits）

```
<type>(<scope 可选>): <中文一句话主题>
```

- **type**：`feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` / `build` / `ci` / `chore` / `revert`
- **scope**：模块名小写（如 `promotion`、`gateway`、`aigc`），跨模块可省略
- **主题**：中文动宾短语，结尾不加句号；一次提交只做一件事
- 与仓库现有提交风格保持一致，例如：

```
feat(promotion): 秒杀领取 Redis Lua 原子预扣
fix(gateway): 修复匿名路由 401 后 user-info 残留透传
docs: 修正 PERF.md 中指向已移出仓库材料的引用
chore: 项目结构精简与隐私清理
```

## 🔄 PR 流程

1. **Issue 先行**：较大改动（新模块、架构调整、新增依赖）请先开 Issue 对齐方案，小改动可直接提 PR
2. **本地验证**：提交前跑通 CI 同款命令

   ```bash
   mvn -B -ntp clean verify
   ```

3. **发起 PR**：向 `main` 提交，标题遵循上述提交规范；描述写清 **动机 / 改动点 / 验证方式**
4. **CI 检查**：GitHub Actions（JDK 21 + `mvn verify` + JaCoCo）必须绿勾
5. **Review 合并**：维护者 review 通过后 squash merge，分支随之删除

## 🚫 提交红线

- 勿提交 `.env`、`.bootstrap-credentials`、日志、压测原始数据（`.gitignore` 已拦截，请勿强行 `git add -f`）
- 勿硬编码任何密码 / 密钥，一律走环境变量（`.env.example` 有模板）
- 勿绕过 CI（禁止 `--no-verify` 跳过钩子）

## 📮 其他

- Bug 反馈请附：复现步骤、期望/实际行为、相关日志（脱敏后）
- 文档类贡献（错别字、示例修正、FAQ 补充）尤其欢迎，零门槛
