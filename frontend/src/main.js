import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { allowMultipleToast, setToastDefaultOptions } from 'vant'
import App from './App.vue'
import router from './router'

// Vant 组件样式
import 'vant/lib/index.css'

// 自定义样式
import './styles/main.css'

// Toast 默认深色底白字，避免成功提示白底看不清
setToastDefaultOptions({
  duration: 2000,
  forbidClick: false
})
setToastDefaultOptions('text', {
  className: 'xbrowse-toast'
})
setToastDefaultOptions('success', {
  className: 'xbrowse-toast'
})
setToastDefaultOptions('fail', {
  className: 'xbrowse-toast'
})
allowMultipleToast(false)

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
