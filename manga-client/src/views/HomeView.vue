<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Moon, Sunny } from '@element-plus/icons-vue'
import { login } from '@/api/user'
import { getWorkList } from '@/api/work'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useDarkMode } from '@/composables/useDarkMode'

const router = useRouter()
const userStore = useUserStore()
const { isDark, toggle: toggleDark } = useDarkMode()

const scrolled = ref(false)
const searchKeyword = ref('')

function doSearch() {
  const kw = searchKeyword.value.trim()
  if (kw) router.push('/search?q=' + encodeURIComponent(kw))
}

function doLogout() {
  userStore.logout()
  favoriteWorks.value = []
  ElMessage.info('已退出登录')
}

// 侧边导航
const sections = [
  { id: 'manga',    label: '漫画',  short: '漫' },
  { id: 'novel',    label: '小说',  short: '文' },
  { id: 'ranking',  label: '排行榜', short: '榜' },
  { id: 'favorite', label: '收藏',  short: '藏' },
]

// 每个区块只展示前 5 条，其余通过"查看更多"进入独立页面
const MAX_SHOW = 5

const mangaWorks = ref([])
const novelWorks = ref([])
const artbookWorks = ref([])
const favoriteWorks = ref([])
const mockNovel = Array.from({ length: 6 }, (_, i) => ({
  id: i + 100, title: `小说标题 ${i + 1}`, author: `小说作者${i + 1}`, coverUrl: null,
}))
const mockArtbook = Array.from({ length: 4 }, (_, i) => ({
  id: i + 200, title: `画集名称 ${i + 1}`, author: `画师${i + 1}`, coverUrl: null,
}))
const mockFavorite = Array.from({ length: 3 }, (_, i) => ({
  id: i + 300, title: `收藏作品 ${i + 1}`, author: `作者${i + 1}`, coverUrl: null,
}))

// 登录弹窗
const loginVisible = ref(false)
const loginForm = ref({ username: '', password: '' })

function openLogin() { loginVisible.value = true }
function goRegister() {
  loginVisible.value = false
  router.push('/register')
}
async function doLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  try {
    const res = await login({
      username: loginForm.value.username,
      password: loginForm.value.password,
    })
    userStore.setLogin(res.data)
    loginVisible.value = false
    ElMessage.success(`欢迎回来，${res.data.user.username}！`)
    loginForm.value = { username: '', password: '' }
    // 重新加载收藏
    loadFavorites()
  } catch {
    // 错误已在 request 拦截器处理
  }
}

// 滚动监听
onMounted(async () => {
  window.addEventListener('scroll', handleScroll)
  try {
    const [mangaRes, novelRes] = await Promise.all([
      getWorkList({ type: 'manga', pageSize: 5 }),
      getWorkList({ type: 'novel', pageSize: 5 }),
    ])
    mangaWorks.value = mangaRes.data.records
    novelWorks.value = novelRes.data.records
  } catch { /* 忽略 */ }

  // 已登录则加载收藏
  loadFavorites()
})

async function loadFavorites() {
  if (!userStore.isLoggedIn) return
  try {
    const favRes = await request.get('/favorite/list')
    const ids = favRes.data || []
    if (ids.length > 0) {
      const works = await Promise.all(ids.map(id => request.get('/work/' + id, { silent: true })))
      favoriteWorks.value = works.map(r => r.data).filter(Boolean)
    } else {
      favoriteWorks.value = []
    }
  } catch { favoriteWorks.value = [] }
}
onUnmounted(() => window.removeEventListener('scroll', handleScroll))
function handleScroll() { scrolled.value = window.scrollY > 40 }

