import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoveView from '../views/LoveView.vue'
import ManusView from '../views/ManusView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/love', name: 'love', component: LoveView },
  { path: '/manus', name: 'manus', component: ManusView },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
