<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Document, Tickets, PriceTag, Lock, Search, User, Menu, Upload } from '@element-plus/icons-vue'
import { getWorkList, getWorkTags } from '@/api/work'
import { adminLogin, addWork, updateWork, toggleWorkStatus, auditWork, setUserRole, updateWorkTags } from '@/api/admin'
import { getTags, addTag, deleteTag, importManga, importNovel } from '@/api/admin'
import { getChapters, addChapter, deleteChapter } from '@/api/admin'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// ========== 上传 ==========
const uploadHeaders = { Authorization: 'Bearer ' + (userStore.adminToken || '') }

function beforeCoverUpload(file) {
  const isValid = file.type.startsWith('image/')
  if (!isValid) ElMessage.error('仅支持图片文件')
  return isValid
}
function onCoverSuccess(res) {
  workForm.value.coverUrl = res.data.url
  ElMessage.success('封面上传成功')
}

const folderInput = ref(null)
const folderUploading = ref(false)

function beforePageUpload(file) {
  return file.type.startsWith('image/')
}

function selectFolder() {
  folderInput.value?.click()
}

async function onFolderSelected(e) {
  const files = Array.from(e.target.files)
    .filter(f => f.type.startsWith('image/'))
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }))
  if (files.length === 0) { ElMessage.warning('文件夹中无图片文件'); return }
  folderUploading.value = true
  let done = 0
  for (const file of files) {
    const form = new FormData()
    form.append('file', file)
    try {
      const res = await request.post(`/file/upload?dir=manga/${selectedWork.value?.id || 0}`, form, {
        headers: { 'Content-Type': 'multipart/form-data', Authorization: uploadHeaders.Authorization },
        silent: true,
      })
      newChapterForm.value.uploadedUrls.push(res.data.url)
      done++
    } catch { /* 跳过失败的 */ }
  }
  folderUploading.value = false
  ElMessage.success(`已上传 ${done}/${files.length} 张图片`)
  // 重置 input 以便可以重复选择同一文件夹
  e.target.value = ''
}

// ========== 管理员认证 ==========
const loginForm = ref({ username: '', password: '' })
const loginLoading = ref(false)

onMounted(() => {
  if (userStore.isAdminAuth) {
    loadAll()
  }
})

async function doAdminLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loginLoading.value = true
  try {
    const res = await adminLogin(loginForm.value)
    userStore.setAdminLogin(res.data)
    ElMessage.success('管理员登录成功')
    loadAll()
  } catch {
    // 错误已在拦截器处理
  } finally {
    loginLoading.value = false
  }
}

function doAdminLogout() {
  userStore.adminLogout()
}

// ========== 菜单 ==========
const activeMenu = ref('works')
// 移动端侧边栏开关
const showSidebar = ref(false)

// ====== 数据大屏 ======
const dashData = ref(null)
async function loadDashboard() {
  try { const res = await request.get('/admin/stats/dashboard', { silent: true }); dashData.value = res.data } catch { /* */ }
}

// ========== 数据 ==========
const works = ref([])
const allTags = ref([])
const users = ref([])

// 作品筛选
const workStatusFilter = ref(null)  // null=全部, 1=上架, 0=下架
const workSearch = ref('')

// 章节编辑
const editingChapter = ref(null)
const editChapterForm = ref({ title: '', chapterNum: 0 })
const editChapterUploaded = ref([])
const showChapterEdit = ref(false)

// 漫画页管理
const viewingChapter = ref(null)
const chapterPages = ref([])
const showPagesDialog = ref(false)
const workDialogVisible = ref(false)
const isEdit = ref(false)
const workForm = ref({ title: '', author: '', type: 'manga', summary: '', status: 1 })
const selectedTagIds = ref([])

const selectedWork = ref(null)
const chapters = ref([])
const chapterForm = ref({ title: '', chapterNum: 0 })

const newTagName = ref('')

async function loadAll() {
  try {
    const params = { pageSize: 100 }
    if (workStatusFilter.value !== null) params.status = workStatusFilter.value
    if (workSearch.value) params.keyword = workSearch.value
    const res = await request.get('/work/admin-list', { params, silent: true })
    works.value = res.data.records
    const tagRes = await getTags()
    allTags.value = tagRes.data
  } catch { /* API 失败时静默，避免 admin 面板崩溃 */ }
}

function onStatusFilter(val) { workStatusFilter.value = val; loadAll() }
function onSearch() { loadAll() }

// ========== 删除作品 ==========
async function deleteWork(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '警告', { type: 'warning' })
  await request.delete(`/work/${row.id}`, { silent: true })
  ElMessage.success('已删除')
  loadAll()
}

// ========== 章节编辑 ==========
function openEditChapter(row) {
  editingChapter.value = row
  editChapterForm.value = { title: row.title, chapterNum: row.chapterNum }
  editChapterUploaded.value = []
  showChapterEdit.value = true
}

function onEditFileSuccess(res) {
  editChapterUploaded.value.push(res.data.url)
}

function removeEditFile(index) {
  editChapterUploaded.value.splice(index, 1)
}

async function saveChapterEdit() {
  await request.put('/chapter', {
    id: editingChapter.value.id,
    workId: editingChapter.value.workId,
    title: editChapterForm.value.title,
    chapterNum: editChapterForm.value.chapterNum,
  }, { silent: true })
  if (editChapterUploaded.value.length > 0) {
    await request.post('/manga-page/batch', {
      chapterId: editingChapter.value.id,
      imageUrls: editChapterUploaded.value,
    }, { silent: true })
  }
  ElMessage.success(editChapterUploaded.value.length > 0
    ? `章节已更新，新增 ${editChapterUploaded.value.length} 张图片`
    : '章节已更新')
  showChapterEdit.value = false
  selectWork(selectedWork.value)
}