function scrollToSec(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function bgStyle(url) {
  return url ? { backgroundImage: `url(/uploads/${url})` } : {}
}
</script>

<template>
  <div class="yhl-page">

    <!-- ====== 右侧浮动导航 ====== -->
    <nav class="side-nav">
      <ul>
        <li v-for="sec in sections" :key="sec.id">
          <a href="javascript:;" :data-short-text="sec.short" @click="scrollToSec(sec.id)">
            <span>{{ sec.label }}</span>
          </a>
        </li>
      </ul>
    </nav>

    <!-- ====== 顶部栏 ====== -->
    <header class="header" :class="{ 'header--scrolled': scrolled }">
      <div class="header-inner">
        <h1 class="header-logo" :class="{ 'header-logo--small': scrolled }">
          <a href="/">
            <svg viewBox="0 0 160 50" class="logo-svg">
              <path class="logo-path" d="M39.69 5.61c-1.83 6.45-10.09 9.78-11.13 9.82 3.76.04 11.61-2.57 13.89-9.82h-2.76zM54.8 16.87H26.36v27.81c2.02 0 9.47 5.31 14.22 5.31s12.19-5.31 14.22-5.31V16.87zm-26.3 1.8h24.15v8.81H28.5v-8.81zm12.08 29.18c-3.04.16-8.66-3.37-12.08-4.85V29.28h24.15V43c-3.41 1.48-9.04 5.01-12.07 4.85zM21.02 4.31h38.31v-1.8H21.02v1.8zM89.13 0c-2.21 2.99-9.62 13.18-21.25 20.33 5.69-.81 15.35-9.16 21.25-16.54 5.89 7.38 15.56 15.73 21.24 16.54C98.74 13.18 91.34 2.99 89.13 0zM78.48 17.64v1.8h21.29v-1.8H78.48zm22.72 6.01H74.91v21.03c2.02 0 9.47 5.31 14.22 5.31s12.19-5.31 14.21-5.31V23.65h-2.14zm0 19.35c-3.42 1.48-9.04 5.01-12.07 4.85-3.04.16-8.66-3.37-12.08-4.85V25.45h24.15V43zm48.57 4.47V30.54h9.01V18.69h-9.01V5.56h8.65v-1.8h-21.38v41.8c2.18 0 6.37 4.43 11.48 4.43s9.3-4.43 11.48-4.43v-2.63c-2.37 0-6.44 3.92-10.23 4.54zm6.87-18.72h-17.46v-8.27h17.46v8.27zm-9.01-23.19v13.13h-8.45V5.56h8.45zm-8.45 24.98h8.45v16.98c-2.92-.32-6.04-2.74-8.45-3.93V30.54zm-5.1-18.43h-8.97l1.62-10.71h-2.22l-1.57 10.71h-4.85v1.8h4.59L120.8 26.7l6.85 8.95a34.539 34.539 0 0 1-10.78 13.18 26.856 26.856 0 0 0 12.04-11.52l5.64 7.36v-3.45l-4.6-5.97c2.83-6.18 4.13-13.93 4.13-23.14zm-11.1 14.09l1.86-12.29h7.15a53.544 53.544 0 0 1-3.4 19.57zM.66 10.85l5.55-.11.11.57h1.24V3.19l-.11-.8H6.32L.55 2.5l.11.8 5.55-.11.11 6.74-5.77.11zm-.09 4.93l.11.8a16.012 16.012 0 0 1 6.89 1.51l.15.07v-.17l-.07-1.28a17.761 17.761 0 0 0-6.16-.95c-.31 0-.62 0-.92.02zm6.39 6.18v-.22l-.05-1.33a11.365 11.365 0 0 0-5.23-1.71c-.09-.01-.17-.03-.26-.04v.01a.41.41 0 0 0-.11-.01l.09.67a13.151 13.151 0 0 1 5.39 2.49zm-6.28-.02v.01c-.03 0-.07-.01-.11-.01l.09.69a16.979 16.979 0 0 1 6.89 3.08l.17.13v-.21l-.05-1.35a13.571 13.571 0 0 0-6.72-2.3c-.09-.01-.17-.03-.27-.04zm1.96 9.79h1.15l-.02-.13a5.169 5.169 0 0 0-1.86-3.15h-.82l.13.18a7.886 7.886 0 0 1 1.42 3.1zm1.39-3.28h-.82l.13.18a7.886 7.886 0 0 1 1.42 3.1H5.9l-.02-.13a5.107 5.107 0 0 0-1.85-3.15zm-1.09 5.64v.51l.06.27.12-.03a11.2 11.2 0 0 0 4.86-2.83v-1.77l-.19.21a14.177 14.177 0 0 1-4.85 3.64zm5.21 5.78l.03-.11H6.63l-2.35.11h-.94l.29-.67h-.17l-1.35.08a12.193 12.193 0 0 1-1.98 4.57l-.13.18h.22L.86 44a10 10 0 0 0 2.05-3.33l2.81-.11h.77a17.953 17.953 0 0 1-5.02 8.69l-.17.18h1.11l.03-.02a17.336 17.336 0 0 0 5.39-8.51c.12-.34.24-.67.35-1.03h-.03z" fill-rule="evenodd" />
            </svg>
          </a>
        </h1>
        <nav class="header-nav">
          <router-link to="/">首页</router-link>
          <router-link to="/category">分类</router-link>
          <router-link to="/ranking">排行榜</router-link>
          <router-link to="/bookshelf">书架</router-link>
          <template v-if="userStore.isLoggedIn">
            <span class="header-avatar" @click="router.push('/user')">
              <img v-if="(userStore.user || userStore.adminUser)?.avatarUrl"
                :src="'/uploads/' + (userStore.user || userStore.adminUser).avatarUrl" class="avatar-small" />
              <svg v-else viewBox="0 0 24 24" width="20" height="20" class="avatar-placeholder-svg"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v1.2c0 .7.5 1.2 1.2 1.2h16.8c.7 0 1.2-.5 1.2-1.2v-1.2c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
            </span>
            <el-button class="btn-logout" size="small" round @click="doLogout">退出</el-button>
          </template>
          <el-button v-else class="btn-login" size="small" round @click="openLogin">登录</el-button>
          <el-button class="btn-theme" size="small" circle @click="toggleDark" :title="isDark ? '切换亮色' : '切换深色'">
            <el-icon><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
          </el-button>
        </nav>
      </div>
    </header>

    <!-- ====== 横幅 ====== -->
    <div class="hero">
      <div class="hero-bg"></div>
      <div class="hero-content">
        <img src="/img/fox.png" alt="" class="hero-fox" />
        <div class="hero-search-box">
          <div class="search-wrapper">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索作品、作者..."
              size="large"
              class="hero-search-input"
              @keyup.enter="doSearch"
            />
            <el-icon class="search-icon-btn" @click="doSearch"><Search /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 主内容 ====== -->
    <main class="main-content">

      <!-- 漫画 -->
      <section :id="sections[0].id" class="sec-block">
        <div class="sec-head">
          <h2 class="sec-title"><span class="sec-title-en">manga</span><span class="sec-title-cn">漫画</span></h2>
          <router-link to="/category?type=manga" class="sec-more">查看更多 →</router-link>
        </div>
        <div class="sec-grid">
          <div v-for="w in mangaWorks.slice(0, MAX_SHOW)" :key="w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <div class="card-cover">
              <div class="card-bg" :style="bgStyle(w.coverUrl)">
                <span v-if="!w.coverUrl" class="card-cover-text">{{ w.title }}</span>
              </div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 小说 -->
      <section :id="sections[1].id" class="sec-block">
        <div class="sec-head">
          <h2 class="sec-title"><span class="sec-title-en">novel</span><span class="sec-title-cn">小说</span></h2>
          <router-link to="/category?type=novel" class="sec-more">查看更多 →</router-link>
        </div>
        <div class="sec-grid">
          <div v-for="w in novelWorks.slice(0, MAX_SHOW)" :key="w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <div class="card-cover">
              <div class="card-bg" :style="bgStyle(w.coverUrl)">
                <span v-if="!w.coverUrl" class="card-cover-text">{{ w.title }}</span>
              </div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 排行榜 -->
      <section :id="sections[2].id" class="sec-block">
        <div class="sec-head">
          <h2 class="sec-title"><span class="sec-title-en">ranking</span><span class="sec-title-cn">排行榜</span></h2>
          <router-link to="/ranking" class="sec-more">查看更多 →</router-link>
        </div>
        <div class="sec-grid">
          <div v-for="w in mangaWorks.slice(0, MAX_SHOW)" :key="'r'+w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <div class="card-cover">
              <div class="card-bg" :style="bgStyle(w.coverUrl)">
                <span v-if="!w.coverUrl" class="card-cover-text">{{ w.title }}</span>
              </div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 收藏 -->
      <section :id="sections[3].id" class="sec-block">
        <div class="sec-head">
          <h2 class="sec-title"><span class="sec-title-en">favorites</span><span class="sec-title-cn">收藏</span></h2>
          <router-link to="/bookshelf" class="sec-more">查看更多 →</router-link>
        </div>
        <div v-if="!userStore.isLoggedIn" class="favorite-login-tip">
          <p>登录后查看你的收藏</p>
          <el-button size="small" round @click="openLogin">立即登录</el-button>
        </div>
        <div v-else-if="favoriteWorks.length === 0" class="favorite-empty">
          <p>还没有收藏作品</p>
          <span>浏览漫画和小说，点击❤收藏</span>
        </div>
        <div v-else class="sec-grid">
          <div v-for="w in favoriteWorks.slice(0, MAX_SHOW)" :key="w.id" class="card-item" @click="router.push('/work/' + w.id)">
            <div class="card-cover">
              <div class="card-bg" :style="bgStyle(w.coverUrl)">
                <span v-if="!w.coverUrl" class="card-cover-text">{{ w.title }}</span>
              </div>
            </div>
            <div class="card-info">
              <p class="card-title">{{ w.title }}</p>
              <p class="card-author">{{ w.author }}</p>
            </div>
          </div>
        </div>
      </section>

    </main>

    <!-- ====== 底部 ====== -->
    <!-- ====== 登录弹窗 ====== -->
    <el-dialog v-model="loginVisible" title="登录" width="380px" :close-on-click-modal="false" center>
      <el-form :model="loginForm" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password
            @keyup.enter="doLogin" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" class="btn-login-submit" @click="doLogin">登 录</el-button>
          <p class="dialog-register-tip">
            还没有账号？<a href="javascript:;" @click="goRegister">立即注册</a>
          </p>
        </div>
      </template>
    </el-dialog>

    <footer class="footer">
      <p>MangaNovel — 漫画小说阅读平台</p>
      <p class="footer-sub">© 2026 All Rights Reserved</p>
    </footer>

  </div>
</template>

<style scoped>

/* ========================================== */
/*  设计语言提取自 yuriheme-manga-library      */
/*  配色: var(--accent)(紫) var(--bg-page)(灰蓝底)         */
/*  以下样式仅作用于本页面，不影响其他页面      */
/* ========================================== */

.yhl-page {
  min-height: 100vh;
  background: var(--bg-page);
}

/* === 侧边导航（圆圈样式，复用原站设计） === */
.side-nav {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  padding: 10px;
  z-index: 200;
  font-size: 0.8em;
}
.side-nav ul { list-style: none; padding: 0; margin: 0; }
.side-nav li { margin-bottom: 10px; }
.side-nav a {
  text-decoration: none;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(141, 86, 218, 0.8);
  transition: all 0.3s ease;
  overflow: hidden;
  position: relative;
  writing-mode: vertical-rl;
}
.side-nav a:hover {
  opacity: 0.6;
}
.side-nav a::before {
  content: attr(data-short-text);
  position: absolute;
  transition: opacity 0.3s ease;
  opacity: 1;
  font-size: 15px;
  letter-spacing: 0;
}
.side-nav a span {
  display: block;
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.3s ease;
  font-size: 13px;
  letter-spacing: 2px;
}
.side-nav a:hover::before { opacity: 0; }
.side-nav a:hover {
  height: 100px;
  width: 40px;
  justify-content: center;
}
.side-nav a:hover span { opacity: 1; }

/* === 顶部导航 === */
.header {
  position: fixed;
  top: 0; left: 0; right: 0;
  z-index: 100;
  height: 100px;
  transition: all 0.5s ease;
  background: transparent;
}
.header--scrolled {
  height: 60px;
  background: var(--bg-nav);
  box-shadow: var(--shadow-sm);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 100%;
  padding: 0 24px 20px;
}
.header--scrolled .header-inner {
  align-items: center;
  padding-bottom: 0;
}
.header-logo { transition: all 0.5s ease; }
.header-logo a {
  display: block;
  width: 180px;
  transition: width 0.5s ease;
}
.header-logo--small a { width: 110px; }
.logo-svg { width: 100%; height: auto; display: block; }
.logo-path { fill: var(--accent); }
.header:not(.header--scrolled) .logo-path { fill: #fff; }

.header-nav {
  display: flex;
  align-items: center;
  gap: 24px;
}
.header-nav a {
  color: #fff;
  text-decoration: none;
  font-size: 15px;
  letter-spacing: 1px;
}
.header-nav a:hover { color: rgba(255,255,255,0.7); }
.header--scrolled .header-nav a { color: var(--text-secondary); }
.header--scrolled .header-nav a:hover { color: var(--accent); }
.btn-login {
  background: #5e5477 !important;
  border-color: #5e5477 !important;
  color: #fff !important;
}
.btn-login:hover { background: rgba(94,84,119,0.7) !important; }

.header-avatar { cursor: pointer; display: flex; align-items: center; transition: transform 0.2s; }
.header-avatar:hover { transform: scale(1.15); }
.avatar-small { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; border: 2px solid rgba(255,255,255,0.4); transition: border-color 0.2s; }
.header-avatar:hover .avatar-small { border-color: var(--accent); }
.avatar-placeholder-svg { width: 32px; height: 32px; border-radius: 50%; background: rgba(255,255,255,0.15); display: flex; align-items: center; justify-content: center; }
.header--scrolled .avatar-placeholder-svg { background: rgba(0,0,0,0.08); }

.btn-logout {
  background: transparent !important;
  border-color: rgba(255,255,255,0.6) !important;
  color: #fff !important;
}
.btn-logout:hover { border-color: #fff !important; }
.header--scrolled .btn-logout {
  border-color: var(--border-light) !important;
  color: var(--text-secondary) !important;
}

/* === 横幅 === */
.hero {
  position: relative;
  height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-light) 40%, #da56c3 100%);
}
.hero-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at 20% 50%, rgba(255,255,255,0.15) 0%, transparent 60%);
}
.hero-content {
  position: relative; z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
}
.hero-fox {
  position: absolute;
  top: 100px;
  left: -560px;
  max-height: 270px;
  width: auto;
  object-fit: contain;
}
.hero-search-box {
  position: absolute;
  bottom: 60px;
  left: 24px;
  width: 400px;
}
.search-wrapper {
  position: relative;
}
.hero-search-input :deep(.el-input__wrapper) {
  border-radius: 24px;
  border: none;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  padding: 4px 50px 4px 20px;
}
.search-icon-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  cursor: pointer;
  color: var(--accent);
  font-size: 20px;
  z-index: 10;
  transition: opacity 0.2s;
}
.search-icon-btn:hover {
  opacity: 0.7;
}

