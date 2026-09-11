<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/api/request'
import { getMyWorks, createMyWork, updateMyWork, submitMyWork, withdrawMyWork, offlineMyWork } from '@/api/author'
import { getTags, updateWorkTags, getChapters, addChapter, deleteChapter } from '@/api/admin'
import { useGoBack } from '@/composables/useGoBack'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const { goBack } = useGoBack()

// ==================== 作品列表 ====================
const works = ref([])
const loading = ref(false)
const statusFilter = ref(null)
const total = ref(0)
const page = ref(1)
const pageSize = 10

const statusMeta = {
  0: { text: '草稿', type: 'info' },
  1: { text: '已上架', type: 'success' },
  2: { text: '待审核', type: 'warning' },
  3: { text: '已驳回', type: 'danger' },
}

async function loadWorks() {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize }
    if (statusFilter.value !== null) params.status = statusFilter.value
    const res = await getMyWorks(params)
    works.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { /* silent */ }
  finally { loading.value = false }
}

function switchFilter(s) {
  statusFilter.value = s
  page.value = 1
  loadWorks()
}

// ==================== 作品编辑弹窗 ====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const workForm = ref({ id: null, title: '', author: '', type: 'manga', summary: '', publishYear: null, completed: 0, coverUrl: '' })
const tags = ref([])
const selectedTagIds = ref([])
const saving = ref(false)

const upHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage.getItem('admin_token') || localStorage.getItem('token') || ''),
}))

function openCreate() {
  isEdit.value = false
  workForm.value = { id: null, title: '', author: userStore.user?.username || '', type: 'manga', summary: '', publishYear: null, completed: 0, coverUrl: '' }
  selectedTagIds.value = []
  dialogVisible.value = true
}

async function openEdit(w) {
  isEdit.value = true
  workForm.value = { id: w.id, title: w.title, author: w.author, type: w.type, summary: w.summary || '', publishYear: w.publishYear, completed: w.completed, coverUrl: w.coverUrl || '' }
  try {
    const [tagRes] = await Promise.all([getTags()])
    tags.value = tagRes.data || []
    const cur = await request.get(`/work/${w.id}/tags`)
    selectedTagIds.value = (cur.data || []).map(t => t.id)
  } catch { /* silent */ }
  dialogVisible.value = true
}

function onCoverUploaded(res) {
  if (res.code === 200) workForm.value.coverUrl = res.data.url
  else ElMessage.error(res.message || '封面上传失败')
}

async function saveWork() {
  if (!workForm.value.title.trim()) return ElMessage.warning('请填写作品标题')
  if (!workForm.value.author.trim()) return ElMessage.warning('请填写笔名')
  saving.value = true
  try {
    let workId = workForm.value.id
    if (isEdit.value) {
      await updateMyWork(workForm.value)
    } else {
      const res = await createMyWork(workForm.value)
      workId = res.data
    }
    if (workId) {
      await updateWorkTags(workId, selectedTagIds.value).catch(() => {})
    }
    ElMessage.success(isEdit.value ? '已保存' : '草稿已创建，可继续添加章节')
    dialogVisible.value = false
    loadWorks()
  } catch (e) { /* 拦截器已提示 */ }
  finally { saving.value = false }
}

// ==================== 状态机操作 ====================
async function doSubmit(w) {
  await ElMessageBox.confirm(`确认提交《${w.title}》进入审核？提交后管理员可见`, '提交审核', { type: 'info' })
  await submitMyWork(w.id)
  ElMessage.success('已提交审核')
  loadWorks()
}

async function doWithdraw(w) {
  await ElMessageBox.confirm(`确认撤回《${w.title}》？撤回后可继续编辑`, '撤回审核', { type: 'warning' })
  await withdrawMyWork(w.id)
  ElMessage.success('已撤回为草稿')
  loadWorks()
}

async function doOffline(w) {
  await ElMessageBox.confirm(`确认下架《${w.title}》？下架后读者将无法阅读`, '下架作品', { type: 'warning' })
  await offlineMyWork(w.id)
  ElMessage.success('已下架')
  loadWorks()
}

// ==================== 章节管理 ====================
const chapterDialog = ref(false)
const chapterWork = ref(null)
const chapters = ref([])
const chapterForm = ref({ title: '', chapterNum: 1 })
const uploadedPages = ref([])
const novelText = ref('')
const chapterSaving = ref(false)

