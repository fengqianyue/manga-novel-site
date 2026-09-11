import { useRouter } from 'vue-router'

/**
 * 返回上一页 composable（消除 5 个文件中的重复代码）
 * 用法: const { goBack } = useGoBack()
 *        const { goBack } = useGoBack('/fallback-path')  // 自定义兜底
 */
export function useGoBack(fallbackPath = '/') {
  const router = useRouter()
  function goBack() {
    if (window.history.length > 1) router.back()
    else router.push(fallbackPath)
  }
  return { goBack }
}
