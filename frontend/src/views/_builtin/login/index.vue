<script setup lang="ts">
import PwdLogin from './modules/pwd-login.vue';
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-#f5f6f8 px-16px">
    <NCard :bordered="false" class="w-full max-w-420px rd-8px shadow-sm">
      <div class="flex flex-col gap-20px">
        <div class="flex flex-col gap-8px text-center">
          <SystemLogo class="mx-auto size-56px" />
          <h1 class="text-24px font-600 text-primary">国邦运输管理系统</h1>
          <p class="text-14px text-#666">请输入登录密码</p>
        </div>
        <PwdLogin />
      </div>
    </NCard>
  </div>
</template>