async function openChapters(w) {
  chapterWork.value = w
  chapters.value = []
  chapterForm.value = { title: '', chapterNum: (chapters.value.length + 1) }
  uploadedPages.value = []
  novelText.value = ''
  chapterDialog.value = true
  loadChapters(w.id)
}

async function loadChapters(workId) {
  try {
    const res = await getChapters(workId)
    chapters.value = res.data || []
  } catch { /* silent */ }
}

async function doAddChapter() {
  if (!chapterForm.value.title.trim()) return ElMessage.warning('请填写章节标题')
  chapterSaving.value = true
  try {
    if (chapterWork.value.type === 'manga' && uploadedPages.value.length === 0) {
      return ElMessage.warning('漫画章节请先上传页面图片')
    }
    const res = await addChapter({
      workId: chapterWork.value.id,
      title: chapterForm.value.title.trim(),
      chapterNum: Number(chapterForm.value.chapterNum) || chapters.value.length + 1,
    })
    const chapterId = res.data
    if (chapterWork.value.type === 'manga') {
      await request.post('/manga-page/batch', { chapterId, imageUrls: uploadedPages.value })
    } else {
      if (novelText.value.trim()) {
        await request.post('/novel-content/save', { chapterId, textContent: novelText.value })
      }
    }
    ElMessage.success('章节已创建')
    chapterForm.value = { title: '', chapterNum: chapters.value.length + 2 }
    uploadedPages.value = []
    novelText.value = ''
    loadChapters(chapterWork.value.id)
  } catch (e) { /* 拦截器已提示 */ }
  finally { chapterSaving.value = false }
}

function onPageUploaded(res) {
  if (res.code === 200) uploadedPages.value.push(res.data.url)
  else ElMessage.error(res.message || '图片上传失败')
}

function removePage(i) {
  uploadedPages.value.splice(i, 1)
}

async function doDeleteChapter(ch) {
  await ElMessageBox.confirm(`确认删除《${ch.title}》？章节内容将一并删除`, '删除章节', { type: 'warning' })
  await deleteChapter(ch.id)
  ElMessage.success('已删除')
  loadChapters(chapterWork.value.id)
}

function onTxtImported(res) {
  if (res.code === 200) {
    ElMessage.success(`导入成功，共 ${res.data.chapters} 章`)
    loadChapters(chapterWork.value.id)
  } else {
    ElMessage.error(res.message || 'TXT 导入失败')
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  loadWorks()
  try {
    const tagRes = await getTags()
    tags.value = tagRes.data || []
  } catch { /* silent */ }
})
</script>

