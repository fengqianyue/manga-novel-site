import { ref, watchEffect } from 'vue'

// 全局暗色模式状态（单例，跨组件共享）
const isDark = ref(localStorage.getItem('theme') === 'dark')

// 应用到 <html> 元素
watchEffect(() => {
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
})

export function useDarkMode() {
  function toggle() {
    isDark.value = !isDark.value
  }
  return { isDark, toggle }
}
