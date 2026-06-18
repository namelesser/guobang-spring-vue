import { createPinia } from 'pinia'

const pinia = createPinia()

export default pinia
export { useAppStore } from './modules/app'
