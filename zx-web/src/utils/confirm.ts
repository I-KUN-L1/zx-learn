import { ElMessageBox } from 'element-plus'

/**
 * 危险操作确认框。
 *
 * ElMessageBox.confirm 在用户点「取消」或关闭弹窗时会 reject，
 * 直接 `await confirm(...).catch(() => null)` 会把「取消」吞掉后继续往下执行删除等危险操作。
 * 本工具把结果归一为布尔值：点「确认」返回 true，点「取消」/关闭返回 false，
 * 调用方必须 `if (!(await confirmAction(...))) return` 先行终止。
 */
export async function confirmAction(
  message: string,
  title: string,
  options: Parameters<typeof ElMessageBox.confirm>[2] = {},
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, options)
    return true
  } catch {
    /* 用户取消或关闭弹窗 */
    return false
  }
}
