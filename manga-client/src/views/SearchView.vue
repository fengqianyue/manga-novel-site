<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const results = ref([])
const loading = ref(false)

async function doSearch() {
  keyword.value = route.query.q || ''
  if (!keyword.value) { results.value = []; return }
  loading.value = true
  try {
    const res = await request.get('/search', { params: { keyword: keyword.value }, silent: true })
    results.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function goBack() { if (window.history.length > 1) router.back(); else router.push('/') }

// 首次加载 + query 变化都触发
doSearch()
watch(() => route.query.q, () => doSearch())
</script>

<template>
  <div class="search-page">
    <div class="search-bar">
      <a href="javascript:;" class="back-link" @click="goBack">← 返回</a>
      <router-link to="/" class="home-link">首页</router-link>
      <el-input v-model="keyword" placeholder="搜索..." size="large" class="search-input"
        @keyup.enter="router.push('/search?q=' + encodeURIComponent(keyword))">
        <template #append>
          <el-button @click="router.push('/search?q=' + encodeURIComponent(keyword))">搜索</el-button>
        </template>
      </el-input>
    </div>

    <div class="search-results">
      <p v-if="!loading && keyword" class="result-count">找到 {{ results.length }} 个结果</p>

      <div v-if="loading" class="loading">搜索中...</div>

      <div v-else-if="results.length === 0 && keyword" class="empty">未找到相关作品</div>

      <div v-else class="result-grid">
        <div v-for="w in results" :key="w.id" class="card-item"
          @click="router.push('/work/' + w.id)">
          <div class="card-cover">
            <div v-if="w.coverUrl" class="cover-img" :style="{ backgroundImage: 'url(/uploads/' + w.coverUrl + ')' }"></div>
            <div v-else class="cover-placeholder"><span>{{ w.title }}</span></div>
          </div>
          <div class="card-info">
            <p class="card-title">{{ w.title }}</p>
            <p class="card-author">{{ w.author }}</p>
            <el-tag size="small">{{ w.type === 'manga' ? '漫画' : '小说' }}</el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-page { min-height: 100vh; background: #f0f3f7; }
.search-bar {
  background: #fff; padding: 16px 32px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05);
}
.back-link { color: #8d56da; text-decoration: none; font-size: 14px; white-space: nowrap; }
.home-link { color: #888; text-decoration: none; font-size: 13px; margin-left: 4px; white-space: nowrap; }
.home-link:hover { color: #8d56da; }
.search-input { max-width: 500px; }
.search-results { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
.result-count { color: #999; margin-bottom: 20px; }
.loading, .empty { text-align: center; color: #999; padding: 60px 0; }
.result-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 20px; }
@media (max-width: 1024px) { .result-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 480px) { .result-grid { grid-template-columns: repeat(2, 1fr); } }
.card-item { cursor: pointer; background: #fff; border-radius: 8px; overflow: hidden; transition: transform 0.2s; }
.card-item:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.card-cover { aspect-ratio: 3/4; }
.cover-img { width: 100%; height: 100%; background-size: cover; background-position: center; }
.cover-placeholder { width: 100%; height: 100%; background: linear-gradient(135deg, #e8edf2, #d5dde5); display: flex; align-items: center; justify-content: center; padding: 16px; }
.cover-placeholder span { color: #999; font-size: 13px; text-align: center; }
.card-info { padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.card-title { font-size: 14px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-author { font-size: 12px; color: #999; }
</style>
