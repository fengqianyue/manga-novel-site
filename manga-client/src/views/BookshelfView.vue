<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Upload, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const works = ref([])
const favWorks = ref([])
function goBack() { if (window.history.length > 1) router.back(); else router.push('/') }
const loading = ref(true)

// 导入弹窗
const showImport = ref(false)
const importType = ref('novel')
const importTitle = ref('')
const importAuthor = ref('')
const importing = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn) { router.push('/'); return }
  try {
    const [myRes, favRes] = await Promise.all([
      request.get('/user-work/list'),
      request.get('/favorite/list'),
    ])
    works.value = myRes.data || []
    // 获取收藏作品详情
    const ids = favRes.data || []
    if (ids.length > 0) {
      const details = await Promise.all(ids.map(id => request.get('/work/' + id, { silent: true })))
      favWorks.value = details.map(r => r.data).filter(Boolean)
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
})

async function doImport(file) {
  importing.value = true
  const form = new FormData()
  form.append('file', file.raw)
  form.append('title', importTitle.value)
  form.append('author', importAuthor.value || '未知')
  const url = importType.value === 'novel' ? '/user-work/import-novel' : '/user-work/import-manga'
  try {
    const res = await request.post(url, form, {
      headers: { 'Content-Type': 'multipart/form-data' }, silent: true,
    })
    ElMessage.success(`导入成功，${res.data.chapters || res.data.pages} 个内容单元`)
    showImport.value = false
    // 刷新
    const myRes = await request.get('/user-work/list')
    works.value = myRes.data || []
  } catch { /* ignore */ }
  finally { importing.value = false }
}

const folderInput = ref(null)

async function removeMyWork(work) {
  await ElMessageBox.confirm(`确认删除「${work.title}」？`, '警告', { type: 'warning' })
  await request.delete('/user-work/' + work.id, { silent: true })
  ElMessage.success('已删除')
  works.value = works.value.filter(w => w.id !== work.id)
}
async function removeFavorite(work) {
  await request.delete('/favorite/' + work.id, { silent: true })
  ElMessage.success('已取消收藏')
  favWorks.value = favWorks.value.filter(w => w.id !== work.id)
}

function openImport(type) {
  importType.value = type
  importTitle.value = ''
  importAuthor.value = ''
  showImport.value = true
}

async function onFolderPicked(e) {
  const files = Array.from(e.target.files).filter(f => f.type.startsWith('image/'))
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))
  if (files.length === 0) { ElMessage.warning('文件夹中无图片'); return }
  importing.value = true
  const form = new FormData()
  files.forEach(f => form.append('files', f))
  form.append('title', importTitle.value || '')
  form.append('author', importAuthor.value || '')
  try {
    const res = await request.post('/user-work/import-manga', form, {
      headers: { 'Content-Type': 'multipart/form-data' }, silent: true,
    })
    ElMessage.success(`导入成功，${res.data.pages} 张图片`)
    showImport.value = false
    const myRes = await request.get('/user-work/list')
    works.value = myRes.data || []
  } catch { /* ignore */ }
  finally { importing.value = false; e.target.value = '' }
}
</script>

