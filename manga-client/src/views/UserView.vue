<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Camera, Reading, Collection, Clock } from '@element-plus/icons-vue'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

// ====== 热力图数据生成 ======
const today = new Date()
const heatmapDays = 150  // 展示最近约5个月
const heatmapOffset = 50  // 左侧月份标签宽度

// 找到最近一个周日作为起始
const heatmapStart = new Date(today)
heatmapStart.setDate(heatmapStart.getDate() - heatmapDays)

const heatmapWeeks = computed(() => {
  const weeks = []
  let current = new Date(heatmapStart)
  // 对齐到周日
  current.setDate(current.getDate() - current.getDay())
  while (current <= today) {
    const week = []
    for (let d = 0; d < 7; d++) {
      const date = new Date(current)
      date.setDate(date.getDate() + d)
      if (date <= today && date >= heatmapStart) {
        const key = date.toISOString().slice(0, 10)
        week.push({ date: key, label: `${date.getMonth() + 1}/${date.getDate()}` })
      } else {
        week.push(null)
      }
    }
    weeks.push(week)
    current.setDate(current.getDate() + 7)
  }
  return weeks
})

const heatmapMonths = computed(() => {
  const months = []
  let lastMonth = -1
  heatmapWeeks.value.forEach((week, wi) => {
    const firstDay = week.find(d => d)
    if (firstDay) {
      const m = parseInt(firstDay.date.slice(5, 7))
      if (m !== lastMonth) {
        months.push({ month: m, x: heatmapOffset + wi * 13 })
        lastMonth = m
      }
    }
  })
  return months
})

function heatmapColor(count) {
  if (count < 0) return '#161b22'  // 无数据的格子（未来日期）
  if (count === 0) return '#ebedf0'
  if (count <= 2) return '#c6e48b'
  if (count <= 5) return '#7bc96f'
  if (count <= 10) return '#239a3b'
  return '#196127'
}

const router = useRouter()
const userStore = useUserStore()
const user = ref(null)
const stats = ref({ favorites: 0, inProgress: 0, totalRead: 0 })
const heatmapData = ref({})  // { "2026-07-15": 3, "2026-07-14": 1, ... }
const recentReads = ref([])

// 密码
const showPwd = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const pwdLoading = ref(false)

// 头像裁剪
const showCrop = ref(false)
const cropImgObj = ref(null)
const cropCanvas = ref(null)
const cropX = ref(0)
const cropY = ref(0)
const cropScale = ref(1)
const cropDragging = ref(false)
const cropStart = ref({ x: 0, y: 0, imgX: 0, imgY: 0 })
const cropLoading = ref(false)
const SIZE = 260 // crop area size

const avatarUrl = computed(() => {
  if (user.value?.avatarUrl) return '/uploads/' + user.value.avatarUrl
  return null
})

onMounted(async () => {
  const saved = localStorage.getItem('user')
  if (!saved) { router.push('/'); return }
  user.value = JSON.parse(saved)
  try {
    const [favRes, statsRes, heatRes] = await Promise.all([
      request.get('/favorite/list', { silent: true }),
      request.get('/stats/user/' + user.value.id, { silent: true }),
      request.get('/stats/heatmap/' + user.value.id, { silent: true }),
    ])
    stats.value.favorites = (favRes.data || []).length
    if (statsRes.data) {
      stats.value.readingCount = statsRes.data.readingCount || 0
      stats.value.totalPages = statsRes.data.totalPages || 0
      stats.value.estimatedHours = statsRes.data.estimatedHours || 0
    }
    heatmapData.value = heatRes.data || {}
    // 获取收藏作品详情
    const ids = favRes.data || []
    if (ids.length > 0) {
      const details = await Promise.all(ids.slice(0, 6).map(id => request.get('/work/' + id, { silent: true })))
      recentReads.value = details.map(r => r.data).filter(Boolean)
    }
  } catch { /* */ }
})

async function changePassword() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) { ElMessage.warning('请填写完整'); return }
  if (pwdForm.value.newPassword !== pwdForm.value.confirm) { ElMessage.warning('两次密码不一致'); return }
  if (pwdForm.value.newPassword.length < 6) { ElMessage.warning('密码至少6位'); return }
  pwdLoading.value = true
  try {
    await request.put('/user/password', pwdForm.value, { silent: true })
    ElMessage.success('密码已修改')
    showPwd.value = false
    pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch { /* */ }
  finally { pwdLoading.value = false }
}

// 头像裁剪
function onAvatarSelect(e) {
  const file = e.target.files[0]; if (!file) return
  const r = new FileReader()
  r.onload = ev => {
    cropX.value = 0; cropY.value = 0; cropScale.value = 0.3; showCrop.value = true
    const img = new Image(); img.onload = async () => { cropImgObj.value = img; await nextTick(); drawCropPreview() }
    img.src = ev.target.result
  }
  r.readAsDataURL(file); e.target.value = ''
}

