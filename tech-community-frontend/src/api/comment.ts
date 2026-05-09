import { request } from './http'
import type { CommentLike, CommentPage, CommentPublishReq } from './types'

export function publishComment(data: CommentPublishReq) {
  return request<number>({
    url: '/comment/publish',
    method: 'post',
    data
  })
}

export function listArticleComments(articleId: number, page = 1, size = 20) {
  return request<CommentPage>({
    url: `/comment/article/${articleId}/list`,
    method: 'get',
    params: { page, size }
  })
}

export function listCommentReplies(commentId: number, page = 1, size = 20) {
  return request<CommentPage>({
    url: `/comment/${commentId}/replies`,
    method: 'get',
    params: { page, size }
  })
}

export function likeComment(commentId: number) {
  return request<CommentLike>({
    url: '/comment/like',
    method: 'post',
    params: { commentId }
  })
}

export function deleteComment(commentId: number) {
  return request<boolean>({
    url: `/comment/${commentId}`,
    method: 'delete'
  })
}