<template>
  <div class="bookshelf">
    <div class="shelf-bar">
      <a href="javascript:;" class="bar-back" @click="goBack">← 返回</a>
      <router-link to="/" class="bar-home">首页</router-link>
      <h2>我的书架</h2>
      <div class="bar-actions">
        <el-dropdown @command="openImport">
          <el-button type="primary" :icon="Plus" size="small">导入本地作品</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="novel"><el-icon><Upload /></el-icon> 导入 TXT 小说</el-dropdown-item>
              <el-dropdown-item command="manga"><el-icon><Upload /></el-icon> 导入漫画图片</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="shelf-content" v-loading="loading">
      <!-- 我的导入 -->
      <section class="shelf-section">
        <h3>📥 本地导入</h3>
        <div v-if="works.length === 0" class="empty-tip">还没有导入作品，点击"导入本地作品"开始</div>
        <div class="shelf-grid" v-else>
          <div v-for="w in works" :key="'my'+w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <el-icon class="card-remove" @click.stop="removeMyWork(w)"><Delete /></el-icon>
            <div class="card-cover">
              <div v-if="w.coverUrl" :style="{ backgroundImage: 'url(/uploads/' + w.coverUrl + ')' }" class="cover-img"></div>
              <div v-else class="cover-placeholder"><span>{{ w.title }}</span></div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
              <el-tag size="small">{{ w.type === 'manga' ? '漫画' : '小说' }}</el-tag>
            </div>
          </div>
        </div>
      </section>

      <!-- 收藏 -->
      <section class="shelf-section">
        <h3>❤ 我的收藏</h3>
        <div v-if="favWorks.length === 0" class="empty-tip">还没有收藏作品</div>
        <div class="shelf-grid" v-else>
          <div v-for="w in favWorks" :key="'fav'+w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <el-icon class="card-remove" @click.stop="removeFavorite(w)"><Delete /></el-icon>
            <div class="card-cover">
              <div v-if="w.coverUrl" :style="{ backgroundImage: 'url(/uploads/' + w.coverUrl + ')' }" class="cover-img"></div>
              <div v-else class="cover-placeholder"><span>{{ w.title }}</span></div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
              <el-tag size="small">{{ w.type === 'manga' ? '漫画' : '小说' }}</el-tag>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 导入弹窗 -->
    <el-dialog v-model="showImport" :title="importType === 'novel' ? '导入 TXT 小说' : '导入漫画图片'" width="440px">
      <el-form label-position="top">
        <el-form-item label="作品标题"><el-input v-model="importTitle" placeholder="选填，默认使用文件名" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="importAuthor" placeholder="选填" /></el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            :action="'/api/user-work/import-' + importType"
            :headers="{ Authorization: 'Bearer ' + userStore.token }"
            :data="{ title: importTitle, author: importAuthor }"
            :show-file-list="true"
            :on-success="doImport"
            :accept="importType === 'novel' ? '.txt' : 'image/*'"
            :multiple="importType === 'manga'"
            drag
          >
            <el-icon class="el-icon--upload"><Plus /></el-icon>
            <div class="el-upload__text">拖拽文件或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">
                {{ importType === 'novel' ? '支持 .txt 文件' : '支持 JPG/PNG，可多选，第一张自动作封面' }}
              </div>
            </template>
          </el-upload>
          <div v-if="importType === 'manga'" style="margin-top:8px">
            <el-button size="small" @click="folderInput?.click()">📁 选择文件夹</el-button>
            <span style="font-size:12px;color:#999;margin-left:8px">导入整个文件夹</span>
          </div>
        </el-form-item>
      </el-form>
    </el-dialog>
    <input ref="folderInput" type="file" webkitdirectory directory multiple accept="image/*" style="display:none" @change="onFolderPicked" />
  </div>
</template>

<style scoped>
.bookshelf { min-height: 100vh; background: #f0f3f7; }
.shelf-bar {
  background: #fff; padding: 16px 32px; display: flex; align-items: center; gap: 20px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 10;
}
.bar-back { color: #8d56da; text-decoration: none; font-size: 14px; }
.bar-home { color: #888; text-decoration: none; font-size: 13px; margin-left: 4px; }
.bar-home:hover { color: #8d56da; }
.shelf-bar h2 { font-size: 18px; color: #333; flex: 1; }
.shelf-content { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
.shelf-section { margin-bottom: 44px; }
.shelf-section h3 { font-size: 18px; color: #333; margin-bottom: 20px; }
.shelf-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 20px; }
@media (max-width: 1024px) { .shelf-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 480px) { .shelf-grid { grid-template-columns: repeat(2, 1fr); } }
.card-item { cursor: pointer; background: #fff; border-radius: 8px; overflow: hidden; transition: transform 0.2s; }
.card-item:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.card-cover { aspect-ratio: 3/4; }
.cover-img { width: 100%; height: 100%; background-size: cover; background-position: center; }
.cover-placeholder { width: 100%; height: 100%; background: linear-gradient(135deg, #e8edf2, #d5dde5); display: flex; align-items: center; justify-content: center; padding: 16px; }
.cover-placeholder span { color: #999; font-size: 13px; text-align: center; }
.card-info { padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.card-title { font-size: 14px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-author { font-size: 12px; color: #999; }
.empty-tip { color: #999; text-align: center; padding: 48px 0; }
.card-remove {
  position: absolute; top: 6px; right: 6px;
  background: rgba(0,0,0,0.5); color: #fff; border-radius: 50%;
  padding: 4px; cursor: pointer; z-index: 5; font-size: 16px;
}
.card-remove:hover { background: #d50707; }
.card-item { position: relative; }
</style>