function renderCrop(ctx, img, s) {
  ctx.clearRect(0, 0, SIZE, SIZE)
  ctx.save()
  ctx.beginPath(); ctx.arc(SIZE/2, SIZE/2, SIZE/2, 0, Math.PI*2); ctx.clip()
  ctx.translate(SIZE/2, SIZE/2)
  ctx.translate(-cropX.value, -cropY.value)
  ctx.scale(s, s)
  ctx.drawImage(img, -img.width/2, -img.height/2)
  ctx.restore()
}
function drawCropPreview() {
  if (!cropCanvas.value || !cropImgObj.value) return
  renderCrop(cropCanvas.value.getContext('2d'), cropImgObj.value, cropScale.value)
}

function onCropWheel(e) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.05 : 0.05
  cropScale.value = Math.max(0.1, Math.min(3, +(cropScale.value + delta).toFixed(2)))
  drawCropPreview()
}

function onCropDown(e) {
  e.preventDefault()
  cropDragging.value = true
  cropStart.value = { x: e.clientX, y: e.clientY, imgX: cropX.value, imgY: cropY.value }
  document.addEventListener('mousemove', onCropMove)
  document.addEventListener('mouseup', onCropUp)
}
function onCropMove(e) {
  if (!cropDragging.value) return
  cropX.value = cropStart.value.imgX - (e.clientX - cropStart.value.x)
  cropY.value = cropStart.value.imgY - (e.clientY - cropStart.value.y)
  drawCropPreview()
}
function onCropUp() {
  cropDragging.value = false
  document.removeEventListener('mousemove', onCropMove)
  document.removeEventListener('mouseup', onCropUp)
}
function zoomCrop(d) { cropScale.value = Math.max(0.1, Math.min(3, +(cropScale.value + d).toFixed(2))); drawCropPreview() }

async function confirmCrop() {
  if (!cropImgObj.value) { ElMessage.warning('图片未加载'); return }
  cropLoading.value = true
  try {
    const canvas = document.createElement('canvas')
    canvas.width = SIZE; canvas.height = SIZE
    const ctx = canvas.getContext('2d')
    renderCrop(ctx, cropImgObj.value, cropScale.value)
    // 用 base64 代替 toBlob，避免兼容性问题
    const dataUrl = canvas.toDataURL('image/png')
    const blob = await fetch(dataUrl).then(r => r.blob())
    const fd = new FormData(); fd.append('file', blob, 'avatar.png')
    const res = await request.post('/file/upload/avatar', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }, silent: true
    })
    const url = res.data.url
    await request.put('/user/avatar', { avatarUrl: url }, { silent: true })
    user.value.avatarUrl = url
    const saved = JSON.parse(localStorage.getItem('user') || '{}')
    saved.avatarUrl = url; localStorage.setItem('user', JSON.stringify(saved))
    userStore.user.avatarUrl = url
    ElMessage.success('头像已更新'); showCrop.value = false
  } catch (e) { ElMessage.error('上传失败: ' + (e.message || '未知错误')); console.error(e) }
  finally { cropLoading.value = false }
}
</script>

