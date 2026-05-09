import { request } from './http'
import type {
  ArticleAttachment,
  ArticleCategory,
  ArticleCollect,
  ArticleDetail,
  ArticleLike,
  ArticleListItem,
  ArticlePostReq,
  ArticleSearchHighlight,
  CursorPageResult,
  PageResult
} from './types'

export function listArticles(cursor = 0, pageSize = 10, categoryId?: number) {
  return request<CursorPageResult<ArticleListItem>>({
    url: '/article/list',
    method: 'get',
    params: { cursor, pageSize, categoryId }
  })
}

export function searchArticles(keyWord: string, page = 1, size = 10) {
  return request<PageResult<ArticleSearchHighlight>>({
    url: '/article/search',
    method: 'get',
    params: { keyWord, page, size }
  })
}

export function listCategories() {
  return request<ArticleCategory[]>({
    url: '/article/categories',
    method: 'get'
  })
}

export function getArticleDetail(articleId: number) {
  return request<ArticleDetail>({
    url: `/article/detail/${articleId}`,
    method: 'get'
  })
}

export function likeArticle(articleId: number) {
  return request<ArticleLike>({
    url: '/article/like',
    method: 'post',
    params: { articleId }
  })
}

export function collectArticle(articleId: number) {
  return request<ArticleCollect>({
    url: '/article/collect',
    method: 'post',
    params: { articleId }
  })
}

export function publishArticle(data: ArticlePostReq) {
  return request<number>({
    url: '/article/publish',
    method: 'post',
    data
  })
}

export function updateArticle(articleId: number, data: ArticlePostReq) {
  return request<number>({
    url: `/article/${articleId}`,
    method: 'put',
    data
  })
}

export function deleteArticle(articleId: number) {
  return request<boolean>({
    url: `/article/${articleId}`,
    method: 'delete'
  })
}

export function uploadAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<ArticleAttachment>({
    url: '/article/attachment/upload',
    method: 'post',
    data: formData
  })
}