<template>
  <div class="author-root">
    <header class="author-top">
      <a href="javascript:;" class="top-back" @click="goBack">← 返回</a>
      <h2>✍ 作者中心</h2>
      <span class="top-hint">发布作品 → 管理员审核通过后即可公开</span>
      <div class="top-user">
        <el-tag v-if="userStore.user?.role === 2" size="small" type="danger">管理员</el-tag>
        <span class="uname">{{ userStore.user?.username }}</span>
      </div>
    </header>

    <main class="author-main">
      <!-- 筛选与新建 -->
      <div class="filter-row">
        <el-radio-group v-model="statusFilter" @change="switchFilter">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="0">草稿</el-radio-button>
          <el-radio-button :value="2">待审核</el-radio-button>
          <el-radio-button :value="1">已上架</el-radio-button>
          <el-radio-button :value="3">已驳回</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建作品</el-button>
      </div>

      <!-- 我的作品表格 -->
      <el-table :data="works" v-loading="loading" stripe class="works-table">
        <el-table-column label="封面" width="70">
          <template #default="{ row }">
            <el-image v-if="row.coverUrl" :src="'/uploads/' + row.coverUrl" fit="cover"
              style="width:44px;height:58px;border-radius:4px" :preview-src-list="['/uploads/' + row.coverUrl]" preview-teleported />
            <div v-else class="cover-dummy">📚</div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="author" label="笔名" width="110" />
        <el-table-column label="类型" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'manga' ? '' : 'success'">{{ row.type === 'manga' ? '漫画' : '小说' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.status === 3 && row.rejectReason" :content="'驳回理由：' + row.rejectReason" placement="top">
              <el-tag size="small" type="danger">已驳回 ⚠</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="statusMeta[row.status]?.type || 'info'">{{ statusMeta[row.status]?.text || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.status === 1 || row.status === 2"
              @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="openChapters(row)">章节管理</el-button>
            <el-button v-if="row.status === 0 || row.status === 3" size="small" type="warning" plain
              @click="doSubmit(row)">提交审核</el-button>
            <el-button v-if="row.status === 2" size="small" type="info" plain
              @click="doWithdraw(row)">撤回</el-button>
            <el-button v-if="row.status === 1" size="small" type="danger" plain
              @click="doOffline(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="page-row" v-if="total > pageSize">
        <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
          v-model:current-page="page" @current-change="loadWorks" />
      </div>
      <div v-if="!loading && works.length === 0" class="empty-state">
        <p>还没有发布作品</p>
        <el-button type="primary" @click="openCreate">创建第一部作品</el-button>
      </div>
    </main>

    <!-- 作品编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑作品' : '新建作品草稿'" width="560px">
      <el-form label-width="80px">
        <el-form-item label="标题"><el-input v-model="workForm.title" maxlength="50" placeholder="作品标题" /></el-form-item>
        <el-form-item label="笔名"><el-input v-model="workForm.author" maxlength="30" placeholder="对外展示的作者名" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="workForm.type" :disabled="isEdit">
            <el-radio value="manga">漫画</el-radio>
            <el-radio value="novel">小说</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="workForm.summary" type="textarea" :rows="3" maxlength="500" placeholder="作品简介（审核时管理员可见）" />
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-row">
            <el-image v-if="workForm.coverUrl" :src="'/uploads/' + workForm.coverUrl" fit="cover" class="cover-preview" />
            <el-upload :action="'/file/upload?dir=covers'" :headers="upHeaders" :show-file-list="false"
              :on-success="onCoverUploaded" accept="image/*" name="file">
              <el-button size="small">{{ workForm.coverUrl ? '更换封面' : '上传封面' }}</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="selectedTagIds" multiple collapse-tags placeholder="选择标签（利于推荐）" style="width:100%">
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="workForm.publishYear" :min="1900" :max="2100" placeholder="出版年份" />
        </el-form-item>
        <el-form-item label="完结">
          <el-switch v-model="workForm.completed" :active-value="1" :inactive-value="0" active-text="已完结" inactive-text="连载中" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveWork">{{ isEdit ? '保存' : '创建草稿' }}</el-button>
      </template>
    </el-dialog>

    <!-- 章节管理弹窗 -->
    <el-dialog v-model="chapterDialog" :title="`章节管理 - ${chapterWork?.title || ''}`" width="760px" top="6vh">
      <div class="chapter-layout">
        <!-- 新增章节 -->
        <div class="chapter-add">
          <h4>新增章节</h4>
          <div class="ch-form-row">
            <el-input v-model="chapterForm.title" placeholder="章节标题（如：第1话 / 第一章 xxx）" style="flex:1" />
            <el-input-number v-model="chapterForm.chapterNum" :min="0.1" :step="0.5" placeholder="章节号" style="width:110px" />
          </div>

          <template v-if="chapterWork?.type === 'manga'">
            <el-upload drag multiple :action="`/file/upload?dir=manga/${chapterWork.id}`"
              :headers="upHeaders" :on-success="onPageUploaded" :show-file-list="false"
              accept="image/*" name="file" class="page-upload">
              <div class="upload-hint">📥 拖拽或点击上传漫画页图片<br /><span>按上传顺序生成页码，第一张建议为章节封面</span></div>
            </el-upload>
            <div class="page-thumbs" v-if="uploadedPages.length">
              <div v-for="(u, i) in uploadedPages" :key="i" class="thumb-item">
                <el-image :src="'/uploads/' + u" fit="cover" class="thumb-img" />
                <span class="thumb-num">{{ i + 1 }}</span>
                <span class="thumb-del" @click="removePage(i)">✕</span>
              </div>
            </div>
          </template>

          <template v-else>
            <el-input v-model="novelText" type="textarea" :rows="7" placeholder="粘贴章节正文，或使用下方 TXT 导入自动分章" />
            <el-upload :action="`/novel-content/import?workId=${chapterWork?.id}`" :headers="upHeaders"
              :show-file-list="false" :on-success="onTxtImported" accept=".txt" name="file" style="margin-top:8px">
              <el-button size="small" plain>📁 TXT 整本导入（自动分章）</el-button>
            </el-upload>
          </template>

          <el-button type="primary" style="margin-top:12px;width:100%" :loading="chapterSaving"
            @click="doAddChapter">创建章节</el-button>
        </div>

        <!-- 章节列表 -->
        <div class="chapter-list">
          <h4>章节列表（{{ chapters.length }}）</h4>
          <div class="ch-scroll">
            <div v-for="ch in chapters" :key="ch.id" class="ch-item">
              <span class="ch-num">{{ ch.chapterNum }}</span>
              <span class="ch-title">{{ ch.title }}</span>
              <span class="ch-del" @click="doDeleteChapter(ch)">删除</span>
            </div>
            <p v-if="chapters.length === 0" class="ch-empty">暂无章节</p>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.author-root { min-height: 100vh; background: #f3f4f6; }
.author-top { background: #fff; padding: 0 28px; height: 56px; display: flex; align-items: center; gap: 16px;
  border-bottom: 1px solid #e8e8e8; position: sticky; top: 0; z-index: 20; }
.top-back { color: var(--accent); text-decoration: none; font-size: 13px; }
.author-top h2 { font-size: 17px; color: #222; font-weight: 600; margin: 0; }
.top-hint { font-size: 12px; color: #999; }
.top-user { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.uname { font-size: 13px; color: #555; }

.author-main { max-width: 1100px; margin: 0 auto; padding: 24px 28px 60px; }
.filter-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.works-table { background: #fff; border-radius: 8px; }
.cover-dummy { width: 44px; height: 58px; background: #f0f0f0; border-radius: 4px;
  display: flex; align-items: center; justify-content: center; font-size: 18px; }
.page-row { display: flex; justify-content: center; margin-top: 20px; }
.empty-state { text-align: center; color: #aaa; padding: 80px 0; }
.empty-state p { margin-bottom: 16px; }

.cover-row { display: flex; align-items: center; gap: 12px; }
.cover-preview { width: 72px; height: 96px; border-radius: 6px; }

.chapter-layout { display: flex; gap: 20px; }
.chapter-add { flex: 1.4; }
.chapter-list { flex: 1; }
.chapter-add h4, .chapter-list h4 { margin: 0 0 10px; color: #333; font-size: 14px; }
.ch-form-row { display: flex; gap: 8px; margin-bottom: 10px; }
.page-upload { margin-bottom: 8px; }
.upload-hint { padding: 14px; font-size: 13px; color: #666; line-height: 1.8; }
.upload-hint span { font-size: 11px; color: #aaa; }
.page-thumbs { display: flex; flex-wrap: wrap; gap: 6px; max-height: 150px; overflow-y: auto; }
.thumb-item { position: relative; width: 52px; height: 70px; }
.thumb-img { width: 52px; height: 70px; border-radius: 3px; }
.thumb-num { position: absolute; left: 2px; top: 2px; background: rgba(0,0,0,.6); color: #fff;
  font-size: 10px; padding: 0 4px; border-radius: 2px; }
.thumb-del { position: absolute; right: 2px; top: 2px; background: rgba(0,0,0,.6); color: #fff;
  font-size: 10px; padding: 0 3px; border-radius: 2px; cursor: pointer; }
.ch-scroll { max-height: 420px; overflow-y: auto; }
.ch-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 6px;
  background: #f7f7f8; margin-bottom: 6px; font-size: 13px; }
.ch-num { color: var(--accent); font-weight: 600; min-width: 32px; }
.ch-title { flex: 1; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ch-del { color: #d50707; font-size: 12px; cursor: pointer; }
.ch-empty { color: #bbb; font-size: 13px; text-align: center; padding: 30px 0; }

@media (max-width: 767px) {
  .author-top { padding: 10px 14px; flex-wrap: wrap; height: auto; gap: 8px; }
  .top-hint { display: none; }
  .author-main { padding: 16px 12px 40px; }
  .filter-row { flex-direction: column; gap: 10px; align-items: stretch; }
  .chapter-layout { flex-direction: column; }
}
</style>
