<script setup lang='ts'>
import { defineAsyncComponent, ref } from 'vue'
import { NAvatar } from 'naive-ui'
import { SvgIcon } from '@/components/common'
import { useUserStore } from '@/store'
import defaultAvatar from '@/assets/avatar.jpg'

const Setting = defineAsyncComponent(() => import('@/components/common/Setting/index.vue'))

const userStore = useUserStore()
const showSetting = ref(false)
</script>

<template>
  <footer class="flex items-center gap-2 flex-none px-3 py-3 border-t border-neutral-200/70 dark:border-neutral-800">
    <button class="flex items-center flex-1 min-w-0 gap-2.5 px-1.5 py-1 rounded-lg transition hover:bg-black/5 dark:hover:bg-white/5" @click="showSetting = true">
      <div class="flex-none w-9 h-9 overflow-hidden rounded-full">
        <NAvatar size="large" round :src="userStore.userInfo.avatar || defaultAvatar" />
      </div>
      <div class="flex-1 min-w-0 text-left">
        <div class="flex items-center gap-0.5 text-[13.5px] font-medium text-neutral-800 dark:text-neutral-100">
          <span class="truncate">{{ userStore.userInfo.name ?? 'ChenYouXin' }}</span>
          <SvgIcon icon="ri:arrow-right-s-line" class="flex-none text-base text-neutral-400" />
        </div>
        <div class="mt-0.5 text-[11px] text-neutral-400 truncate">本地运行</div>
      </div>
    </button>
    <button
      class="flex items-center justify-center flex-none w-8 h-8 rounded-lg text-neutral-500 transition hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
      title="设置"
      @click="showSetting = true"
    >
      <SvgIcon icon="ri:settings-4-line" class="text-xl" />
    </button>

    <Setting v-if="showSetting" v-model:visible="showSetting" />
  </footer>
</template>
