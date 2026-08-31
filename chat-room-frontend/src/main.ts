import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './assets/theme.css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// Init theme
import { useThemeStore } from './stores/theme'
const themeStore = useThemeStore()
themeStore.init()

app.mount('#app')
