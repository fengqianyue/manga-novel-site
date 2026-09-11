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
const scrollMode = ref(false)       // 滚动模式（Webtoon风格）
const scrollRef = ref(null)         // 滚动容器的 ref
const aiSummary = ref('')           // AI 生成的章节摘要
const aiLoading = ref(false)        // AI 摘要加载中
const isSpeaking = ref(false)       // TTS 语音朗读状态
const ttsRate = ref(1.0)            // 语速 (0.5 - 2.0)
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

// 返回上一页，兜底跳作品详情
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/work/' + workId.value)
}

onMounted(async () => {
  const pageNum = route.params.pageNum ? Number(route.params.pageNum) - 1 : 0
  await loadChapter(chapterId.value, pageNum)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  clearTimeout(hideTimer)
  flushSaveProgress()
  speechSynthesis.cancel()
  if (scrollObserver) { scrollObserver.disconnect(); scrollObserver = null }
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

// ========== 移动端触摸滑动 ==========
let touchStartX = 0
let touchStartY = 0

function onTouchStart(e) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
}

function onTouchEnd(e) {
  const dx = e.changedTouches[0].clientX - touchStartX
  const dy = e.changedTouches[0].clientY - touchStartY
  // 水平滑动超过50px且超过垂直滑动距离才触发翻页（避免上下滚动误触）
  if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
    if (dx < 0) nextPage()
    else prevPage()
  }
}

function onImageError() {
  imageError.value = true
}

// ========== AI 章节摘要 ==========
async function generateAiSummary() {
  aiLoading.value = true
  aiSummary.value = ''
  try {
    const res = await request.post('/ai/summary', { chapterId: chapterId.value }, { silent: true })
    aiSummary.value = res.data.summary || 'AI 暂时无法生成摘要'
  } catch {
    aiSummary.value = '摘要生成失败，请检查 AI 服务配置'
  } finally {
    aiLoading.value = false
  }
}

// ========== TTS 语音朗读 ==========
function toggleTts() {
  if (isSpeaking.value) {
    speechSynthesis.cancel()
    isSpeaking.value = false
    return
  }
  if (!novelText.value) return
  // 清除纯文本标记用于朗读
  const plainText = novelText.value.replace(/<[^>]*>/g, '').trim()
  if (!plainText) return
  const utterance = new SpeechSynthesisUtterance(plainText)
  utterance.lang = 'zh-CN'
  utterance.rate = ttsRate.value
  utterance.onend = () => { isSpeaking.value = false }
  utterance.onerror = () => { isSpeaking.value = false }
  speechSynthesis.speak(utterance)
  isSpeaking.value = true
}

function changeTtsRate(delta) {
  ttsRate.value = Math.max(0.5, Math.min(2.0, +(ttsRate.value + delta).toFixed(1)))
  if (isSpeaking.value) {
    speechSynthesis.cancel()
    isSpeaking.value = false
    toggleTts()  // 重新开始朗读以应用新语速
  }
}

// ========== 滚动模式 - IntersectionObserver 进度追踪 ==========
let scrollObserver = null

function setupScrollObserver() {
  if (!scrollRef.value || !scrollMode.value) return
  scrollObserver = new IntersectionObserver((entries) => {
    // 找到最可见的图片作为当前页
    let maxRatio = 0
    let bestIdx = 0
    entries.forEach(e => {
      if (e.intersectionRatio > maxRatio) {
        maxRatio = e.intersectionRatio
        const pageNum = parseInt(e.target.dataset.page)
        if (!isNaN(pageNum)) bestIdx = pageNum
      }
    })
    if (entries.length > 0 && maxRatio > 0.3) {
      currentPage.value = bestIdx
      scheduleSaveProgress()
    }
  }, { threshold: [0.3, 0.5, 0.7] })

  const imgs = scrollRef.value.querySelectorAll('.scroll-img')
  imgs.forEach(img => scrollObserver.observe(img))
}

watch(scrollMode, (val) => {
  if (val) {
    document.querySelector('.reader-bottom')?.classList.add('hidden')
    setTimeout(() => setupScrollObserver(), 100)
  } else {
    if (scrollObserver) { scrollObserver.disconnect(); scrollObserver = null }
    document.querySelector('.reader-bottom')?.classList.remove('hidden')
  }
})
</script>