// ========== 漫画页管理 ==========
async function openPagesDialog(chapter) {
  viewingChapter.value = chapter
  const res = await request.get(`/manga-page/list/${chapter.id}`, { silent: true })
  chapterPages.value = res.data
  showPagesDialog.value = true
}
async function deletePage(page) {
  await request.delete(`/manga-page/${page.id}`, { silent: true })
  ElMessage.success('已删除')
  // 刷新
  const res = await request.get(`/manga-page/list/${viewingChapter.value.id}`, { silent: true })
  chapterPages.value = res.data
}

// ========== TXT 导入 ==========
const showTxtImport = ref(false)
const txtUploading = ref(false)

function openTxtImport() { showTxtImport.value = true }
async function onTxtSuccess(res) {
  ElMessage.success(`导入完成！自动创建了 ${res.data.chapters} 个章节`)
  showTxtImport.value = false
  selectWork(selectedWork.value)
}

// ========== 作品整部导入（ZIP/CBZ · TXT/EPUB） ==========
const showQuickImport = ref(false)
const quickImporting = ref(false)
const quickFile = ref(null)
const quickForm = reactive({
  kind: 'manga',   // manga=漫画ZIP/CBZ  novel-txt=小说TXT  novel-epub=小说EPUB
  title: '', author: '', summary: '', publishYear: null, completed: 1,
})

const quickAccept = computed(() =>
  quickForm.kind === 'manga' ? '.zip,.cbz' : quickForm.kind === 'novel-txt' ? '.txt' : '.epub')

function openQuickImport() {
  quickFile.value = null
  Object.assign(quickForm, { kind: 'manga', title: '', author: '', summary: '', publishYear: null, completed: 1 })
  showQuickImport.value = true
}

function onQuickFileChange(uploadFile) {
  quickFile.value = uploadFile.raw || null
  // 未填标题时用文件名预填
  if (!quickForm.title && uploadFile.name) {
    quickForm.title = uploadFile.name.replace(/\.(zip|cbz|txt|epub)$/i, '')
  }
}

function removeQuickFile() { quickFile.value = null }

async function doQuickImport() {
  if (!quickFile.value) { ElMessage.warning('请选择要导入的文件'); return }
  if (!quickForm.title.trim()) { ElMessage.warning('请填写作品标题'); return }
  quickImporting.value = true
  try {
    const form = new FormData()
    form.append('file', quickFile.value)
    form.append('title', quickForm.title.trim())
    if (quickForm.author) form.append('author', quickForm.author.trim())
    if (quickForm.summary) form.append('summary', quickForm.summary.trim())
    if (quickForm.publishYear) form.append('publishYear', quickForm.publishYear)
    form.append('completed', quickForm.completed)
    const isManga = quickForm.kind === 'manga'
    const res = isManga ? await importManga(form) : await importNovel(form)
    const d = res.data
    ElMessage.success(isManga
      ? `导入成功：${d.chapters} 个章节 / ${d.pages} 页图片`
      : `导入成功：${d.chapters} 个章节 / 约 ${(d.chars / 10000).toFixed(1)} 万字`)
    showQuickImport.value = false
    loadAll()
  } catch {
    // 拦截器已提示错误
  } finally {
    quickImporting.value = false
  }
}

// ========== 用户管理 ==========
async function loadUsers() {
  const res = await request.get('/user/list', { silent: true })
  users.value = res.data
}
async function toggleUser(row) {
  const action = row.isDeleted ? '启用' : '禁用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`)
  await request.put(`/user/${row.id}/toggle`, null, { silent: true })
  ElMessage.success(`已${action}`)
  loadUsers()
}

async function toggleAuthorRole(row) {
  const becoming = row.role !== 1
  await ElMessageBox.confirm(
    becoming ? `确认授予「${row.username}」作者身份？作者可在作者中心发布作品（需管理员审核后公开）`
             : `确认撤销「${row.username}」的作者身份？其已发布的作品不受影响`,
    becoming ? '设为作者' : '取消作者', { type: 'warning' })
  await setUserRole(row.id, becoming ? 1 : 0)
  ElMessage.success(becoming ? '已授予作者身份' : '已撤销作者身份')
  loadUsers()
}

// ========== 作品 CRUD ==========
async function openAddWork() {
  isEdit.value = false
  workForm.value = { title: '', author: '', type: 'manga', summary: '', status: 1, publishYear: null, completed: 0 }
  selectedTagIds.value = []
  workDialogVisible.value = true
}
async function openEditWork(row) {
  isEdit.value = true
  workForm.value = { id: row.id, title: row.title, author: row.author, type: row.type, summary: row.summary, status: row.status, publishYear: row.publishYear, completed: row.completed }
  try {
    const res = await getWorkTags(row.id)
    selectedTagIds.value = res.data.map(t => t.id)
  } catch { selectedTagIds.value = [] }
  workDialogVisible.value = true
}
async function saveWork() {
  if (isEdit.value) {
    await updateWork(workForm.value)
    await updateWorkTags(workForm.value.id, selectedTagIds.value)
    ElMessage.success('修改成功')
  } else {
    const res = await addWork(workForm.value)
    await updateWorkTags(res.data, selectedTagIds.value)
    ElMessage.success('新增成功')
  }
  workDialogVisible.value = false
  loadAll()
}
async function toggleWork(row) {
  await ElMessageBox.confirm(`确认${row.status === 1 ? '下架' : '上架'}「${row.title}」？`)
  await toggleWorkStatus(row.id, row.status === 1 ? 0 : 1)
  loadAll()
}

