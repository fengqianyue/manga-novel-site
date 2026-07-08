import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const adminToken = ref(localStorage.getItem('admin_token') || '')
  const adminUser = ref(JSON.parse(localStorage.getItem('admin_user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdminAuth = computed(() => !!adminToken.value)

  function setLogin(data) {
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
  }

  function setAdminLogin(data) {
    adminToken.value = data.token
    adminUser.value = data.user
    localStorage.setItem('admin_token', data.token)
    localStorage.setItem('admin_user', JSON.stringify(data.user))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_user')
  }

  function adminLogout() {
    adminToken.value = ''
    adminUser.value = null
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_user')
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, adminToken, adminUser, isLoggedIn, isAdminAuth,
           setLogin, setAdminLogin, logout, adminLogout }
})
