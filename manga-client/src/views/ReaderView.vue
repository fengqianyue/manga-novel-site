<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ========== 核心数据 ==========
const chapter = ref(null)
const pages = ref([])
const allChapters = ref([])
const currentPage = ref(0)
const loading = ref(true)
const showUI = ref(true)
const imageError = ref(false)
const isNovel = ref(false)
const novelText = ref('')
const fontSize = ref(18)
const darkMode = ref(false)
let hideTimer = null
let saveTimer = null

// ========== 计算属性 ==========
const workId = computed(() => Number(route.params.workId))
const chapterId = computed(() => Number(route.params.chapterId))
const totalPages = computed(() => pages.value.length)

const prevChapter = computed(() => {
  const idx = allChapters.value.findIndex(c => c.id === chapterId.value)
  return idx > 0 ? allChapters.value[idx - 1] : null
})
const nextChapter = computed(() => {
  const idx = allChapters.value.findIndex(c => c.id === chapterId.value)
  return idx < allChapters.value.length - 1 ? allChapters.value[idx + 1] : null
})
const currentImageUrl = computed(() => {
  const p = pages.value[currentPage.value]
  if (!p) return ''
  return `/uploads/${p.imageUrl}`
})
const formattedNovelText = computed(() => {
  if (!novelText.value) return ''
  return novelText.value
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>').replace(/$/, '</p>')
})

// ========== 加载数据 ==========
async function loadChapter(chId, startPage) {
  loading.value = true
  imageError.value = false
  const wid = workId.value
  if (!wid || isNaN(wid)) {
    ElMessage.error('作品 ID 无效')
    loading.value = false
    return
  }
  try {
    const [chRes, allChRes, workRes] = await Promise.all([
      request.get(`/chapter/${chId}`),
      request.get(`/chapter/list/${wid}`),
      request.get(`/work/${wid}`),
    ])
    chapter.value = chRes.data
    allChapters.value = allChRes.data
    isNovel.value = workRes.data.type === 'novel'

    if (isNovel.value) {
      pages.value = []
      const ncRes = await request.get(`/novel-content/${chId}`)
      novelText.value = ncRes.data?.textContent || ''
    } else {
      novelText.value = ''
      const pagesRes = await request.get(`/manga-page/list/${chId}`)
      pages.value = pagesRes.data
      const maxPage = pagesRes.data.length - 1
      if (startPage !== undefined && startPage >= 0 && startPage <= maxPage) {
        currentPage.value = startPage
      } else {
        currentPage.value = 0
      }
    }
    // 进入章节即保存进度（覆盖"不翻页直接退出"的场景）
    scheduleSaveProgress()
  } catch (e) {
    // 不弹错误 —— 加载过程中 500 错误已由 request 拦截器处理，这里只做静默恢复
    console.error('reader load failed:', e)
  } finally {
    loading.value = false
    resetHideTimer()
  }
}

// ========== route 参数监听：处理 URL 跳转 ==========
watch(() => route.params.chapterId, (newChId, oldChId) => {
  if (newChId && newChId !== oldChId) {
    const pageNum = route.params.pageNum ? Number(route.params.pageNum) - 1 : 0
    loadChapter(Number(newChId), pageNum)
  }
}, { immediate: false })

onMounted(async () => {
  const pageNum = route.params.pageNum ? Number(route.params.pageNum) - 1 : 0
  await loadChapter(chapterId.value, pageNum)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  clearTimeout(hideTimer)
  flushSaveProgress()
})

// ========== 翻页 ==========
function goToPage(idx) {
  if (idx < 0 || idx >= totalPages.value) return
  currentPage.value = idx
  imageError.value = false
  resetHideTimer()
  scheduleSaveProgress()
  // 更新 URL 页码
  router.replace(`/reader/${workId.value}/${chapterId.value}/${idx + 1}`).catch(() => {})
}

function nextPage() {
  if (currentPage.value < totalPages.value - 1) {
    goToPage(currentPage.value + 1)
  } else if (nextChapter.value) {
    // 当前话翻完，跳下一话第一页
    router.push(`/reader/${workId.value}/${nextChapter.value.id}`)
  }
}

