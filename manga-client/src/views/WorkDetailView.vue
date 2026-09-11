<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { StarFilled, View } from '@element-plus/icons-vue'
import { getWorkDetail, getWorkTags } from '@/api/work'
import { getChapters } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import request from '@/api/request'
import { useGoBack } from '@/composables/useGoBack'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const work = ref(null)
const tags = ref([])
const chapters = ref([])
const recommends = ref([])
const comments = ref([])
const commentText = ref('')
const commentLoading = ref(false)
const isFav = ref(false)
const progress = ref(null)
const favLoading = ref(false)

const { goBack } = useGoBack()

const coverUrl = computed(() => {
  if (work.value?.coverUrl) return '/uploads/' + work.value.coverUrl
  return null
})

async function loadWorkData(wid) {
  work.value = null; chapters.value = []; tags.value = []; recommends.value = []
  try {
    const [workRes, tagRes, chRes] = await Promise.all([
      getWorkDetail(wid),
      getWorkTags(wid),
      getChapters(wid),
    ])
    work.value = workRes.data
    tags.value = tagRes.data
    chapters.value = chRes.data
  } catch {
    router.push('/')
    return
  }

  try {
    const recRes = await request.get('/work/recommend/' + wid, { silent: true })
    recommends.value = (recRes.data || []).map(r => ({
      id: Number(r.id), title: r.title, author: r.author || '',
      cover_url: r.cover_url, type: r.type
    }))
  } catch { /* */ }

  loadComments()

  if (userStore.isLoggedIn) {
    try { const favRes = await request.get(`/favorite/check/${wid}`); isFav.value = favRes.data } catch { /* */ }
    try {
      const progRes = await request.get(`/reading-progress/${wid}`)
      if (progRes.data) {
        const chRes = await request.get(`/chapter/${progRes.data.chapterId}`, { silent: true })
        progress.value = { ...progRes.data, chapterNum: chRes.data?.chapterNum, chapterTitle: chRes.data?.title }
      }
    } catch { /* 忽略 */ }
  }
}

// 首次加载 + 路由参数变化时重新加载
onMounted(() => loadWorkData(Number(route.params.id)))
watch(() => route.params.id, (newId) => { if (newId) loadWorkData(Number(newId)) })

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  favLoading.value = true
  try {
    if (isFav.value) {
      await request.delete(`/favorite/${route.params.id}`)
      isFav.value = false
      ElMessage.success('已取消收藏')
    } else {
      await request.post('/favorite', { workId: Number(route.params.id) })
      isFav.value = true
      ElMessage.success('已收藏')
    }
  } catch { /* 忽略 */ }
  finally { favLoading.value = false }
}

async function loadComments() {
  try { const res = await request.get('/comment/list/' + route.params.id, { silent: true }); comments.value = res.data || [] } catch { /* */ }
}
async function addComment() {
  if (!commentText.value.trim()) { ElMessage.warning('请输入评论内容'); return }
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  commentLoading.value = true
  try {
    await request.post('/comment', { workId: Number(route.params.id), content: commentText.value }, { silent: true })
    commentText.value = ''
    ElMessage.success('评论成功')
    loadComments()
  } catch { /* */ }
  finally { commentLoading.value = false }
}
async function delComment(c) {
  try { await request.delete('/comment/' + c.id, { silent: true }); ElMessage.success('已删除'); loadComments() } catch { /* */ }
}

function startReading(chapterId, pageNum) {
  if (pageNum) {
    router.push(`/reader/${route.params.id}/${chapterId}/${pageNum}`)
  } else {
    router.push(`/reader/${route.params.id}/${chapterId}`)
  }
}
</script>

