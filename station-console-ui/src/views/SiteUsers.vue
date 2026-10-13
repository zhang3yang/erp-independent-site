<!--
  站点用户（2026-09-24）——不在左边菜单里的独立页。

  用户管理原来是管理台左边的一个菜单。那一页全是 ERP 登录名、「ERP 免密」来源、
  「从 ERP 进来时自动落的」说明——海外客户（Esva boots 的站长）进管理台就能看到，
  而这些是我们内部的事。所以菜单拿掉了，入口挪到 ERP「独立站管理」的「站点用户」按钮：

    ERP 点「站点用户」→ 令牌带 scope=users → 站点 SsoController 建会话时打上 userAdmin
    → 302 到这里。没有那个标记的会话打这个地址，路由守卫送回概览，后端用户接口 403。

  不套 AppShell：那个壳的左边菜单、站点状态、客服浮窗在这里都用不上，
  而且壳会去取整份站点档案——这一页只要一张用户表。
  表格本身还是 views/Users.vue，没有复制一份。
-->
<template>
  <div class="su">
    <header class="su-top">
      <span class="su-site">{{ identity.siteName }}</span>
      <span class="su-title">{{ $t('users.standalone.title') }}</span>
      <span class="su-sp"></span>
      <!-- 语言：ERP 员工多半要中文，而这个站的默认界面语言可能是法语 -->
      <el-dropdown @command="onLangCommand">
        <span class="su-lang">
          {{ currentLangLabel }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in LOCALES" :key="item.code" :command="item.code">
              {{ item.label }}
              <el-icon v-if="item.code === locale" class="el-icon--right"><Check /></el-icon>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <router-link class="su-console" to="/overview">{{ $t('users.standalone.toConsole') }}</router-link>
    </header>
    <main class="su-main">
      <users />
    </main>
  </div>
</template>

<script setup name="SiteUsers">
import { computed } from 'vue'
import { ArrowDown, Check } from '@element-plus/icons-vue'
import Users from '@/views/Users.vue'
import { identity } from '@/store/identity'
import { LOCALES, locale, setLocale } from '@/i18n'

const currentLangLabel = computed(() => (LOCALES.find(item => item.code === locale.value) || {}).label || '')

function onLangCommand(code) {
  setLocale(code)
}
</script>

<style scoped>
.su { min-height: 100vh; background: #f0f2f5; }
.su-top {
  display: flex; align-items: center; gap: 14px;
  height: 56px; padding: 0 20px;
  background: #1f2d3d; color: #fff;
}
.su-site { font-weight: 700; font-size: 16px; }
.su-title { color: rgba(255, 255, 255, .7); font-size: 14px; }
.su-sp { flex: 1; }
.su-lang { color: #fff; cursor: pointer; display: inline-flex; align-items: center; }
.su-console { color: #8cc5ff; text-decoration: none; font-size: 14px; }
.su-console:hover { text-decoration: underline; }
.su-main { padding: 16px 20px; }
</style>
