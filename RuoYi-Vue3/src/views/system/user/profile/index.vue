<template>
   <div class="app-container">
      <el-row :gutter="20">
         <el-col :span="6" :xs="24">
            <el-card class="box-card">
               <template v-slot:header>
                 <div class="clearfix">
                   <span>个人信息</span>
                 </div>
               </template>
               <div>
                     <div class="text-center">
                     <userAvatar v-if="state.canEditProfile" />
                  </div>
                  <ul class="list-group list-group-striped">
                     <li class="list-group-item">
                        <svg-icon icon-class="phone" />手机号码
                        <div class="pull-right">{{ state.user.phonenumber }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="people" />性别
                        <div class="pull-right">
                           <dict-tag :options="sys_user_sex" :value="state.user.sex" />
                        </div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="tree" />所属部门
                        <div class="pull-right" v-if="state.user.dept">{{ state.user.dept.deptName }} / {{ state.postGroup }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="peoples" />所属角色
                        <div class="pull-right">{{ state.roleGroup }}</div>
                     </li>
                     <li class="list-group-item">
                        <svg-icon icon-class="date" />创建日期
                        <div class="pull-right">{{ state.user.createTime }}</div>
                     </li>
                  </ul>
               </div>
            </el-card>
         </el-col>
         <el-col :span="18" :xs="24">
            <el-card>
               <template v-slot:header>
                 <div class="clearfix">
                   <span>基本资料</span>
                   <el-button v-if="!pwdEditing" class="pull-right" type="primary" link icon="Edit" @click="pwdEditing = true">编辑</el-button>
                 </div>
               </template>
               <ul v-if="!pwdEditing" class="list-group list-group-striped">
                  <li class="list-group-item">
                     <svg-icon icon-class="password" />登录密码
                     <div class="pull-right">********</div>
                  </li>
               </ul>
               <resetPwd v-else @close="pwdEditing = false" />
            </el-card>
         </el-col>
      </el-row>
   </div>
</template>

<script setup name="Profile">
import userAvatar from "./userAvatar"
import resetPwd from "./resetPwd"
import { getUserProfile } from "@/api/system/user"

const { sys_user_sex } = useDict("sys_user_sex")

const route = useRoute()
const pwdEditing = ref(false)
const state = reactive({
  user: {},
  roleGroup: {},
  postGroup: {},
  canEditProfile: false
})

function getUser() {
  getUserProfile().then(response => {
    state.user = response.data
    state.roleGroup = response.roleGroup
    state.postGroup = response.postGroup
    state.canEditProfile = response.canEditProfile === true
  })
}

onMounted(() => {
  const activeTab = route.params && route.params.activeTab
  if (activeTab === "resetPwd") {
    pwdEditing.value = true
  }
  getUser()
})
</script>