/* === 主内容 === */
.main-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 50px 24px 60px;
}

/* 区块 */
.sec-block {
  margin-bottom: 60px;
}
.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 28px;
}
.sec-title {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.sec-title-en {
  font-family: 'Rajdhani', 'Arial', sans-serif;
  font-size: 24px;
  font-weight: 600;
  color: var(--accent);
  text-transform: uppercase;
  letter-spacing: 3px;
}
.sec-title-cn {
  font-size: 15px;
  color: #888;
  letter-spacing: 2px;
}
.sec-more {
  font-size: 14px;
  color: #888;
  text-decoration: none;
  letter-spacing: 1px;
  transition: color 0.3s;
}
.sec-more:hover {
  color: var(--accent);
}

/* 卡片网格 */
.sec-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 24px 20px;
}
@media (max-width: 1024px) {
  .sec-grid { grid-template-columns: repeat(3, 1fr); }
  .hero-title { font-size: 38px; }
}
@media (max-width: 480px) {
  .sec-grid { grid-template-columns: repeat(2, 1fr); gap: 14px 10px; }
  .hero-title { font-size: 28px; }
}

/* 卡片 */
.card-item {
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.card-item:hover { transform: translateY(-6px); }
.card-cover {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 0 20px rgba(0,0,0,0.15);
  background: #d5dde5;
}
.card-bg {
  width: 100%; height: 100%;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.4s ease;
  position: relative;
}
.card-bg::before {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(141,86,218,0.7), rgba(218,86,195,0.7));
  opacity: 0;
  transition: opacity 0.35s ease;
}
.card-item:hover .card-bg::before { opacity: 1; }
.card-bg::after {
  content: '';
  position: absolute; bottom: 0; left: 0; right: 0;
  height: 50px;
  background: linear-gradient(var(--accent), #da56c3);
  opacity: 0;
  transition: opacity 0.35s ease;
}
.card-item:hover .card-bg::after { opacity: 1; }
.card-item:hover .card-bg { transform: scale(1.03); }
.card-cover-text {
  font-size: 13px;
  color: #999;
  padding: 12px;
  text-align: center;
  line-height: 1.5;
}
.card-info { padding: 10px 2px 0; }
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-author { font-size: 12px; color: #999; }

.favorite-login-tip, .favorite-empty {
  text-align: center; padding: 48px 0; color: #999;
}
.favorite-login-tip p, .favorite-empty p { font-size: 14px; margin-bottom: 8px; }
.favorite-empty span { font-size: 12px; color: #bbb; }

/* === 底部 === */
.footer {
  background: #2a2a2a;
  text-align: center;
  padding: 40px 20px;
  color: #aaa;
  font-size: 14px;
}
.footer-sub { margin-top: 6px; font-size: 12px; color: #777; }

/* === 响应式 === */
@media (max-width: 896px) {
  .side-nav { display: none; }
  .header { height: 70px; }
  .header--scrolled { height: 50px; }
  .hero { height: 300px; }
  .hero-title { font-size: 36px; letter-spacing: 4px; }
  .hero-search-box { width: 90vw; }
  .header-logo a { width: 130px; }
  .header-logo--small a { width: 90px; }
  .main-content { padding: 32px 16px 40px; }
  .sec-block { margin-bottom: 44px; }
}
@media (max-width: 420px) {
  .header-nav a { display: none; }
  .hero { height: 240px; }
}

/* === 登录弹窗 === */
.dialog-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.btn-login-submit {
  width: 100%;
}
.dialog-register-tip {
  margin-top: 14px;
  font-size: 13px;
  color: #999;
}
.dialog-register-tip a {
  color: var(--accent);
  cursor: pointer;
}
</style>
