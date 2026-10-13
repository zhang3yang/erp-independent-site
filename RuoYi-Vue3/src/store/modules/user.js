import router from '@/router'
import cache from '@/plugins/cache'
import { ElMessageBox, } from 'element-plus'
import { login, loginBySms, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from "@/utils/validate"
import useLockStore from '@/store/modules/lock'
import defAva from '@/assets/images/profile.jpg'
import useBusinessShopStore from '@/store/modules/businessShop'

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      platformAdmin: false,
      name: '',
      nickName: '',
      avatar: '',
      shopCode: '',
      shopName: '',
      memberUserCode: undefined,
      tenantSelected: false,
      tenantRoles: [],
      tenantPermissions: [],
      roles: [],
      permissions: []
    }),
    actions: {
      // 登录
      login(userInfo) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            setToken(res.token)
            this.token = res.token
            useLockStore().unlockScreen()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      loginBySms(userInfo) {
        const phonenumber = userInfo.phonenumber.trim()
        const code = userInfo.smsCode
        return new Promise((resolve, reject) => {
          loginBySms(phonenumber, code).then(res => {
            setToken(res.token)
            this.token = res.token
            useLockStore().unlockScreen()
            resolve()
          }).catch(error => reject(error))
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(res => {
            const user = res.user
            let avatar = user.avatar || ""
            if (!isHttp(avatar)) {
              avatar = (isEmpty(avatar)) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.permissions = res.permissions || []
            this.id = user.userCode
            this.platformAdmin = Boolean(res.platformAdmin)
            this.name = user.userName
            this.nickName = user.nickName
            this.avatar = avatar
            this.shopCode = res.shopCode || ''
            this.shopName = res.shopName || ''
            this.memberUserCode = res.memberUserCode
            this.tenantSelected = Boolean(res.tenantSelected)
            this.tenantRoles = res.tenantRoles || []
            this.tenantPermissions = res.tenantPermissions || []
            if (!this.platformAdmin || this.shopCode) useBusinessShopStore().clear()
            if (!this.tenantSelected && !this.roles.includes('admin')) {
              this.permissions = []
            }
            cache.session.set('pwrChrtype', res.pwdChrtype)
            /* 初始密码提示 */
            if(res.isDefaultModifyPwd) {
              ElMessageBox.confirm('您的密码还是初始密码，请修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning', closeOnClickModal: false }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            /* 过期密码提示 */
            if(!res.isDefaultModifyPwd && res.isPasswordExpired) {
              ElMessageBox.confirm('您的密码已过期，请尽快修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning', closeOnClickModal: false }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise((resolve, reject) => {
          logout(this.token).then(() => {
            this.token = ''
            this.platformAdmin = false
            this.roles = []
            this.permissions = []
            this.shopCode = ''
            this.shopName = ''
            this.memberUserCode = undefined
            this.tenantSelected = false
            this.tenantRoles = []
            this.tenantPermissions = []
            useBusinessShopStore().clear()
            removeToken()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      }
    }
  })

export default useUserStore