<template>
  <div class="detail-page" v-if="work">
    <!-- 顶部导航条 -->
    <div class="detail-bar">
      <a href="javascript:;" class="bar-back" @click="goBack">← 返回</a>
      <router-link to="/" class="bar-home">首页</router-link>
      <span class="bar-title">{{ work.title }}</span>
    </div>

    <!-- 主体内容 -->
    <div class="detail-container">
      <!-- 左侧封面 -->
      <div class="detail-cover">
        <div v-if="coverUrl" class="cover-img" :style="{ backgroundImage: 'url(' + coverUrl + ')' }"></div>
        <div v-else class="cover-placeholder">
          <span>{{ work.title }}</span>
        </div>
      </div>

      <!-- 右侧信息 -->
      <div class="detail-info">
        <h1 class="info-title">{{ work.title }}</h1>
        <p class="info-author">
          作者：{{ work.author }}
          <span v-if="work.publishYear" class="info-year" @click="router.push('/category?publishYear=' + work.publishYear)">{{ work.publishYear }}</span>
          <span v-if="work.completed" class="info-completed" @click="router.push('/category?completed=1')">已完结</span>
          <span v-else class="info-ongoing" @click="router.push('/category?completed=0')">连载中</span>
        </p>
        <p class="info-type">
          <el-tag size="small" :type="work.type === 'manga' ? '' : 'success'" class="type-tag" @click="router.push('/category?type=' + work.type)">
            {{ work.type === 'manga' ? '漫画' : '小说' }}
          </el-tag>
        </p>

        <!-- 标签 -->
        <div class="info-tags" v-if="tags.length">
          <el-tag v-for="t in tags" :key="t.id" class="tag-chip" size="small" effect="plain" @click="router.push('/category?tagId=' + t.id)">{{ t.name }}</el-tag>
        </div>

        <!-- 简介 -->
        <div class="info-summary" v-if="work.summary">
          <h3>简介</h3>
          <p>{{ work.summary }}</p>
        </div>

        <!-- 操作按钮 -->
        <div class="info-actions">
          <el-button
            :type="isFav ? 'warning' : 'default'"
            :icon="StarFilled"
            :loading="favLoading"
            @click="toggleFavorite"
          >
            {{ isFav ? '已收藏' : '收藏' }}
          </el-button>
          <el-button
            v-if="progress"
            type="primary"
            :icon="View"
            @click="startReading(progress.chapterId, progress.pageNum)"
          >
            继续阅读 {{ progress.chapterTitle || '第' + progress.chapterNum + '话' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 章节列表 -->
    <div class="chapter-section">
      <h3>章节列表（共 {{ chapters.length }} 话）</h3>
      <div class="chapter-list">
        <div
          v-for="ch in chapters"
          :key="ch.id"
          class="chapter-item"
          @click="startReading(ch.id)"
        >
          <span class="ch-num">第{{ ch.chapterNum }}话</span>
          <span class="ch-title">{{ ch.title }}</span>
          <span class="ch-arrow">→</span>
        </div>
      </div>
    </div>

    <!-- 推荐 -->
    <div v-if="recommends.length > 0" class="recommend-section">
      <div class="rec-head">
        <h3>✨ 猜你喜欢</h3>
        <span class="rec-sub">基于标签智能推荐</span>
      </div>
      <div class="rec-grid">
        <router-link v-for="r in recommends" :key="r.id" :to="'/work/' + r.id" class="rec-card">
          <div class="rec-cover">
            <img v-if="r.cover_url" :src="'/uploads/' + r.cover_url" />
            <div v-else class="rec-cover-dummy">📚</div>
          </div>
          <div class="rec-info">
            <p class="rec-title">{{ r.title }}</p>
            <p class="rec-author">{{ r.author || '未知' }}</p>
            <span class="rec-tag">{{ r.type === 'manga' ? '漫画' : '小说' }}</span>
          </div>
        </router-link>
      </div>
    </div>

    <!-- 评论区 -->
    <div class="comment-section">
      <h3>💬 评论（{{ comments.length }}）</h3>
      <div class="comment-input">
        <el-input v-model="commentText" type="textarea" :rows="3" placeholder="写下你的评论..." maxlength="500" show-word-limit />
        <el-button type="primary" size="small" :loading="commentLoading" @click="addComment" style="margin-top:8px">发表评论</el-button>
      </div>
      <div v-if="comments.length > 0" class="comment-list">
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-avatar">
            <img v-if="c.avatarUrl" :src="'/uploads/' + c.avatarUrl" />
            <div v-else class="cmt-avatar-dummy"></div>
          </div>
          <div class="comment-body">
            <div class="comment-head">
              <span class="comment-user">{{ c.username }}</span>
              <span class="comment-time">{{ (c.createdAt || '').substring(0, 10) }}</span>
            </div>
            <p class="comment-content">{{ c.content }}</p>
          </div>
          <el-button v-if="userStore.user && userStore.user.id === c.userId" size="small" type="danger" text @click="delComment(c)">删除</el-button>
        </div>
      </div>
    </div>
  </div>

  <!-- 加载中 -->
  <div v-else class="detail-loading">
    <p>加载中...</p>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* 导航条 */
.detail-bar {
  background: #fff;
  padding: 16px 32px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05);
  position: sticky;
  top: 0;
  z-index: 10;
}
.bar-back { color: var(--accent); text-decoration: none; font-size: 14px; }
.bar-home { color: #888; text-decoration: none; font-size: 13px; margin-left: 4px; }
.bar-home:hover { color: var(--accent); }
.bar-title { font-size: 16px; font-weight: 600; color: #333; }

/* 主容器 */
.detail-container {
  max-width: 1000px;
  margin: 40px auto 0;
  padding: 0 32px;
  display: flex;
  gap: 40px;
}

/* 封面 */
.detail-cover {
  width: 260px;
  flex-shrink: 0;
}
.cover-img {
  width: 100%;
  aspect-ratio: 3 / 4;
  background-size: cover;
  background-position: center;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
}
.cover-placeholder {
  width: 100%;
  aspect-ratio: 3 / 4;
  background: linear-gradient(135deg, #e8edf2, #d5dde5);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.cover-placeholder span {
  color: #999;
  font-size: 14px;
  text-align: center;
}

/* 信息 */
.detail-info { flex: 1; }
.info-title { font-size: 26px; font-weight: 700; color: #222; margin-bottom: 12px; }
.info-author { font-size: 15px; color: #666; margin-bottom: 12px; }
.info-type { margin-bottom: 14px; }
.info-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 20px; }
.tag-chip { cursor: pointer; }
.tag-chip:hover { opacity: 0.8; }
.info-year { display: inline-block; margin-left: 8px; padding: 1px 10px; background: #ede4f7; color: var(--accent); border-radius: 10px; font-size: 12px; cursor: pointer; }
.info-year:hover { background: #dcccf2; }
.info-completed { display: inline-block; margin-left: 6px; padding: 1px 10px; background: #e8f5e9; color: #4caf50; border-radius: 10px; font-size: 12px; cursor: pointer; }
.info-completed:hover { background: #c8e6c9; }
.info-ongoing { display: inline-block; margin-left: 6px; padding: 1px 10px; background: #fff3e0; color: #ff9800; border-radius: 10px; font-size: 12px; cursor: pointer; }
.info-ongoing:hover { background: #ffe0b2; }
.type-tag { cursor: pointer; }
.type-tag:hover { opacity: 0.8; }
.info-summary { margin-bottom: 24px; }
.info-summary h3 { font-size: 15px; color: #555; margin-bottom: 8px; }
.info-summary p { font-size: 14px; color: #777; line-height: 1.8; }
.info-actions { display: flex; gap: 14px; }

/* 章节列表 */
.chapter-section {
  max-width: 1000px;
  margin: 48px auto 60px;
  padding: 0 32px;
}
.chapter-section h3 { font-size: 18px; color: #333; margin-bottom: 20px; }
.chapter-list { display: flex; flex-direction: column; gap: 2px; }
.chapter-item {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.chapter-item:hover { background: #e9e0f5; }
.ch-num { font-size: 15px; font-weight: 600; color: var(--accent); width: 80px; }
.ch-title { flex: 1; font-size: 14px; color: #555; }
.ch-arrow { color: #ccc; font-size: 14px; }

/* 加载 */
.detail-loading {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

/* 推荐 */
.recommend-section { max-width: 1000px; margin: 0 auto 60px; padding: 0 32px; }
.rec-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px; }
.rec-head h3 { font-size: 18px; color: #333; }
.rec-sub { font-size: 12px; color: #bbb; }
.rec-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 14px; }
.rec-card { display: block; background: #fff; border-radius: 10px; overflow: hidden; cursor: pointer; text-decoration: none; color: inherit; transition: transform 0.2s, box-shadow 0.2s; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.rec-card:hover { transform: translateY(-4px); box-shadow: 0 8px 20px rgba(0,0,0,0.08); }
.rec-cover { aspect-ratio: 3/4; overflow: hidden; pointer-events: none; }
.rec-cover img { width: 100%; height: 100%; object-fit: cover; }
.rec-cover-dummy { width: 100%; height: 100%; background: linear-gradient(135deg, #f5f0fa, #e8e0f0); display: flex; align-items: center; justify-content: center; font-size: 28px; }
.rec-info { padding: 10px 12px 14px; pointer-events: none; }
.rec-title { font-size: 13px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 2px; }
.rec-author { font-size: 11px; color: #aaa; margin-bottom: 6px; }
.rec-tag { font-size: 10px; background: #ede4f7; color: var(--accent); padding: 1px 6px; border-radius: 8px; }

.comment-section { max-width: 1000px; margin: 0 auto 60px; padding: 0 32px; }
.comment-section h3 { font-size: 18px; color: #333; margin-bottom: 18px; }
.comment-input { margin-bottom: 20px; }
.comment-list { display: flex; flex-direction: column; gap: 2px; }
.comment-item { display: flex; gap: 12px; padding: 14px; background: #fff; border-radius: 10px; align-items: flex-start; }
.comment-avatar { width: 36px; height: 36px; border-radius: 50%; overflow: hidden; flex-shrink: 0; }
.comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
.cmt-avatar-dummy { width: 100%; height: 100%; background: #e8e8e8; border-radius: 50%; }
.comment-body { flex: 1; min-width: 0; }
.comment-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.comment-user { font-size: 13px; font-weight: 600; color: var(--accent); }
.comment-time { font-size: 11px; color: #ccc; }
.comment-content { font-size: 14px; color: #444; line-height: 1.7; word-break: break-all; }

@media (max-width: 768px) {
  .rec-grid { grid-template-columns: repeat(3, 1fr); }
  .detail-container { flex-direction: column; align-items: center; }
  .detail-cover { width: 200px; }
  .detail-info { text-align: center; }
  .info-tags { justify-content: center; }
  .info-actions { justify-content: center; }
  .info-title { font-size: 22px; }
  .chapter-section, .recommend-section, .comment-section { padding: 0 16px; }
  .chapter-section { margin: 32px auto 40px; }
  .comment-item { align-items: center; }
}

@media (max-width: 480px) {
  .rec-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .detail-cover { width: 160px; }
  .info-title { font-size: 20px; }
  .info-author { font-size: 13px; }
  .detail-bar { padding: 10px 12px; flex-wrap: wrap; }
  .bar-title { font-size: 13px; }
  .ch-num { width: 50px; font-size: 13px; }
  .chapter-item { padding: 10px 14px; }
  .ch-title { font-size: 13px; }
  .detail-container { padding: 20px 12px; margin-top: 0; }
  .rec-card { border-radius: 8px; }
}

/* 大屏优化推荐网格 */
@media (min-width: 1200px) {
  .rec-grid { grid-template-columns: repeat(6, 1fr); }
}
</style>