// ========== 作品审核（作者提交的待审核作品） ==========
async function auditWorkPass(row) {
  await ElMessageBox.confirm(`确认通过「${row.title}」的审核？通过后立即公开展示`, '审核通过', { type: 'success' })
  await auditWork(row.id, true)
  ElMessage.success('已通过并上架')
  loadAll()
}

async function auditWorkReject(row) {
  const { value } = await ElMessageBox.prompt('请填写驳回理由（作者可见）', '驳回作品', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：章节内容不完整 / 封面涉及版权问题',
    inputValidator: v => (v && v.trim().length >= 2) || '驳回理由至少 2 个字',
  })
  await auditWork(row.id, false, value.trim())
  ElMessage.success('已驳回')
  loadAll()
}

const pendingCount = computed(() => works.value.filter(w => w.status === 2).length)

// ========== 章节 ==========
const newChapterVisible = ref(false)
const newChapterForm = ref({ title: '', chapterNum: 0, uploadedUrls: [] })
const uploadingChapter = ref(false)

async function selectWork(w) {
  selectedWork.value = w
  const res = await getChapters(w.id)
  chapters.value = res.data
}

function openNewChapter() {
  newChapterForm.value = { title: '', chapterNum: (chapters.value.length || 0) + 1, uploadedUrls: [], textContent: '' }
  newChapterVisible.value = true
}

function onChapterFileSuccess(res) {
  newChapterForm.value.uploadedUrls.push(res.data.url)
}

function removeChapterFile(index) {
  newChapterForm.value.uploadedUrls.splice(index, 1)
}

async function saveNewChapter() {
  if (!newChapterForm.value.title) { ElMessage.warning('请输入章节标题'); return }
  uploadingChapter.value = true
  try {
    const chRes = await addChapter({
      workId: selectedWork.value.id,
      title: newChapterForm.value.title,
      chapterNum: newChapterForm.value.chapterNum,
    })
    // 如果有上传的图片，批量导入
    const newChId = chRes.data
    if (newChId && newChapterForm.value.textContent) {
      await request.post('/novel-content/save', {
        chapterId: newChId,
        textContent: newChapterForm.value.textContent,
      }, { silent: true })
    }
    if (newChId && newChapterForm.value.uploadedUrls.length > 0) {
      await request.post('/manga-page/batch', {
        chapterId: newChId,
        imageUrls: newChapterForm.value.uploadedUrls,
      }, { silent: true })
    }
    const imgCount = newChapterForm.value.uploadedUrls.length
    ElMessage.success(imgCount > 0 ? `章节已创建，共 ${imgCount} 张图片` : '章节已创建')
    newChapterVisible.value = false
    selectWork(selectedWork.value)
  } catch { /* 忽略 */ }
  finally { uploadingChapter.value = false }
}

async function delChapter(row) {
  await deleteChapter(row.id)
  ElMessage.success('已删除')
  selectWork(selectedWork.value)
}

// ========== 标签管理 ==========
async function doAddTag() {
  if (!newTagName.value.trim()) return
  await addTag(newTagName.value.trim())
  newTagName.value = ''
  ElMessage.success('标签已添加')
  const res = await getTags()
  allTags.value = res.data
}
async function doDeleteTag(tag) {
  await deleteTag(tag.id)
  ElMessage.success('已删除')
  const res = await getTags()
  allTags.value = res.data
}
</script>