<template>
  <div class="reader" :class="{ 'reader-light': darkMode && !isNovel }">
    <!-- 加载中 -->
    <div v-if="loading" class="reader-loading">加载中...</div>

    <!-- 内容 -->
    <template v-else-if="pages.length > 0 || (isNovel && novelText)">
      <!-- 顶部栏 -->
      <div class="reader-top" :class="{ hidden: !showUI }">
        <a class="top-btn" @click.prevent="goBack">← 返回</a>
        <span>{{ chapter?.title }}</span>
        <div class="top-right">
          <template v-if="!isNovel">
            <button class="mode-toggle" @click="darkMode = !darkMode" :title="darkMode ? '亮色背景' : '深色背景'">{{ darkMode ? '☀' : '🌙' }}</button>
            <button class="mode-toggle" @click="scrollMode = !scrollMode">{{ scrollMode ? '📖 翻页' : '📜 滚动' }}</button>
          </template>
          <span class="page-indicator">{{ currentPage + 1 }} / {{ totalPages }}</span>
        </div>
      </div>

      <!-- 图片（漫画）- 翻页模式 -->
      <div v-if="!isNovel && !scrollMode" class="reader-viewport"
        @click="onImageClick"
        @touchstart="onTouchStart"
        @touchend="onTouchEnd">
        <img :src="currentImageUrl" class="reader-img" @error="onImageError" draggable="false" />
        <div v-if="imageError" class="img-error">图片加载失败</div>
      </div>

      <!-- 图片（漫画）- 滚动模式 (Webtoon) -->
      <div v-else-if="!isNovel && scrollMode" ref="scrollRef" class="scroll-viewport" @click="resetHideTimer">
        <div v-for="(p, idx) in pages" :key="p.id" class="scroll-page">
          <img
            :src="'/uploads/' + p.imageUrl"
            class="scroll-img"
            :data-page="idx"
            draggable="false"
            loading="lazy"
          />
          <span class="scroll-page-num">{{ idx + 1 }} / {{ totalPages }}</span>
        </div>
        <div v-if="nextChapter" class="scroll-next-chapter" @click="goChapter(nextChapter.id)">
          下一话：{{ nextChapter.title }} →
        </div>
      </div>

      <!-- 小说文本 -->
      <div v-else class="novel-viewport" :class="{ dark: darkMode }" @click="resetHideTimer">
        <div class="novel-toolbar" :class="{ hidden: !showUI }">
          <span class="toolbar-chapter">{{ chapter?.title }}</span>
          <div class="toolbar-actions">
            <el-button size="small" @click="generateAiSummary" :loading="aiLoading">🤖 AI摘要</el-button>
            <el-button size="small" @click="toggleTts" :type="isSpeaking ? 'warning' : ''">
              {{ isSpeaking ? '⏸ 暂停' : '🔊 朗读' }}
            </el-button>
            <template v-if="isSpeaking">
              <el-button size="small" @click="changeTtsRate(-0.5)" :disabled="ttsRate <= 0.5">慢速</el-button>
              <span style="color:#888;font-size:11px">{{ ttsRate }}x</span>
              <el-button size="small" @click="changeTtsRate(0.5)" :disabled="ttsRate >= 2.0">快速</el-button>
            </template>
            <el-button size="small" @click="fontSize = Math.max(14, fontSize - 2)">A-</el-button>
            <span style="color:#888;font-size:12px">{{ fontSize }}px</span>
            <el-button size="small" @click="fontSize = Math.min(28, fontSize + 2)">A+</el-button>
            <el-button size="small" @click="darkMode = !darkMode">{{ darkMode ? '☀' : '🌙' }}</el-button>
          </div>
        </div>
        <div v-if="aiSummary" class="ai-summary-card">
          <div class="ai-summary-text">{{ aiSummary }}</div>
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
      <a @click.prevent="goBack">返回作品详情</a>
    </div>

    <!-- 翻页提示 -->
    <div v-if="pages.length > 0 && showUI" class="reader-hint">
      <span class="hint-desktop">点击左/右翻页 · 键盘 ← →</span>
      <span class="hint-mobile">点击左/右翻页 · 左右滑动</span>
    </div>
  </div>
</template>

