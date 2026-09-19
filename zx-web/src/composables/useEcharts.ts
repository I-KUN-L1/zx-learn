import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { echarts, type EChartsOption } from '@/utils/echarts'

/**
 * ECharts 封装：自动初始化 / 响应 option 更新 / 容器级自适应 / 自动销毁。
 *
 * <h3>为什么需要这个实现（修复"教师端学员画像图表空白"）</h3>
 * 旧实现只在 `onMounted` 与 `watch(option)` 里调用 `echarts.init(el)`：
 * <ol>
 *   <li>图表容器常位于 {@code v-if="profile"} 或抽屉内，挂载时 `el.value` 仍是 `undefined`，
 *       初始化直接被跳过；</li>
 *   <li>Vue 的 `watch` 默认 `flush: 'pre'`，在组件重渲染<b>之前</b>执行 —— 数据到达触发 option 变化时，
 *       DOM 还没更新出容器，`el.value` 依然为空，于是图表永远不渲染；</li>
 *   <li>即使容器已存在但在 {@code display:none} / 0 尺寸下 init，ECharts 会拿到 0×0 画布，
 *       后续不再自适应，表现为"图表区域一片空白"。</li>
 * </ol>
 *
 * 修复要点：监听<b>元素本身</b>（`flush: 'post'`）→ 等 `nextTick` + `requestAnimationFrame`
 * 让布局稳定 → 容器仍不可见时逐帧重试 → 用 `ResizeObserver` 做容器级自适应。
 */
export function useEcharts(el: Ref<HTMLElement | undefined>, option: Ref<EChartsOption | undefined>) {
  let chart: echarts.ECharts | null = null
  let observer: ResizeObserver | null = null
  /** 容器已就绪并完成一次绘制 —— 供组件做淡入过渡 */
  const ready = ref(false)

  /** 容器是否已具备可绘制尺寸（0 尺寸时 init 会得到空白画布） */
  function hasSize(): boolean {
    const dom = el.value
    return !!dom && dom.clientWidth > 0 && dom.clientHeight > 0
  }

  function disposeChart() {
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
    ready.value = false
  }

  function ensureChart(): echarts.ECharts | null {
    if (chart) {
      return chart
    }
    if (!hasSize()) {
      return null
    }
    chart = echarts.init(el.value as HTMLElement)
    // 容器级自适应：抽屉展开/收起、侧栏折叠、栅格断点切换都能触发重排
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(() => chart?.resize())
      observer.observe(el.value as HTMLElement)
    }
    ready.value = true
    return chart
  }

  function render() {
    const instance = ensureChart()
    if (instance && option.value) {
      // notMerge=true：切换数据源时清掉旧系列，避免残影
      instance.setOption(option.value, true)
    }
  }

  /**
   * 等 DOM 更新与浏览器布局完成后再渲染。
   * 容器暂时不可见（如抽屉尚未展开）时，逐帧重试有限次数，避免无限空转。
   */
  function renderWhenReady(retry = 5) {
    nextTick(() => {
      requestAnimationFrame(() => {
        if (hasSize()) {
          render()
        } else if (retry > 0) {
          renderWhenReady(retry - 1)
        }
      })
    })
  }

  function resize() {
    chart?.resize()
  }

  // 元素出现/消失：出现则渲染，消失（v-if 卸载）则释放，避免图表实例泄漏
  watch(
    el,
    (dom) => {
      if (!dom) {
        disposeChart()
        return
      }
      // 容器被替换（v-if 重建）时，旧实例绑定的是已移除的 DOM，必须重建
      if (chart && chart.getDom() !== dom) {
        disposeChart()
      }
      renderWhenReady()
    },
    { flush: 'post' },
  )

  watch(option, () => renderWhenReady(), { deep: true })

  onMounted(() => {
    renderWhenReady()
    window.addEventListener('resize', resize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    disposeChart()
  })

  return { ready, render: () => renderWhenReady(), resize }
}