<template>
  <div class="user-page" v-if="user">
    <a href="javascript:;" class="page-back" @click="window.history.length > 1 ? router.back() : router.push('/')">← 返回</a>
    <router-link to="/" class="page-home">首页</router-link>

    <div class="user-container">
      <!-- 头像卡片 -->
      <div class="profile-card">
        <div class="avatar-wrap" @click="$refs.avatarInput.click()">
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" />
          <div v-else class="avatar-default">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="#ccc"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v1.2c0 .7.5 1.2 1.2 1.2h16.8c.7 0 1.2-.5 1.2-1.2v-1.2c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
          </div>
          <div class="avatar-overlay"><el-icon><Camera /></el-icon></div>
        </div>
        <h2 class="profile-name">{{ user.username }}</h2>
        <p class="profile-meta">{{ user.email || '未绑定邮箱' }} · {{ user.role === 1 ? '管理员' : '用户' }}</p>
        <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarSelect" />
      </div>

      <!-- 统计 -->
      <div class="stats-row">
        <div class="stat-card" @click="router.push('/bookshelf')">
          <el-icon :size="22" color="var(--accent)"><Collection /></el-icon>
          <span class="stat-num">{{ stats.favorites }}</span>
          <span class="stat-label">我的收藏</span>
        </div>
        <div class="stat-card">
          <el-icon :size="22" color="var(--accent)"><Reading /></el-icon>
          <span class="stat-num">{{ stats.readingCount || '0' }}</span>
          <span class="stat-label">阅读中</span>
        </div>
        <div class="stat-card">
          <el-icon :size="22" color="var(--accent)"><Clock /></el-icon>
          <span class="stat-num">{{ stats.totalPages || '0' }}</span>
          <span class="stat-label">已翻页</span>
        </div>
      </div>
      <div v-if="stats.estimatedHours" class="stat-extra">
        📖 累计阅读时长约 <strong>{{ stats.estimatedHours }}</strong> 小时
      </div>

      <!-- 阅读热力图 -->
      <div class="section-card" v-if="Object.keys(heatmapData).length">
        <h3 class="section-title">📊 阅读热力图</h3>
        <div class="heatmap-container">
          <svg viewBox="0 0 700 120" class="heatmap-svg">
            <!-- 月份标签 -->
            <text v-for="m in heatmapMonths" :key="m.month" :x="m.x" y="10" font-size="10" fill="#999">{{ m.month }}月</text>
            <!-- 列（周） -->
            <g v-for="(week, wi) in heatmapWeeks" :key="wi">
              <rect v-for="(day, di) in week" :key="di"
                :x="heatmapOffset + wi * 13"
                :y="20 + di * 13"
                width="11" height="11" rx="2"
                :fill="heatmapColor(day && day.date ? (heatmapData[day.date] || 0) : -1)"
              >
                <title v-if="day">{{ day.label }}: {{ heatmapData[day.date] || 0 }} 次阅读</title>
              </rect>
            </g>
            <!-- 图例 -->
            <rect :x="heatmapOffset" y="115" width="11" height="11" rx="2" fill="#ebedf0" />
            <rect :x="heatmapOffset + 15" y="115" width="11" height="11" rx="2" fill="#c6e48b" />
            <rect :x="heatmapOffset + 30" y="115" width="11" height="11" rx="2" fill="#7bc96f" />
            <rect :x="heatmapOffset + 45" y="115" width="11" height="11" rx="2" fill="#239a3b" />
            <rect :x="heatmapOffset + 60" y="115" width="11" height="11" rx="2" fill="#196127" />
            <text :x="heatmapOffset + 78" y="124" font-size="9" fill="#999">少</text>
            <text :x="heatmapOffset + 72 + 20" y="124" font-size="9" fill="#999">多</text>
          </svg>
        </div>
      </div>

      <!-- 最近收藏 -->
      <div class="section-card" v-if="recentReads.length">
        <h3 class="section-title">最近收藏</h3>
        <div class="recent-list">
          <div v-for="w in recentReads" :key="w.id" class="recent-item" @click="router.push('/work/' + w.id)">
            <div class="recent-cover">
              <img v-if="w.coverUrl" :src="'/uploads/' + w.coverUrl" />
              <div v-else class="rcv-dummy"></div>
            </div>
            <div class="recent-info">
              <p class="recent-title">{{ w.title }}</p>
              <p class="recent-author">{{ w.author }}</p>
            </div>
            <span class="recent-arrow">→</span>
          </div>
        </div>
      </div>

      <!-- 操作 -->
      <div class="section-card">
        <h3 class="section-title">账号设置</h3>
        <div class="setting-row" @click="showPwd = !showPwd">
          <span>🔒 修改密码</span>
          <el-icon><Edit /></el-icon>
        </div>
        <div v-if="showPwd" class="pwd-form">
          <el-input v-model="pwdForm.oldPassword" type="password" placeholder="原密码" size="small" style="margin-bottom:8px" />
          <el-input v-model="pwdForm.newPassword" type="password" placeholder="新密码（至少6位）" size="small" style="margin-bottom:8px" />
          <el-input v-model="pwdForm.confirm" type="password" placeholder="确认新密码" size="small" style="margin-bottom:10px" />
          <el-button type="primary" size="small" :loading="pwdLoading" @click="changePassword" style="width:100%">确认修改</el-button>
        </div>
      </div>
    </div>

    <!-- 裁剪弹窗 -->
    <div v-show="showCrop" class="crop-overlay">
      <div class="crop-dialog">
        <h3>长按拖拽 · 滚轮缩放 · 按钮微调</h3>
        <div class="crop-view" @mousedown="onCropDown" @wheel="onCropWheel">
          <canvas ref="cropCanvas" width="260" height="260" class="crop-canvas"></canvas>
        </div>
        <div class="crop-ctrl">
          <el-button size="small" circle @click="zoomCrop(-0.1)">−</el-button>
          <span>{{ Math.round(cropScale * 100) }}%</span>
          <el-button size="small" circle @click="zoomCrop(0.1)">+</el-button>
        </div>
        <div class="crop-btns">
          <el-button @click="showCrop = false">取消</el-button>
          <el-button type="primary" :loading="cropLoading" @click="confirmCrop">确认裁剪</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-page { min-height: 100vh; background: linear-gradient(180deg, var(--accent) 0%, var(--accent-light) 30%, var(--bg-page) 30%); padding: 20px 0 60px; }
