import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', () => {
  // Glass morphism is the only theme — always active
  function init() {
    document.documentElement.classList.add('dark')
  }
  return { init }
})
