<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import { register } from '@/api/user'

const router = useRouter()

const form = ref({ username: '', email: '', password: '', confirmPassword: '' })
const loading = ref(false)
const registered = ref(false)

function goLogin() {
  router.push({ path: '/' })
}

async function doRegister() {
  if (!form.value.username || !form.value.email || !form.value.password) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await register({
      username: form.value.username,
      email: form.value.email,
      password: form.value.password,
    })
    registered.value = true
    ElMessage.success('注册成功，即将跳转至登录...')
    setTimeout(() => {
      router.push({ path: '/' })
    }, 2000)
  } catch {
    // 错误已在 request 拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <h2 class="register-title">创建账号</h2>

      <template v-if="!registered">
        <el-form :model="form" label-position="top" @keyup.enter="doRegister">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名"
              maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="请输入邮箱地址" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="请输入密码"
              show-password maxlength="30" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码"
              show-password maxlength="30" />
          </el-form-item>
        </el-form>
        <el-button type="primary" class="btn-register" :loading="loading" @click="doRegister">
          注 册
        </el-button>
        <p class="login-tip">
          已有账号？<a href="javascript:;" @click="goLogin">去登录</a>
        </p>
      </template>

      <template v-else>
        <div class="register-success">
          <el-icon class="success-icon" color="#67c23a" :size="56"><CircleCheckFilled /></el-icon>
          <p>注册成功！</p>
          <p class="redirect-text">即将跳转至首页登录...</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f3f7;
}
.register-card {
  background: #fff;
  padding: 48px 40px 36px;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
  width: 420px;
  max-width: 90vw;
}
.register-title {
  text-align: center;
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin-bottom: 32px;
}
.btn-register {
  width: 100%;
  margin-top: 8px;
}
.login-tip {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: #999;
}
.login-tip a {
  color: #8d56da;
  cursor: pointer;
}
.register-success {
  text-align: center;
  padding: 20px 0;
}
.register-success p {
  font-size: 16px;
  color: #333;
  margin-top: 16px;
}
.redirect-text {
  font-size: 13px !important;
  color: #999 !important;
  margin-top: 8px !important;
}
</style>