function prevPage() {
  if (currentPage.value > 0) {
    goToPage(currentPage.value - 1)
  } else if (prevChapter.value) {
    // 来到第一页左边，跳上一话最后一页（先快速查一下上一话有多少页）
    jumpToChapterLastPage(prevChapter.value.id)
  }
}

async function jumpToChapterLastPage(chId) {
  try {
    const pagesRes = await request.get(`/manga-page/list/${chId}`)
    const lastPage = pagesRes.data.length
    router.push(`/reader/${workId.value}/${chId}/${lastPage}`)
  } catch {
    router.push(`/reader/${workId.value}/${chId}`)
  }
}

function goChapter(chId) {
  router.push(`/reader/${workId.value}/${chId}`)
}

// ========== 进度保存 ==========
function scheduleSaveProgress() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => flushSaveProgress(), 500)
}

function flushSaveProgress() {
  clearTimeout(saveTimer)
  const canSave = userStore.isLoggedIn || userStore.isAdminAuth
  if (canSave && chapter.value) {
    request.post('/reading-progress/save', {
      workId: workId.value,
      chapterId: chapter.value.id,
      pageNum: currentPage.value + 1,
    }, { silent: true }).catch(() => {})
  }
}

// ========== UI 自动隐藏 ==========
function resetHideTimer() {
  showUI.value = true
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => { showUI.value = false }, 3000)
}

// ========== 键盘 ==========
function handleKeydown(e) {
  if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    e.preventDefault()
    if (e.key === 'ArrowRight') nextPage()
    else prevPage()
  }
}

// ========== 点击翻页 ==========
function onImageClick(e) {
  const x = e.clientX - e.currentTarget.getBoundingClientRect().left
  const w = e.currentTarget.getBoundingClientRect().width
  if (x < w * 0.3) prevPage()
  else nextPage()
}

function onImageError() {
  imageError.value = true
}
</script>

<template>
  <div class="reader">
    <!-- 加载中 -->
    <div v-if="loading" class="reader-loading">加载中...</div>

    <!-- 内容 -->
    <template v-else-if="pages.length > 0 || (isNovel && novelText)">
      <!-- 顶部栏 -->
      <div class="reader-top" :class="{ hidden: !showUI }">
        <a class="top-btn" @click="router.push('/work/' + workId)">← 返回</a>
        <span>{{ chapter?.title }}</span>
        <span>{{ currentPage + 1 }} / {{ totalPages }}</span>
      </div>

      <!-- 图片（漫画） -->
      <div v-if="!isNovel" class="reader-viewport" @click="onImageClick">
        <img :src="currentImageUrl" class="reader-img" @error="onImageError" draggable="false" />
        <div v-if="imageError" class="img-error">图片加载失败</div>
      </div>

      <!-- 小说文本 -->
      <div v-else class="novel-viewport" :class="{ dark: darkMode }" @click="resetHideTimer">
        <div class="novel-toolbar" :class="{ hidden: !showUI }">
          <span class="toolbar-chapter">{{ chapter?.title }}</span>
          <div class="toolbar-actions">
            <el-button size="small" @click="fontSize = Math.max(14, fontSize - 2)">A-</el-button>
            <span style="color:#888;font-size:12px">{{ fontSize }}px</span>
            <el-button size="small" @click="fontSize = Math.min(28, fontSize + 2)">A+</el-button>
            <el-button size="small" @click="darkMode = !darkMode">{{ darkMode ? '☀' : '🌙' }}</el-button>
          </div>
        </div>
        <article class="novel-text" :style="{ fontSize: fontSize + 'px' }">
          <h2 class="novel-title">{{ chapter?.title }}</h2>
          <div v-html="formattedNovelText"></div>
        </article>
      </div>

      <!-- 底部栏 -->
      <div v-if="!isNovel" class="reader-bottom" :class="{ hidden: !showUI }">
        <button class="ch-btn" :disabled="!prevChapter" @click="goChapter(prevChapter.id)">← {{ prevChapter?.title || '--' }}</button>
        <span class="bottom-page">{{ currentPage + 1 }} / {{ totalPages }}</span>
        <button class="ch-btn" :disabled="!nextChapter" @click="goChapter(nextChapter.id)">{{ nextChapter?.title || '--' }} →</button>
      </div>
      <!-- 小说底部 -->
      <div v-else class="reader-bottom novel-bottom" :class="{ hidden: !showUI }">
        <button class="ch-btn" :disabled="!prevChapter" @click="goChapter(prevChapter.id)">← {{ prevChapter?.title || '--' }}</button>
        <span style="color:#888;font-size:13px">{{ chapter?.title }}</span>
        <button class="ch-btn" :disabled="!nextChapter" @click="goChapter(nextChapter.id)">{{ nextChapter?.title || '--' }} →</button>
      </div>
    </template>

    <!-- 空 -->
    <div v-else class="reader-empty">
      <p>该章节暂无内容</p>
      <a @click="router.push('/work/' + workId)">返回作品详情</a>
    </div>

    <!-- 翻页提示 -->
    <div v-if="pages.length > 0 && showUI" class="reader-hint">
      点击左/右翻页 · 键盘 ← →
    </div>
  </div>
