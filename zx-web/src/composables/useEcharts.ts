import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { echarts, type EChartsOption } from '@/utils/echarts'

/**
 * ECharts 按需引入封装：自动初始化 / 响应 option 更新 / 自适应尺寸 / 销毁
 */
export function useEcharts(el: Ref<HTMLElement | undefined>, option: Ref<EChartsOption | undefined>) {
  let chart: echarts.ECharts | null = null
  const ready = ref(false)

  function ensure() {
    if (!el.value) return null
    if (!chart) {
      chart = echarts.init(el.value)
      ready.value = true
    }
    return chart
  }

  function render() {
    const c = ensure()
    if (c && option.value) {
      c.setOption(option.value, true)
    }
  }

  function resize() {
    chart?.resize()
  }

  onMounted(() => {
    render()
    window.addEventListener('resize', resize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    chart?.dispose()
    chart = null
  })

  watch(option, render, { deep: true })

  return { ready, render, resize }
}
