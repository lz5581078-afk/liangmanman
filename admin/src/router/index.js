import { createRouter, createWebHistory } from 'vue-router'
import { session, isSuperAdmin } from '@/utils/auth'
import Layout from '@/layout/Layout.vue'

/** 菜单按 children 顺序显示；meta.group 用于菜单分组，meta.superOnly 仅 ADMIN 可见 */
export const menuRoutes = [
  { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '数据看板', icon: 'DataAnalysis' } },
  { path: 'stores', name: 'stores', component: () => import('@/views/stores/StoreList.vue'), meta: { title: '店铺管理', icon: 'Shop', group: '店铺与商品' } },
  { path: 'licenses', name: 'licenses', component: () => import('@/views/stores/LicenseList.vue'), meta: { title: '证件审核', icon: 'Postcard', group: '店铺与商品' } },
  { path: 'products', name: 'products', component: () => import('@/views/products/ProductList.vue'), meta: { title: '商品管理', icon: 'Goods', group: '店铺与商品' } },
  { path: 'standard-products', name: 'standard-products', component: () => import('@/views/products/StandardProductList.vue'), meta: { title: '商品库', icon: 'Collection', group: '店铺与商品' } },
  { path: 'search-words', name: 'search-words', component: () => import('@/views/products/SearchWordList.vue'), meta: { title: '搜索同义词', icon: 'Search', group: '店铺与商品' } },
  { path: 'categories', name: 'categories', component: () => import('@/views/products/CategoryList.vue'), meta: { title: '分类管理', icon: 'Menu', group: '店铺与商品' } },
  { path: 'orders', name: 'orders', component: () => import('@/views/orders/OrderList.vue'), meta: { title: '订单管理', icon: 'Tickets', group: '交易' } },
  { path: 'group-buys', name: 'group-buys', component: () => import('@/views/orders/GroupBuyList.vue'), meta: { title: '凑单', icon: 'UserFilled', group: '交易' } },
  { path: 'finance', name: 'finance', component: () => import('@/views/finance/Finance.vue'), meta: { title: '资金', icon: 'Wallet', group: '交易' } },
  { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewList.vue'), meta: { title: '评价', icon: 'ChatLineSquare', group: '用户与内容' } },
  { path: 'wanted', name: 'wanted', component: () => import('@/views/WantedList.vue'), meta: { title: '求购', icon: 'Search', group: '用户与内容' } },
  { path: 'users', name: 'users', component: () => import('@/views/UserList.vue'), meta: { title: '用户', icon: 'User', group: '用户与内容' } },
  { path: 'regions', name: 'regions', component: () => import('@/views/RegionList.vue'), meta: { title: '开通区域', icon: 'Location', group: '平台设置' } },
  { path: 'banners', name: 'banners', component: () => import('@/views/BannerList.vue'), meta: { title: '轮播图', icon: 'Picture', group: '平台设置' } },
  { path: 'admins', name: 'admins', component: () => import('@/views/AdminList.vue'), meta: { title: '管理员', icon: 'Avatar', group: '平台设置', superOnly: true } },
  { path: 'logs', name: 'logs', component: () => import('@/views/OperationLogList.vue'), meta: { title: '操作日志', icon: 'Document', group: '平台设置' } },
  { path: 'notify-logs', name: 'notify-logs', component: () => import('@/views/NotifyLogList.vue'), meta: { title: '通知记录', icon: 'Bell', group: '平台设置' } }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录', public: true } },
    { path: '/', component: Layout, redirect: '/dashboard', children: menuRoutes },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!session.token) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.superOnly && !isSuperAdmin()) return { name: 'dashboard' }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 粮满满运营后台` : '粮满满运营后台'
})

export default router
