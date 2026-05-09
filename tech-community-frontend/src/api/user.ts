import { request } from './http'
import type {
  ArticleListItem,
  FollowAction,
  FollowStats,
  PageResult,
  UserFollowListItem,
  UserProfile,
  UserProfileUpdateReq
} from './types'

export function getUserProfile(userId: number) {
  return request<UserProfile>({
    url: `/user/${userId}/profile`,
    method: 'get'
  })
}

export function updateCurrentUserProfile(data: UserProfileUpdateReq) {
  return request<void>({
    url: '/user/profile',
    method: 'put',
    data
  })
}

export function listUserArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/articles`,
    method: 'get',
    params: { page, size }
  })
}

export function listUserCollectionArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/collections/articles`,
    method: 'get',
    params: { page, size }
  })
}

export function listUserLikeArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/likes/articles`,
    method: 'get',
    params: { page, size }
  })
}

export function followUser(targetUserId: number) {
  return request<FollowAction>({
    url: '/user/follow',
    method: 'post',
    params: { targetUserId }
  })
}

export function unfollowUser(targetUserId: number) {
  return request<FollowAction>({
    url: '/user/unfollow',
    method: 'post',
    params: { targetUserId }
  })
}

export function getFollowStatus(targetUserId: number) {
  return request<FollowAction>({
    url: '/user/follow/status',
    method: 'get',
    params: { targetUserId }
  })
}

export function getFollowStats(userId: number) {
  return request<FollowStats>({
    url: `/user/${userId}/follow/stats`,
    method: 'get'
  })
}

export function getFollowList(userId: number, page = 1, size = 10) {
  return request<PageResult<UserFollowListItem>>({
    url: `/user/${userId}/follows`,
    method: 'get',
    params: { page, size }
  })
}

export function getFanList(userId: number, page = 1, size = 10) {
  return request<PageResult<UserFollowListItem>>({
    url: `/user/${userId}/fans`,
    method: 'get',
    params: { page, size }
  })
}
