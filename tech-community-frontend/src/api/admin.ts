import { request } from './http'
import type {
  AdminArticleDetail,
  AdminArticleListItem,
  AdminCategoryItem,
  AdminCategorySaveReq,
  AdminCommentListItem,
  AdminDashboardSummary,
  AdminUserListItem,
  PageResult
} from './types'

export function getAdminDashboardSummary() {
  return request<AdminDashboardSummary>({
    url: '/admin/dashboard/summary',
    method: 'get'
  })
}

export function getAdminArticles(params: {
  keyword?: string
  status?: number
  categoryId?: number
  authorName?: string
  page?: number
  size?: number
}) {
  return request<PageResult<AdminArticleListItem>>({
    url: '/admin/articles',
    method: 'get',
    params
  })
}

export function getAdminArticleDetail(articleId: number) {
  return request<AdminArticleDetail>({
    url: `/admin/articles/${articleId}`,
    method: 'get'
  })
}

export function deleteAdminArticle(articleId: number) {
  return request<boolean>({
    url: `/admin/articles/${articleId}`,
    method: 'delete'
  })
}

export function getAdminComments(params: {
  status?: number
  articleId?: number
  keyword?: string
  page?: number
  size?: number
}) {
  return request<PageResult<AdminCommentListItem>>({
    url: '/admin/comments',
    method: 'get',
    params
  })
}

export function approveAdminComment(commentId: number) {
  return request<boolean>({
    url: `/admin/comments/${commentId}/approve`,
    method: 'post'
  })
}

export function rejectAdminComment(commentId: number, reason: string) {
  return request<boolean>({
    url: `/admin/comments/${commentId}/reject`,
    method: 'post',
    data: { reason }
  })
}

export function deleteAdminComment(commentId: number) {
  return request<boolean>({
    url: `/admin/comments/${commentId}`,
    method: 'delete'
  })
}

export function getAdminCategories() {
  return request<AdminCategoryItem[]>({
    url: '/admin/categories',
    method: 'get'
  })
}

export function createAdminCategory(data: AdminCategorySaveReq) {
  return request<AdminCategoryItem>({
    url: '/admin/categories',
    method: 'post',
    data
  })
}

export function updateAdminCategory(categoryId: number, data: AdminCategorySaveReq) {
  return request<AdminCategoryItem>({
    url: `/admin/categories/${categoryId}`,
    method: 'put',
    data
  })
}

export function updateAdminCategoryStatus(categoryId: number, status: number) {
  return request<boolean>({
    url: `/admin/categories/${categoryId}/status`,
    method: 'patch',
    data: { status }
  })
}

export function getAdminUsers(params: {
  username?: string
  userRole?: number
  page?: number
  size?: number
}) {
  return request<PageResult<AdminUserListItem>>({
    url: '/admin/users',
    method: 'get',
    params
  })
}

export function updateAdminUserRole(userId: number, userRole: number) {
  return request<boolean>({
    url: `/admin/users/${userId}/role`,
    method: 'patch',
    data: { userRole }
  })
}
