<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const route = useRoute()
const tab = ref(route.query.tab || 'manga')
const subTab = ref('click')
const works = ref([])
const loading = ref(false)

const subTabs = [
  { label: '点击榜', value: 'click' },
  { label: '新书榜', value: 'new' },
]

function switchTab(t) {
  tab.value = t
  router.replace({ query: { tab: t } })
}

async function loadRanking() {
  loading.value = true
  try {
    const res = await request.get('/work/ranking', {
      params: { type: tab.value, recent: subTab.value === 'new' },
      silent: true,
    })
    works.value = res.data || []
  } catch { /* */ }
  finally { loading.value = false }
}
onMounted(loadRanking)
watch([tab, subTab], loadRanking)

const medal = (i) => i < 3 ? ['🥇','🥈','🥉'][i] : ''
</script>

<template>
  <div class="rank-page">
    <div class="rank-bar">
      <a href="javascript:;" class="bar-back" @click="router.back()">← 返回</a>
      <router-link to="/" class="bar-home">首页</router-link>
      <h2>🏆 排行榜</h2>
    </div>
    <!-- 漫画/小说 Tab -->
    <div class="rank-tabs">
      <span :class="{ on: tab === 'manga' }" @click="switchTab('manga')">漫画排行</span>
      <span :class="{ on: tab === 'novel' }" @click="switchTab('novel')">小说排行</span>
    </div>
    <!-- 点击榜/新书榜 子Tab -->
    <div class="sub-tabs">
      <span v-for="st in subTabs" :key="st.value"
        :class="{ on: subTab === st.value }" @click="subTab = st.value">{{ st.label }}</span>
    </div>

    <div class="rank-body" v-loading="loading">
      <div v-if="works.length === 0" class="empty">暂无数据</div>
      <div v-else class="rank-list">
        <div v-for="(w, idx) in works" :key="w.id" class="rank-item" @click="router.push('/work/' + w.id)">
          <span class="rank-num">{{ medal(idx) || (idx + 1) }}</span>
          <div class="rank-cover">
            <img v-if="w.coverUrl" :src="'/uploads/' + w.coverUrl" />
            <div v-else class="cover-dummy">📚</div>
          </div>
          <div class="rank-info">
            <p class="rank-title">{{ w.title }}</p>
            <p class="rank-meta">{{ w.author }} · {{ w.publishYear || '--' }}</p>
          </div>
          <span class="rank-views">👁 {{ w.viewCount || 0 }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rank-page { min-height: 100vh; background: #f0f3f7; }
.rank-bar { background: #fff; padding: 16px 32px; display: flex; align-items: baseline; gap: 10px; box-shadow: 0 1px 6px rgba(0,0,0,0.05); }
.bar-back { color: #8d56da; text-decoration: none; font-size: 14px; }
.bar-home { color: #888; text-decoration: none; font-size: 13px; margin-left: 4px; }
.bar-home:hover { color: #8d56da; }
.rank-bar h2 { font-size: 18px; color: #333; }

.rank-tabs { display: flex; gap: 0; max-width: 700px; margin: 20px auto 0; padding: 0 20px; }
.rank-tabs span { flex: 1; text-align: center; padding: 10px; cursor: pointer; font-size: 14px; color: #888; border-bottom: 2px solid transparent; transition: all 0.2s; }
.rank-tabs span.on { color: #8d56da; border-bottom-color: #8d56da; font-weight: 600; }

.sub-tabs { display: flex; gap: 12px; max-width: 700px; margin: 0 auto; padding: 12px 20px 0; }
.sub-tabs span { padding: 4px 16px; border-radius: 14px; font-size: 12px; cursor: pointer; color: #888; background: #fff; border: 1px solid #e0e0e0; transition: all 0.2s; }
.sub-tabs span.on { background: #8d56da; color: #fff; border-color: #8d56da; }

.rank-body { max-width: 700px; margin: 0 auto; padding: 16px 20px 40px; }
.empty { text-align: center; color: #999; padding: 60px 0; }
.rank-list { display: flex; flex-direction: column; gap: 4px; }
.rank-item { display: flex; align-items: center; gap: 14px; background: #fff; padding: 14px 18px; border-radius: 10px; cursor: pointer; transition: transform 0.2s; }
.rank-item:hover { transform: translateX(6px); background: #faf8ff; }
.rank-num { width: 32px; text-align: center; font-size: 14px; font-weight: 700; color: #999; flex-shrink: 0; }
.rank-cover { width: 44px; height: 60px; border-radius: 4px; overflow: hidden; flex-shrink: 0; }
.rank-cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-dummy { width: 100%; height: 100%; background: linear-gradient(135deg, #e8edf2, #d5dde5); display: flex; align-items: center; justify-content: center; font-size: 20px; }
.rank-info { flex: 1; min-width: 0; }
.rank-title { font-size: 15px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-meta { font-size: 12px; color: #aaa; margin-top: 3px; }
.rank-views { font-size: 12px; color: #bbb; flex-shrink: 0; }
</style>
