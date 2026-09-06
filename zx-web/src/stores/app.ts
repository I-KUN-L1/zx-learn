import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'

/** 应用全局状态：主题切换 / 消息未读数 */
export const useAppStore = defineStore('app', () => {
  /** 深色主题 */
  const dark = ref<boolean>(getStorage<boolean>(STORAGE_KEYS.theme, false))
  /** 站内信未读数 */
  const unreadCount = ref<number>(0)

  function applyTheme() {
    document.documentElement.classList.toggle('dark', dark.value)
  }

  function toggleTheme() {
    dark.value = !dark.value
    setStorage(STORAGE_KEYS.theme, dark.value)
    applyTheme()
  }

  return { dark, unreadCount, applyTheme, toggleTheme }
})
