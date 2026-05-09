import {
  FileText,
  FolderTree,
  LayoutDashboard,
  MessageSquareText,
  Settings,
  Users
} from 'lucide-vue-next'
import { markRaw } from 'vue'

export const adminMenus = [
  { label: '仪表盘', to: '/admin/dashboard', icon: markRaw(LayoutDashboard) },
  { label: '文章管理', to: '/admin/articles', icon: markRaw(FileText) },
  { label: '评论管理', to: '/admin/comments', icon: markRaw(MessageSquareText) },
  { label: '分类管理', to: '/admin/categories', icon: markRaw(FolderTree) },
  { label: '用户管理', to: '/admin/users', icon: markRaw(Users) },
  { label: '系统设置', to: '/admin/settings', icon: markRaw(Settings) }
]