<template>
  <div class="admin-layout">
    <!-- ====== 未登录：显示管理员登录表单 ====== -->
    <template v-if="!userStore.isAdminAuth">
      <div class="admin-login-page">
        <div class="admin-login-card">
          <div class="admin-login-icon"><el-icon :size="40" color="var(--accent)"><Lock /></el-icon></div>
          <h2>管理后台</h2>
          <el-form :model="loginForm" label-position="top" @keyup.enter="doAdminLogin">
            <el-form-item label="管理员账号">
              <el-input v-model="loginForm.username" placeholder="请输入管理员账号" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="loginLoading" class="btn-admin-login" @click="doAdminLogin">登 录</el-button>
          <p class="admin-login-tip">仅限管理员登录，普通用户无法进入</p>
        </div>
      </div>
    </template>

    <!-- ====== 已登录：正常后台 ====== -->
    <template v-else>
      <!-- 移动端汉堡菜单按钮 -->
      <div class="mobile-topbar">
        <el-button :icon="Menu" circle @click="showSidebar = !showSidebar" class="hamburger-btn" />
        <span class="mobile-title">管理后台</span>
      </div>
      <!-- 移动端遮罩层 -->
      <div v-if="showSidebar" class="sidebar-overlay" @click="showSidebar = false"></div>
      <aside class="admin-sidebar" :class="{ 'sidebar-open': showSidebar }">
        <h2 class="admin-logo">⚙ 管理后台</h2>
        <el-menu :default-active="activeMenu" @select="activeMenu = $event" background-color="#2a2a2a" text-color="#bbb" active-text-color="var(--accent)">
          <el-menu-item index="dashboard" @click="loadDashboard"><el-icon><Lock /></el-icon> 数据大屏</el-menu-item>
          <el-menu-item index="works"><el-icon><Document /></el-icon> 作品管理</el-menu-item>
          <el-menu-item index="chapters"><el-icon><Tickets /></el-icon> 章节管理</el-menu-item>
          <el-menu-item index="tags"><el-icon><PriceTag /></el-icon> 标签管理</el-menu-item>
          <el-menu-item index="users" @click="loadUsers"><el-icon><User /></el-icon> 用户管理</el-menu-item>
        </el-menu>
        <div class="sidebar-footer">
          <p class="sidebar-user">{{ userStore.adminUser?.username }}</p>
          <a href="javascript:;" @click="doAdminLogout">退出登录</a>
          <a href="/" style="margin-top:8px">← 返回首页</a>
        </div>
      </aside>

      <main class="admin-main">
        <!-- 数据大屏 -->
        <template v-if="activeMenu === 'dashboard'">
          <div class="section-header"><h3>📊 数据大屏</h3></div>
          <div v-if="dashData" class="dash-container">

            <!-- 概览卡片 -->
            <div class="dash-cards">
              <div class="dash-card"><span class="dc-num">{{ dashData.overview.totalUsers }}</span><span class="dc-label">用户总数</span></div>
              <div class="dash-card"><span class="dc-num">{{ dashData.overview.totalWorks }}</span><span class="dc-label">作品总数</span></div>
              <div class="dash-card"><span class="dc-num">{{ dashData.overview.activeWorks }}</span><span class="dc-label">上架作品</span></div>
              <div class="dash-card"><span class="dc-num">{{ dashData.overview.totalChapters }}</span><span class="dc-label">章节总数</span></div>
              <div class="dash-card"><span class="dc-num">{{ dashData.overview.totalComments }}</span><span class="dc-label">评论总数</span></div>
            </div>

            <div class="dash-charts">
              <!-- 类型分布 -->
              <div class="dash-panel">
                <h4>作品类型分布</h4>
                <div class="type-bars">
                  <div class="type-bar-row">
                    <span class="bar-label">漫画</span>
                    <div class="bar-track"><div class="bar-fill" :style="{ width: dashData.overview.totalWorks > 0 ? (dashData.typeDistribution.manga / dashData.overview.totalWorks * 100) + '%' : '0%' }"></div></div>
                    <span class="bar-val">{{ dashData.typeDistribution.manga }}</span>
                  </div>
                  <div class="type-bar-row">
                    <span class="bar-label">小说</span>
                    <div class="bar-track"><div class="bar-fill bar-novel" :style="{ width: dashData.overview.totalWorks > 0 ? (dashData.typeDistribution.novel / dashData.overview.totalWorks * 100) + '%' : '0%' }"></div></div>
                    <span class="bar-val">{{ dashData.typeDistribution.novel }}</span>
                  </div>
                </div>
              </div>

              <!-- 热门标签 -->
              <div class="dash-panel">
                <h4>热门标签 Top 10</h4>
                <div v-if="dashData.hotTags.length" class="tag-bars">
                  <div v-for="(t, i) in dashData.hotTags" :key="i" class="tag-bar-row">
                    <span class="bar-label" style="width:80px">{{ t.name }}</span>
                    <div class="bar-track"><div class="bar-fill bar-purple" :style="{ width: t.value / dashData.hotTags[0].value * 100 + '%' }"></div></div>
                    <span class="bar-val">{{ t.value }}</span>
                  </div>
                </div>
                <div v-else class="dash-empty">暂无数据</div>
              </div>
            </div>

            <!-- 近7日趋势 -->
            <div class="dash-panel dash-trend">
              <h4>近7日新增作品</h4>
              <div class="trend-bars">
                <div v-for="(d, i) in dashData.recentTrend" :key="i" class="trend-bar-item">
                  <div class="trend-bar" :style="{ height: d.count * 20 + 'px', minHeight: d.count > 0 ? '4px' : '1px' }"></div>
                  <span class="trend-label">{{ d.date }}</span>
                  <span class="trend-val">{{ d.count }}</span>
                </div>
              </div>
            </div>

          </div>
          <div v-else-if="activeMenu === 'dashboard'" class="dash-loading">加载中...</div>
        </template>

        <!-- 作品管理 -->
        <template v-if="activeMenu === 'works'">
          <div class="section-header"><h3>作品管理</h3></div>
          <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px">
            <el-radio-group v-model="workStatusFilter" size="small" @change="onStatusFilter">
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button :value="2">待审核<template v-if="pendingCount">({{ pendingCount }})</template></el-radio-button>
              <el-radio-button :value="1">上架</el-radio-button>
              <el-radio-button :value="0">下架</el-radio-button>
              <el-radio-button :value="3">已驳回</el-radio-button>
            </el-radio-group>
            <el-input v-model="workSearch" placeholder="搜索标题/作者" size="small" style="width:220px" clearable @clear="onSearch" @keyup.enter="onSearch">
              <template #suffix><el-icon @click="onSearch" style="cursor:pointer"><Search /></el-icon></template>
            </el-input>
            <el-button :icon="Upload" @click="openQuickImport">快速导入作品</el-button>
            <el-button type="primary" :icon="Plus" @click="openAddWork">新增作品</el-button>
          </div>
          <el-table :data="works" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="author" label="作者" width="140" />
            <el-table-column prop="type" label="类型" width="80">
              <template #default="{ row }">{{ row.type === 'manga' ? '漫画' : '小说' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="92">
              <template #default="{ row }">
                <el-tag v-if="row.status === 2" type="warning" size="small">待审核</el-tag>
                <el-tooltip v-else-if="row.status === 3 && row.rejectReason" :content="'驳回理由：' + row.rejectReason" placement="top">
                  <el-tag type="danger" size="small">已驳回</el-tag>
                </el-tooltip>
                <el-tag v-else-if="row.status === 3" type="danger" size="small">已驳回</el-tag>
                <el-tag v-else :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="290">
              <template #default="{ row }">
                <template v-if="row.status === 2">
                  <el-button size="small" type="success" @click="auditWorkPass(row)">通过</el-button>
                  <el-button size="small" type="warning" plain @click="auditWorkReject(row)">驳回</el-button>
                </template>
                <template v-else>
                  <el-button size="small" :icon="Edit" @click="openEditWork(row)">编辑</el-button>
                  <el-button size="small" @click="toggleWork(row)">{{ row.status === 1 ? '下架' : '上架' }}</el-button>
                </template>
                <el-button size="small" type="danger" :icon="Delete" @click="deleteWork(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 章节管理 -->
        <template v-if="activeMenu === 'chapters'">
          <div class="chapter-layout">
            <div class="chapter-work-list">
              <h4>选择作品</h4>
              <div v-for="w in works" :key="w.id" class="work-select-item" :class="{ active: selectedWork?.id === w.id }" @click="selectWork(w)">{{ w.title }}</div>
            </div>
            <div class="chapter-detail">
              <template v-if="selectedWork">
                <div class="section-header">
                  <h3>「{{ selectedWork.title }}」的章节</h3>
                  <div style="display:flex;gap:8px">
                    <el-button v-if="selectedWork.type === 'novel'" size="small" @click="openTxtImport">📄 导入 TXT</el-button>
                    <el-button type="primary" :icon="Plus" @click="openNewChapter">新增章节</el-button>
                  </div>
                </div>
                <el-table :data="chapters" stripe>
                  <el-table-column prop="chapterNum" label="序号" width="70" />
                  <el-table-column prop="title" label="标题" />
                  <el-table-column label="操作" width="210">
                    <template #default="{ row }">
                      <el-button size="small" :icon="Edit" @click="openEditChapter(row)">编辑</el-button>
                      <el-button size="small" @click="openPagesDialog(row)">页面</el-button>
                      <el-button size="small" type="danger" :icon="Delete" @click="delChapter(row)" />
                    </template>
                  </el-table-column>
                </el-table>
              </template>
              <div v-else class="empty-tip">← 请先选择一部作品</div>
            </div>
          </div>
        </template>

        <!-- 标签管理 -->
        <template v-if="activeMenu === 'tags'">
          <div class="section-header"><h3>标签管理</h3></div>
          <div class="tag-create">
            <el-input v-model="newTagName" placeholder="输入新标签名..." style="width:260px" @keyup.enter="doAddTag" />
            <el-button type="primary" :icon="Plus" @click="doAddTag">添加</el-button>
          </div>
          <div class="tag-list">
            <el-tag v-for="t in allTags" :key="t.id" closable size="large" class="tag-item" @close="doDeleteTag(t)">{{ t.name }}</el-tag>
          </div>
        </template>

        <!-- 用户管理 -->
        <template v-if="activeMenu === 'users'">
          <div class="section-header"><h3>用户列表</h3></div>
          <el-table :data="users" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="email" label="邮箱" />
            <el-table-column prop="role" label="角色" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.role === 2" type="danger" size="small">管理员</el-tag>
                <el-tag v-else-if="row.role === 1" type="warning" size="small">作者</el-tag>
                <el-tag v-else type="info" size="small">用户</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190">
              <template #default="{ row }">
                <el-button v-if="row.role !== 2" size="small"
                  :type="row.role === 1 ? 'warning' : 'success'" plain
                  @click="toggleAuthorRole(row)">
                  {{ row.role === 1 ? '取消作者' : '设为作者' }}
                </el-button>
                <el-button v-if="row.role !== 2" size="small" type="danger" plain @click="toggleUser(row)">
                  {{ row.isDeleted ? '启用' : '禁用' }}
                </el-button>
                <span v-else style="color:#ccc;font-size:12px">—</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </main>
    </template>

    <!-- 作品弹窗 -->
    <el-dialog v-model="workDialogVisible" :title="isEdit ? '编辑作品' : '新增作品'" width="520px">
      <el-form :model="workForm" label-position="top">
        <el-form-item label="标题"><el-input v-model="workForm.title" placeholder="作品标题" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="workForm.author" placeholder="作者名" /></el-form-item>
        <el-form-item label="封面">
          <el-upload
            class="cover-upload"
            :action="'/file/upload?dir=covers'"
            :headers="uploadHeaders"
            :show-file-list="false"
            :before-upload="beforeCoverUpload"
            :on-success="onCoverSuccess"
            accept="image/*"
          >
            <img v-if="workForm.coverUrl" :src="'/uploads/' + workForm.coverUrl" class="cover-preview" />
            <el-button v-else type="primary" plain size="small">选择封面</el-button>
          </el-upload>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-radio-group v-model="workForm.type"><el-radio value="manga">漫画</el-radio><el-radio value="novel">小说</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="workForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="简介"><el-input v-model="workForm.summary" type="textarea" :rows="3" placeholder="作品简介" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出版年份"><el-input-number v-model="workForm.publishYear" :min="1990" :max="2030" placeholder="年份" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="完结状态">
              <el-radio-group v-model="workForm.completed">
                <el-radio :value="0">连载中</el-radio>
                <el-radio :value="1">已完结</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-select v-model="selectedTagIds" multiple placeholder="选择标签" style="width:100%">
            <el-option v-for="t in allTags" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWork">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增章节弹窗（含上传） -->
    <el-dialog v-model="newChapterVisible" title="新增章节" width="560px">
      <el-form :model="newChapterForm" label-position="top">
        <el-row :gutter="16">
          <el-col :span="15">
            <el-form-item label="章节标题"><el-input v-model="newChapterForm.title" placeholder="如：第七话" /></el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="序号"><el-input-number v-model="newChapterForm.chapterNum" :min="0.5" :step="1" /></el-form-item>
          </el-col>
        </el-row>
        <!-- 漫画：上传图片 | 小说：文本框 -->
        <template v-if="selectedWork?.type !== 'novel'">
          <el-form-item label="上传图片（选填，可多选）">
            <el-upload
              :action="'/file/upload?dir=manga/'+(selectedWork?.id||0)"
              :headers="uploadHeaders"
              multiple
              :show-file-list="false"
              :before-upload="beforePageUpload"
              :on-success="onChapterFileSuccess"
              accept="image/*"
              drag
            >
              <el-icon class="el-icon--upload"><Plus /></el-icon>
              <div class="el-upload__text">拖拽图片到此处 或 <em>点击选择</em></div>
            </el-upload>
            <div style="margin-top:8px;display:flex;gap:8px;align-items:center">
              <el-button size="small" @click="selectFolder" :loading="folderUploading">📁 选择文件夹</el-button>
              <span style="font-size:12px;color:#999">直接上传整个文件夹内的图片</span>
            </div>
            <input ref="folderInput" type="file" webkitdirectory directory multiple accept="image/*" style="display:none" @change="onFolderSelected" />
          </el-form-item>
          <div v-if="newChapterForm.uploadedUrls.length > 0" class="upload-preview">
            <div v-for="(url, idx) in newChapterForm.uploadedUrls" :key="idx" class="preview-item">
              <img :src="'/uploads/' + url" class="preview-thumb" />
              <el-icon class="preview-remove" @click="removeChapterFile(idx)"><Delete /></el-icon>
            </div>
          </div>
        </template>
        <el-form-item v-else label="正文内容">
          <el-input v-model="newChapterForm.textContent" type="textarea" :rows="10" placeholder="输入章节正文..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newChapterVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNewChapter" :loading="uploadingChapter">
          创建章节{{ newChapterForm.uploadedUrls.length ? '（含 ' + newChapterForm.uploadedUrls.length + ' 张图）' : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 章节编辑弹窗 -->
    <el-dialog v-model="showChapterEdit" title="编辑章节" width="560px">
      <el-form :model="editChapterForm" label-position="top">
        <el-row :gutter="16">
          <el-col :span="15">
            <el-form-item label="章节标题"><el-input v-model="editChapterForm.title" /></el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="序号"><el-input-number v-model="editChapterForm.chapterNum" :min="0.5" :step="1" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="追加图片（选填）">
          <el-upload
            :action="'/file/upload?dir=manga/'+(selectedWork?.id||0)"
            :headers="uploadHeaders"
            multiple
            :show-file-list="false"
            :before-upload="beforePageUpload"
            :on-success="onEditFileSuccess"
            accept="image/*"
            drag
          >
            <el-icon class="el-icon--upload"><Plus /></el-icon>
            <div class="el-upload__text">拖拽或点击追加图片</div>
          </el-upload>
        </el-form-item>
        <div v-if="editChapterUploaded.length > 0" class="upload-preview">
          <div v-for="(url, idx) in editChapterUploaded" :key="idx" class="preview-item">
            <img :src="'/uploads/' + url" class="preview-thumb" />
            <el-icon class="preview-remove" @click="removeEditFile(idx)"><Delete /></el-icon>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showChapterEdit = false">取消</el-button>
        <el-button type="primary" @click="saveChapterEdit">
          保存{{ editChapterUploaded.length ? '（新增 ' + editChapterUploaded.length + ' 张图）' : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- TXT 导入弹窗 -->
    <el-dialog v-model="showTxtImport" title="导入小说 TXT" width="480px">
      <p style="color:#666;margin-bottom:16px;font-size:14px">
        上传 TXT 文件，系统自动识别章节标记（如"1 标题"）并拆分
      </p>
      <el-upload
        :action="'/novel-content/import?workId='+(selectedWork?.id||0)"
        :headers="uploadHeaders"
        :show-file-list="true"
        :limit="1"
        :on-success="onTxtSuccess"
        accept=".txt"
        drag
      >
        <el-icon class="el-icon--upload"><Plus /></el-icon>
        <div class="el-upload__text">拖拽 TXT 文件到此处 或 <em>点击选择</em></div>
      </el-upload>
    </el-dialog>

    <!-- 快速导入弹窗（整部作品） -->
    <el-dialog v-model="showQuickImport" title="快速导入作品" width="560px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        <template #title>
          <span v-if="quickForm.kind === 'manga'">
            上传 ZIP/CBZ 漫画包：每个子文件夹 = 一个章节，文件夹内图片按自然顺序作为页面（与 Komga/Kavita 等漫画站一致的目录约定）。未填封面时自动取第一页。
          </span>
          <span v-else-if="quickForm.kind === 'novel-txt'">
            上传 TXT 小说：自动识别 UTF-8 / GBK 编码，按「第X章 / 第X回 / 楔子 / Chapter N」等格式智能分章，一次性建好整本书。
          </span>
          <span v-else>
            上传 EPUB 小说：按书内 spine 顺序抽取各章正文（跳过封面/版权页），适合导入正规电子书。
          </span>
        </template>
      </el-alert>
      <el-form label-position="top">
        <el-form-item label="导入类型">
          <el-radio-group v-model="quickForm.kind">
            <el-radio-button value="manga">漫画 ZIP/CBZ</el-radio-button>
            <el-radio-button value="novel-txt">小说 TXT</el-radio-button>
            <el-radio-button value="novel-epub">小说 EPUB</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <div class="quick-drop" v-if="!quickFile" :class="{ 'is-novel': quickForm.kind !== 'manga' }">
            <el-upload drag :accept="quickAccept" :auto-upload="false" :limit="1" :on-change="onQuickFileChange">
              <el-icon class="el-icon--upload"><Upload /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击选择</em>
                <div class="quick-hint">{{ quickForm.kind === 'manga' ? '支持 .zip / .cbz，单文件最大 300MB' : (quickForm.kind === 'novel-txt' ? '支持 .txt' : '支持 .epub') }}</div>
              </div>
            </el-upload>
          </div>
          <div v-else class="quick-file">
            <span class="quick-file-name">{{ quickFile.name }}</span>
            <span class="quick-file-size">{{ (quickFile.size / 1024 / 1024).toFixed(2) }} MB</span>
            <el-button size="small" text type="danger" @click="removeQuickFile">移除</el-button>
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标题" required>
              <el-input v-model="quickForm.title" placeholder="作品标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者">
              <el-input v-model="quickForm.author" placeholder="作者名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="简介">
          <el-input v-model="quickForm.summary" type="textarea" :rows="2" placeholder="作品简介（选填）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出版年份">
              <el-input-number v-model="quickForm.publishYear" :min="1000" :max="2100" placeholder="如 1939" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="完结状态">
              <el-radio-group v-model="quickForm.completed">
                <el-radio :value="1">已完结</el-radio>
                <el-radio :value="0">连载中</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showQuickImport = false">取消</el-button>
        <el-button type="primary" :loading="quickImporting" @click="doQuickImport">
          {{ quickImporting ? '正在导入...' : '开始导入' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 漫画页预览弹窗 -->
    <el-dialog v-model="showPagesDialog" :title="'「' + (viewingChapter?.title || '') + '」的页面'" width="700px">
      <div v-if="chapterPages.length === 0" style="color:#999;text-align:center;padding:40px">暂无图片</div>
      <div v-else class="pages-grid">
        <div v-for="p in chapterPages" :key="p.id" class="page-item">
          <img :src="'/uploads/' + p.imageUrl" class="page-thumb" />
          <span class="page-num">第{{ p.pageNum }}页</span>
          <el-button size="small" type="danger" :icon="Delete" circle @click="deletePage(p)" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ====== 登录页 ====== */
.admin-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  width: 100%;
}
.admin-login-card {
  background: #fff;
  padding: 48px 40px 36px;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
  width: 400px;
}
.admin-login-icon { text-align: center; margin-bottom: 16px; }
.admin-login-card h2 { text-align: center; font-size: 20px; color: #333; margin-bottom: 28px; }
.btn-admin-login { width: 100%; }
.admin-login-tip { text-align: center; margin-top: 14px; font-size: 12px; color: #bbb; }

/* ====== 管理布局 ====== */
.admin-layout { display: flex; min-height: 100vh; background: var(--bg-page); }
.admin-sidebar { width: 200px; background: #2a2a2a; display: flex; flex-direction: column; position: fixed; top: 0; left: 0; bottom: 0; z-index: 50; }
.admin-logo { color: #fff; font-size: 16px; padding: 24px 20px 20px; border-bottom: 1px solid rgba(255,255,255,0.08); }
.sidebar-footer { margin-top: auto; padding: 20px; border-top: 1px solid rgba(255,255,255,0.08); }
.sidebar-footer a { color: #888; font-size: 13px; text-decoration: none; display: block; }
.sidebar-footer a:hover { color: var(--accent); }
.sidebar-user { color: var(--accent); font-size: 14px; margin-bottom: 8px; }
.admin-main { flex: 1; margin-left: 200px; padding: 32px 36px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.section-header h3 { font-size: 18px; color: #333; }
.chapter-layout { display: flex; gap: 28px; }
.chapter-work-list { width: 220px; flex-shrink: 0; }
.chapter-work-list h4 { margin-bottom: 12px; color: #555; }
.work-select-item { padding: 10px 14px; margin-bottom: 6px; background: #fff; border-radius: 6px; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.work-select-item:hover { background: #e9e0f5; }
.work-select-item.active { background: var(--accent); color: #fff; }
.chapter-detail { flex: 1; }
.empty-tip { color: #999; padding: 60px 0; text-align: center; }
.tag-create { display: flex; gap: 12px; margin-bottom: 20px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 12px; }
.tag-item { cursor: pointer; }

/* 封面上传 */
.cover-preview {
  max-width: 200px; max-height: 150px;
  border-radius: 6px; border: 1px solid #eee;
}
.cover-upload { display: inline-block; }

/* 章节图片上传 */
.page-upload { background: #f8f9fb; padding: 16px; border-radius: 8px; }

/* 漫画页预览 */
.pages-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.page-item { text-align: center; position: relative; }
.page-thumb { width: 100%; aspect-ratio: 3/4; object-fit: cover; border-radius: 4px; border: 1px solid #eee; }
.page-num { display: block; font-size: 12px; color: #999; margin-top: 4px; }

/* 章节上传预览 */
.upload-preview { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.preview-item { position: relative; width: 80px; height: 110px; }
.preview-thumb { width: 100%; height: 100%; object-fit: cover; border-radius: 4px; border: 1px solid #eee; }
.preview-remove { position: absolute; top: -6px; right: -6px; background: #d50707; color: #fff; border-radius: 50%; cursor: pointer; font-size: 14px; padding: 2px; }

/* ============================================ */
/*  移动端汉堡菜单                               */
/* ============================================ */
.mobile-topbar { display: none; }
.sidebar-overlay { display: none; }

/* ============================================ */
/*  响应式适配 - 手机端 (< 768px)               */
/* ============================================ */
@media (max-width: 767px) {
  /* 登录页 */
  .admin-login-card { width: 90vw; padding: 36px 24px 28px; }

  /* 移动端顶部栏 */
  .mobile-topbar {
    display: flex; align-items: center; gap: 12px;
    padding: 8px 12px; background: #2a2a2a; position: sticky; top: 0; z-index: 60;
  }
  .mobile-title { color: #fff; font-size: 15px; }
  .hamburger-btn { background: transparent !important; border-color: rgba(255,255,255,0.2) !important; color: #fff !important; }

  /* 遮罩 */
  .sidebar-overlay {
    display: block; position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 55;
  }

  /* 侧边栏 - 默认隐藏，点击汉堡图标滑出 */
  .admin-sidebar {
    width: 240px;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    z-index: 58;
  }
  .admin-sidebar.sidebar-open { transform: translateX(0); }

  /* 主内容区无左边距 */
  .admin-main {
    margin-left: 0 !important; padding: 16px 12px;
  }

  /* 筛选+搜索行折行 */
  .section-header { flex-wrap: wrap; gap: 8px; }
  .section-header + div[style] {
    flex-wrap: wrap !important; gap: 8px !important;
  }

  /* 表格横向滚动 */
  :deep(.el-table) { display: block; overflow-x: auto; white-space: nowrap; }

  /* 章节布局堆叠 */
  .chapter-layout { flex-direction: column; gap: 16px; }
  .chapter-work-list { width: 100%; }

  /* 弹窗全屏或缩小 */
  :deep(.el-dialog) { width: 95vw !important; margin: 10px auto !important; }
  :deep(.el-dialog__body) { padding: 16px !important; }

  /* 表单行堆叠 */
  :deep(.el-row) .el-col { max-width: 100% !important; flex: 0 0 100% !important; margin-bottom: 6px; }

  /* 上传预览响应式 */
  .upload-preview { gap: 8px; }
  .preview-item { width: 60px; height: 85px; }

  /* 漫画页预览网格 */
  .pages-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; }

  /* 作品选择列表 */
  .work-select-item { font-size: 13px; padding: 8px 12px; }

  /* 封面上传 */
  .cover-preview { max-width: 140px; max-height: 110px; }

  /* 底部边距 */
  .admin-main { padding-bottom: 40px; }
}

/* ============================================ */
/*  平板适配 (768px - 1024px)                    */
/* ============================================ */
@media (min-width: 768px) and (max-width: 1024px) {
  .admin-main { padding: 24px 20px; }
  .chapter-layout { gap: 20px; }
  .chapter-work-list { width: 180px; }
  :deep(.el-dialog) { width: 90vw !important; }
}

/* ============================================ */
/*  数据大屏                                     */
/* ============================================ */
.dash-loading { text-align: center; padding: 80px 0; color: #999; }
.dash-container { max-width: 1000px; }
.dash-cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin-bottom: 24px; }
.dash-card { background: #fff; border-radius: 10px; padding: 18px 12px; text-align: center; box-shadow: 0 1px 6px rgba(0,0,0,0.05); }
.dc-num { display: block; font-size: 26px; font-weight: 700; color: var(--accent); }
.dc-label { display: block; font-size: 12px; color: #999; margin-top: 4px; }
.dash-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 20px; }
.dash-panel { background: #fff; border-radius: 10px; padding: 18px 20px; box-shadow: 0 1px 6px rgba(0,0,0,0.05); }
.dash-panel h4 { font-size: 14px; color: #555; margin-bottom: 14px; }
.dash-empty { text-align: center; color: #ccc; padding: 20px 0; }

/* 横条进度图 */
.type-bars, .tag-bars { display: flex; flex-direction: column; gap: 10px; }
.type-bar-row, .tag-bar-row { display: flex; align-items: center; gap: 8px; }
.bar-label { font-size: 12px; color: #777; min-width: 30px; }
.bar-track { flex: 1; height: 18px; background: #f0f0f0; border-radius: 9px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, var(--accent), var(--accent-light)); border-radius: 9px; transition: width 0.6s; min-width: 2px; }
.bar-novel { background: linear-gradient(90deg, #67c23a, #85ce61); }
.bar-purple { background: linear-gradient(90deg, var(--accent), var(--accent-light)); }
.bar-val { font-size: 12px; color: #999; min-width: 24px; text-align: right; }

/* 趋势图 */
.trend-bars { display: flex; align-items: flex-end; justify-content: space-between; gap: 8px; height: 100px; padding-top: 20px; }
.trend-bar-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px; }
.trend-bar { width: 28px; background: linear-gradient(180deg, var(--accent), var(--accent-light)); border-radius: 4px 4px 0 0; min-height: 1px; transition: height 0.5s; }
.trend-label { font-size: 10px; color: #999; }
.trend-val { font-size: 10px; color: var(--accent); font-weight: 600; }

@media (max-width: 767px) {
  .dash-cards { grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .dash-charts { grid-template-columns: 1fr; }
  .dash-card { padding: 12px 8px; }
  .dc-num { font-size: 20px; }
  .trend-bar { width: 20px; }
}

/* ====== 快速导入弹窗 ====== */
.quick-drop :deep(.el-upload-dragger) { width: 100%; }
.quick-hint { color: #999; font-size: 12px; margin-top: 6px; }
.quick-file {
  width: 100%; display: flex; align-items: center; gap: 12px;
  background: #f5f7fa; border: 1px solid #e4e7ed; border-radius: 6px;
  padding: 12px 16px;
}
.quick-file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; color: #333; }
.quick-file-size { color: #999; font-size: 12px; }
@media (max-width: 767px) {
  .quick-file-name { font-size: 12px; }
}
</style>
