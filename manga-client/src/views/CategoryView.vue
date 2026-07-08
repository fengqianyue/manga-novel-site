<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const works = ref([])
const tags = ref([])
const loading = ref(true)
const page = ref(1)
const total = ref(0)
const pageSize = 24

const filters = ref({
  type: route.query.type || '',
  keyword: '',
  publishYear: route.query.publishYear ? Number(route.query.publishYear) : null,
  completed: route.query.completed ? Number(route.query.completed) : null,
  tagId: route.query.tagId ? Number(route.query.tagId) : null,
})
const yearOptions = []
for (let y = new Date().getFullYear(); y >= 2000; y--) yearOptions.push(y)

onMounted(async () => {
  try { const res = await request.get('/tag/list'); tags.value = res.data || [] } catch { /* */ }
  loadWorks()
})

async function loadWorks() {
  loading.value = true
  const p = { pageNum: page.value, pageSize }
  if (filters.value.type) p.type = filters.value.type
  if (filters.value.keyword) p.keyword = filters.value.keyword
  if (filters.value.publishYear) p.publishYear = filters.value.publishYear
  if (filters.value.completed !== null && filters.value.completed !== '') p.completed = filters.value.completed
  try {
    const res = await request.get('/work/filter', { params: p, silent: true })
    works.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { /* */ }
  finally { loading.value = false }
}

watch([() => filters.value.type, () => filters.value.completed, () => filters.value.publishYear],
  () => { page.value = 1; loadWorks() })

function goBack() { if (window.history.length > 1) router.back(); else router.push('/') }
function doSearch() { page.value = 1; loadWorks() }
function clearFilters() {
  filters.value = { type: '', keyword: '', publishYear: null, completed: null, tagId: null }
  page.value = 1; loadWorks()
}
</script>

<template>
  <div class="cat-root">
    <!-- 顶部 -->
    <header class="cat-top">
      <a href="javascript:;" class="top-back" @click="goBack">← 返回</a>
      <router-link to="/" class="top-home">首页</router-link>
      <h2>作品分类</h2>
      <div class="top-search">
        <el-input v-model="filters.keyword" placeholder="搜索作品、作者..."
          size="default" clearable @keyup.enter="doSearch" @clear="doSearch">
          <template #prefix><span style="color:#8d56da">🔍</span></template>
        </el-input>
      </div>
    </header>

    <div class="cat-body">
      <!-- 侧边栏 fixed -->
      <aside class="cat-aside">
        <div class="aside-card">
          <div class="aside-title">📂 作品类型</div>
          <div class="aside-opts">
            <span :class="{ on: !filters.type }" @click="filters.type = ''">全部</span>
            <span :class="{ on: filters.type === 'manga' }" @click="filters.type = 'manga'">漫画</span>
            <span :class="{ on: filters.type === 'novel' }" @click="filters.type = 'novel'">小说</span>
          </div>
        </div>

        <div class="aside-card">
          <div class="aside-title">📖 连载状态</div>
          <div class="aside-opts">
            <span :class="{ on: filters.completed === null }" @click="filters.completed = null">全部</span>
            <span :class="{ on: filters.completed === 0 }" @click="filters.completed = 0">连载中</span>
            <span :class="{ on: filters.completed === 1 }" @click="filters.completed = 1">已完结</span>
          </div>
        </div>

        <div class="aside-card">
          <div class="aside-title">📅 年份</div>
          <el-select v-model="filters.publishYear" placeholder="不限" size="small" clearable style="width:100%">
            <el-option v-for="y in yearOptions" :key="y" :label="String(y)" :value="y" />
          </el-select>
        </div>

        <div class="aside-card" v-if="tags.length">
          <div class="aside-title">🏷 标签</div>
          <div class="tag-cloud">
            <span v-for="t in tags" :key="t.id"
              :class="{ on: filters.tagId === t.id }"
              @click="filters.tagId = filters.tagId === t.id ? null : t.id">{{ t.name }}</span>
          </div>
        </div>

        <span class="aside-clear" @click="clearFilters">✕ 清除全部筛选</span>
      </aside>

      <!-- 右侧内容区 可滚动 -->
      <main class="cat-main" v-loading="loading">
        <p class="result-hint" v-if="!loading">共 {{ total }} 部 · 第 {{ page }} / {{ Math.ceil(total / pageSize) || 1 }} 页</p>
        <div v-if="!loading && works.length === 0" class="empty-state">😕 没有匹配的作品</div>
        <div v-else class="work-grid">
          <div v-for="w in works" :key="w.id" class="work-card" @click="router.push('/work/' + w.id)">
            <div class="card-cover">
              <div v-if="w.coverUrl" class="cover-real" :style="{ backgroundImage: 'url(/uploads/' + w.coverUrl + ')' }"></div>
              <div v-else class="cover-dummy">
                <span class="dummy-icon">📚</span>
              </div>
              <span class="badge-type">{{ w.type === 'manga' ? '漫' : '文' }}</span>
              <span v-if="w.completed" class="badge-done">✓</span>
            </div>
            <div class="card-text">
              <p class="card-name">{{ w.title }}</p>
              <p class="card-meta">{{ w.author }}<span v-if="w.publishYear"> · {{ w.publishYear }}</span></p>
            </div>
          </div>
        </div>
        <div class="page-row" v-if="total > pageSize">
          <el-pagination background layout="prev, pager, next"
            :total="total" :page-size="pageSize" v-model:current-page="page"
            @current-change="loadWorks" />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
/* ====== 整体 ====== */
.cat-root { min-height: 100vh; background: #f3f4f6; }

/* ====== 顶栏 ====== */
.cat-top {
  background: #fff; padding: 0 32px; height: 56px;
  display: flex; align-items: center; gap: 20px;
  border-bottom: 1px solid #e8e8e8; position: sticky; top: 0; z-index: 20;
}
.top-back { color: #8d56da; text-decoration: none; font-size: 13px; white-space: nowrap; }
.top-home { color: #888; text-decoration: none; font-size: 12px; margin-left: 4px; white-space: nowrap; }
.top-home:hover { color: #8d56da; }
.cat-top h2 { font-size: 17px; color: #222; font-weight: 600; margin: 0; }
.top-search { width: 280px; margin-left: auto; }

/* ====== 主体 ====== */
.cat-body { display: flex; max-width: 1300px; margin: 0 auto; padding-left: 20px; }

/* ====== 侧边栏（fixed） ====== */
.cat-aside {
  width: 200px; flex-shrink: 0; padding: 28px 0 40px 0;
  position: sticky; top: 56px; height: calc(100vh - 56px);
  overflow-y: auto; border-right: 1px solid #e8e8e8; padding-right: 20px;
}
.aside-card { margin-bottom: 24px; }
.aside-title { font-size: 13px; color: #888; font-weight: 600; margin-bottom: 10px; letter-spacing: 1px; }
.aside-opts { display: flex; flex-wrap: wrap; gap: 6px; }
.aside-opts span {
  padding: 5px 14px; border-radius: 16px; font-size: 12px; cursor: pointer;
  color: #666; background: #fff; border: 1px solid #e0e0e0;
  transition: all 0.2s;
}
.aside-opts span:hover { color: #8d56da; border-color: #8d56da; }
.aside-opts span.on { background: #8d56da; color: #fff; border-color: #8d56da; }

.tag-cloud { display: flex; flex-wrap: wrap; gap: 5px; }
.tag-cloud span {
  padding: 3px 10px; border-radius: 12px; font-size: 11px; cursor: pointer;
  color: #888; background: #f5f5f5; transition: all 0.2s;
}
.tag-cloud span:hover { color: #8d56da; }
.tag-cloud span.on { background: #ede4f7; color: #8d56da; font-weight: 600; }

.aside-clear { font-size: 11px; color: #bbb; cursor: pointer; }
.aside-clear:hover { color: #d50707; }

/* ====== 主内容 ====== */
.cat-main { flex: 1; padding: 24px 32px 60px 28px; min-height: 600px; }
.result-hint { color: #aaa; font-size: 12px; margin-bottom: 20px; }

/* 卡片网格 */
.work-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
@media (min-width: 1500px) { .work-grid { grid-template-columns: repeat(5, 1fr); } }
@media (max-width: 1100px) { .work-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 760px)  { .work-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; } }

.work-card {
  background: #fff; border-radius: 10px; overflow: hidden; cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s; position: relative;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.work-card:hover { transform: translateY(-6px); box-shadow: 0 12px 28px rgba(0,0,0,0.1); }

.card-cover { aspect-ratio: 3/4; position: relative; background: #f0f0f0; }
.cover-real { width: 100%; height: 100%; background-size: cover; background-position: center; }
.cover-dummy { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #f5f0fa, #e8e0f0); }
.dummy-icon { font-size: 36px; opacity: 0.5; }

.badge-type {
  position: absolute; top: 8px; left: 8px; background: #8d56da; color: #fff;
  font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600;
}
.badge-done {
  position: absolute; top: 8px; right: 8px; background: #67c23a; color: #fff;
  font-size: 12px; width: 20px; height: 20px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; font-weight: 700;
}

.card-text { padding: 12px 14px 14px; }
.card-name { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 4px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-meta { font-size: 12px; color: #aaa; }

.empty-state { text-align: center; color: #bbb; padding: 80px 0; font-size: 16px; }
.page-row { display: flex; justify-content: center; margin-top: 40px; }
</style>