<style scoped>
.reader {
  width: 100vw; height: 100vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  user-select: none;
  position: relative;
  transition: background 0.4s;
}
.reader { background: #1a1a1a; }
/* 亮色模式漫画阅读器不适用此规则，改用类名控制 */
.reader.reader-light { background: #f0f0f0; }
.reader-loading, .reader-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; color: #999; gap: 12px;
}
.reader-light .reader-loading, .reader-light .reader-empty { color: #777; }
.reader-empty a { color: var(--accent); cursor: pointer; }

/* 顶部 */
.reader-top {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; height: 44px;
  background: rgba(0,0,0,0.75); color: #ccc; font-size: 14px;
  transition: opacity 0.3s; position: relative; z-index: 10;
}
.reader-top.hidden { opacity: 0; pointer-events: none; }
.reader-light .reader-top { background: rgba(240,240,240,0.9); color: #444; }
.reader-light .reader-bottom { background: rgba(240,240,240,0.9); }
.reader-light .ch-btn { color: #555; border-color: rgba(0,0,0,0.15); }
.reader-light .ch-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.reader-light .bottom-page { color: #999; }
.reader-light .mode-toggle { color: #555; border-color: rgba(0,0,0,0.15); }
.reader-light .mode-toggle:hover { color: var(--accent); border-color: var(--accent); }
.reader-light .page-indicator { color: #888; }
.reader-light .top-btn { color: var(--accent); }
.reader-light .reader-hint { color: rgba(0,0,0,0.2); }
.top-btn { color: var(--accent); cursor: pointer; }

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
.ch-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.ch-btn:disabled { opacity: 0.25; cursor: default; }
.bottom-page { color: #777; font-size: 13px; }

/* 提示 */
.reader-hint {
  position: absolute; bottom: 56px; left: 50%; transform: translateX(-50%);
  color: rgba(255,255,255,0.3); font-size: 13px; pointer-events: none;
  text-align: center; white-space: nowrap;
}
.hint-mobile { display: none; }

/* ============================================ */
/*  响应式适配 - 手机端 (< 768px)               */
/* ============================================ */
@media (max-width: 767px) {
  .hint-desktop { display: none; }
  .hint-mobile  { display: inline; }

  /* 顶部栏紧凑 */
  .reader-top {
    padding: 0 12px; height: 40px; font-size: 12px;
  }

  /* 漫画图片：填满屏幕宽度 */
  .reader-img {
    max-width: 100vw;
    max-height: calc(100vh - 80px);
  }

  /* 底部栏 - 更大触摸目标 */
  .reader-bottom {
    padding: 0 8px; height: 48px;
  }
  .ch-btn {
    padding: 8px 10px; font-size: 11px; max-width: 100px; min-height: 32px;
  }
  .bottom-page {
    font-size: 11px;
  }

  /* 小说文本 - 更窄的边距 */
  .novel-text {
    max-width: 100%; padding: 20px 16px 80px;
  }
  .novel-toolbar {
    padding: 6px 12px; flex-wrap: wrap; gap: 6px;
  }
  .toolbar-chapter {
    font-size: 12px; flex-basis: 100%;
  }
  .toolbar-actions {
    gap: 4px;
  }

  /* 小说底部栏 */
  .novel-bottom {
    padding: 0 8px; height: 48px;
  }

  /* 翻页提示下移 */
  .reader-hint {
    bottom: 60px; font-size: 11px;
  }

  /* 错误和加载状态 */
  .reader-loading, .reader-empty {
    font-size: 14px;
  }
}

/* ============================================ */
/*  平板适配 (768px - 1024px)                    */
/* ============================================ */
@media (min-width: 768px) and (max-width: 1024px) {
  .novel-text {
    max-width: 90%; padding: 28px 20px 80px;
  }
  .ch-btn {
    max-width: 180px;
  }
}

/* ============================================ */
/*  大屏优化 (> 1400px)                          */
/* ============================================ */
@media (min-width: 1400px) {
  .novel-text {
    max-width: 800px;
  }
}
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

/* ====== 滚动模式 ====== */
.top-right { display: flex; align-items: center; gap: 12px; }
.mode-toggle {
  background: none; border: 1px solid rgba(255,255,255,0.25); color: #bbb;
  padding: 2px 10px; border-radius: 4px; cursor: pointer; font-size: 12px;
  transition: all 0.2s;
}
.mode-toggle:hover { border-color: var(--accent); color: var(--accent); }
.page-indicator { white-space: nowrap; }

.scroll-viewport {
  flex: 1; overflow-y: auto; background: #000;
  display: flex; flex-direction: column; align-items: center;
  padding-bottom: 60px;
}
.scroll-page {
  width: 100%; display: flex; flex-direction: column; align-items: center;
  position: relative;
}
.scroll-img {
  width: 100%; max-width: 800px; display: block;
  min-height: 100px; background: #1a1a1a;
}
.scroll-page-num {
  position: absolute; bottom: 6px; right: 12px;
  background: rgba(0,0,0,0.55); color: rgba(255,255,255,0.5);
  font-size: 11px; padding: 2px 8px; border-radius: 4px;
  pointer-events: none;
}
.scroll-next-chapter {
  width: 100%; max-width: 800px; padding: 16px;
  text-align: center; color: var(--accent); cursor: pointer;
  font-size: 15px; border-top: 1px solid rgba(255,255,255,0.08);
  transition: background 0.2s;
}
.scroll-next-chapter:hover { background: rgba(141,86,218,0.1); }

@media (max-width: 767px) {
  .scroll-img { max-width: 100%; }
  .mode-toggle { font-size: 11px; padding: 1px 8px; }
  .page-indicator { font-size: 11px; }
}

/* ====== AI 摘要卡片 ====== */
.ai-summary-card {
  background: #fffbe6; border: 1px solid #ffe58f;
  border-radius: 8px; padding: 10px 14px; margin: 8px 16px;
}
.dark .ai-summary-card { background: #2a2a1e; border-color: #5a4a10; }
.ai-summary-text { font-size: 13px; color: #555; line-height: 1.7; }
.dark .ai-summary-text { color: #ccc; }
</style>