.page-back { position: absolute; top: 20px; left: 24px; color: #fff; text-decoration: none; font-size: 14px; z-index: 5; }
.page-home { position: absolute; top: 20px; left: 80px; color: rgba(255,255,255,0.7); text-decoration: none; font-size: 13px; z-index: 5; }
.page-home:hover { color: #fff; }
.user-container { max-width: 480px; margin: 0 auto; padding: 0 20px; }

/* 头像 */
.profile-card { background: #fff; border-radius: 16px; padding: 56px 20px 24px; margin-top: 20px; text-align: center; box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
.avatar-wrap { width: 96px; height: 96px; border-radius: 50%; margin: 0 auto 16px; position: relative; cursor: pointer; overflow: hidden; border: 3px solid #fff; box-shadow: 0 0 0 3px var(--accent); }
.avatar-wrap:hover .avatar-overlay { opacity: 1; }
.avatar-img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.avatar-default { width: 100%; height: 100%; border-radius: 50%; background: #e8e8e8; display: flex; align-items: center; justify-content: center; }
.avatar-overlay { position: absolute; inset: 0; border-radius: 50%; background: rgba(0,0,0,0.5); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24px; opacity: 0; transition: opacity 0.2s; }
.profile-name { font-size: 20px; font-weight: 700; color: #333; margin-bottom: 4px; }
.profile-meta { font-size: 13px; color: #aaa; }

/* 统计 */
.stats-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; margin-top: 14px; }
.stat-card { background: #fff; border-radius: 12px; padding: 16px 8px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.04); cursor: pointer; transition: transform 0.2s; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat-card:hover { transform: translateY(-2px); }
.stat-num { font-size: 22px; font-weight: 700; color: #333; }
.stat-label { font-size: 11px; color: #aaa; }

/* 最近收藏 */
.section-card { background: #fff; border-radius: 12px; margin-top: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); overflow: hidden; padding: 18px 20px; }
.section-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 14px; }
.recent-list { display: flex; flex-direction: column; gap: 2px; }
.recent-item { display: flex; align-items: center; gap: 12px; padding: 10px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.recent-item:hover { background: #f5f5f5; }
.recent-cover { width: 44px; height: 60px; border-radius: 4px; overflow: hidden; flex-shrink: 0; }
.recent-cover img { width: 100%; height: 100%; object-fit: cover; }
.rcv-dummy { width: 100%; height: 100%; background: linear-gradient(135deg, #e8edf2, #d5dde5); }
.recent-info { flex: 1; min-width: 0; }
.recent-title { font-size: 14px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.recent-author { font-size: 12px; color: #999; margin-top: 2px; }
.recent-arrow { color: #ccc; font-size: 14px; }

/* 设置 */
.setting-row { padding: 12px 0; display: flex; align-items: center; justify-content: space-between; cursor: pointer; font-size: 14px; color: #555; }
.setting-row:hover { color: var(--accent); }
.pwd-form { padding-top: 4px; }

/* 裁剪弹窗 */
.crop-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.75); z-index: 1000; display: flex; align-items: center; justify-content: center; }
.crop-dialog { background: #fff; border-radius: 14px; padding: 24px; width: 340px; text-align: center; }
.crop-dialog h3 { font-size: 14px; color: #888; margin-bottom: 14px; font-weight: 400; }
.crop-view { width: 260px; height: 260px; margin: 0 auto 12px; border-radius: 50%; overflow: hidden; cursor: grab; }
.crop-canvas { display: block; width: 260px; height: 260px; }
.crop-ctrl { display: flex; align-items: center; justify-content: center; gap: 16px; margin-bottom: 16px; }
.crop-btns { display: flex; gap: 12px; justify-content: center; }

/* 统计额外信息 */
.stat-extra {
  text-align: center; font-size: 13px; color: var(--text-muted, #999);
  padding: 8px 0 4px;
}
.stat-extra strong { color: var(--accent, var(--accent)); }

/* 热力图 */
.heatmap-container {
  overflow-x: auto; padding: 4px 0;
}
.heatmap-svg {
  width: 100%; min-width: 500px; height: auto;
}

/* 统计卡片 (补充) */
.user-container { max-width: 520px; margin: 0 auto; padding: 0 20px; }

/* ============================================ */
/*  响应式适配                                    */
/* ============================================ */
@media (max-width: 767px) {
  .user-container { padding: 0 12px; }
  .profile-card { margin-top: 16px; padding: 48px 14px 20px; }
  .stats-row { grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .section-card { padding: 14px; }
  .crop-dialog { width: 92vw; padding: 16px; }
  .crop-view, .crop-canvas { width: 220px; height: 220px; }
}
</style>