</template>

<style scoped>
.reader {
  width: 100vw; height: 100vh;
  background: #1a1a1a;
  display: flex; flex-direction: column;
  overflow: hidden;
  user-select: none;
  position: relative;
}
.reader-loading, .reader-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; color: #999; gap: 12px;
}
.reader-empty a { color: #8d56da; cursor: pointer; }

/* 顶部 */
.reader-top {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; height: 44px;
  background: rgba(0,0,0,0.75); color: #ccc; font-size: 14px;
  transition: opacity 0.3s; position: relative; z-index: 10;
}
.reader-top.hidden { opacity: 0; pointer-events: none; }
.top-btn { color: #8d56da; cursor: pointer; }

/* 图片区 */
.reader-viewport {
  flex: 1; display: flex; align-items: center; justify-content: center;
  cursor: pointer; min-height: 0;
}
.reader-img {
  max-width: 100%; max-height: calc(100vh - 88px);
  object-fit: contain;
}
.img-error { color: #d50707; font-size: 14px; }

/* 底部 */
.reader-bottom {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; height: 44px;
  background: rgba(0,0,0,0.75);
  transition: opacity 0.3s;
}
.reader-bottom.hidden { opacity: 0; pointer-events: none; }
.ch-btn {
  background: none; border: 1px solid rgba(255,255,255,0.2);
  color: #bbb; padding: 4px 14px; border-radius: 4px;
  cursor: pointer; font-size: 13px; max-width: 140px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.ch-btn:hover:not(:disabled) { border-color: #8d56da; color: #8d56da; }
.ch-btn:disabled { opacity: 0.25; cursor: default; }
.bottom-page { color: #777; font-size: 13px; }

/* 提示 */
.reader-hint {
  position: absolute; bottom: 56px; left: 50%; transform: translateX(-50%);
  color: rgba(255,255,255,0.3); font-size: 13px; pointer-events: none;
}

/* === 小说 === */
.novel-viewport {
  flex: 1; overflow-y: auto; background: #faf9f6; padding: 0;
}
.novel-viewport.dark { background: #1a1a1a; color: #ccc; }
.novel-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px; background: #fff; border-bottom: 1px solid #eee;
  position: sticky; top: 0; z-index: 5;
  transition: opacity 0.3s;
}
.novel-toolbar.hidden { opacity: 0; pointer-events: none; }
.dark .novel-toolbar { background: #2a2a2a; border-color: #333; }
.toolbar-chapter { font-size: 14px; color: #666; }
.dark .toolbar-chapter { color: #aaa; }
.toolbar-actions { display: flex; align-items: center; gap: 6px; }
.novel-text { max-width: 720px; margin: 0 auto; padding: 32px 24px 80px; line-height: 2; }
.novel-title { font-size: 1.4em; font-weight: 700; text-align: center; margin-bottom: 28px; color: #333; }
.dark .novel-title { color: #ddd; }
.novel-text p { text-indent: 2em; margin-bottom: 0.8em; }
.novel-bottom { background: rgba(0,0,0,0.85) !important; }
</style>
