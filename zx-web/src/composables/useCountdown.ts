import { computed, onBeforeUnmount, ref } from 'vue'
import { formatCountdown } from '@/utils/format'

/**
 * 倒计时（用于订单 15 分钟超时关单 / 秒杀开始倒计时）
 * @param initialSeconds 初始秒数
 */
export function useCountdown(initialSeconds = 0) {
  const seconds = ref(initialSeconds)
  let timer: ReturnType<typeof setInterval> | null = null

  const formatted = computed(() => formatCountdown(Math.max(0, seconds.value)))
  const isFinished = computed(() => seconds.value <= 0)

  function start(from?: number) {
    stop()
    if (from != null) seconds.value = from
    if (seconds.value <= 0) return
    timer = setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) {
        stop()
      }
    }, 1000)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onBeforeUnmount(stop)

  return { seconds, formatted, isFinished, start, stop }
}
