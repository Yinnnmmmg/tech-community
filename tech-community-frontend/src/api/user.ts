import { request } from './http'
import type {
  ArticleListItem,
  ChangePasswordReq,
  FollowAction,
  FollowStats,
  PageResult,
  UserFollowListItem,
  UserProfile,
  UserProfileUpdateReq
} from './types'
import { resolveAssetUrl } from '@/utils/asset'

function normalizeUserProfile(profile: UserProfile): UserProfile {
  return {
    ...profile,
    photo: resolveAssetUrl(profile.photo)
  }
}

function normalizeFollowUser(user: UserFollowListItem): UserFollowListItem {
  return {
    ...user,
    photo: resolveAssetUrl(user.photo)
  }
}

function normalizeArticleListItem(article: ArticleListItem): ArticleListItem {
  return {
    ...article,
    coverUrl: resolveAssetUrl(article.coverUrl)
  }
}

function normalizePageResult<T>(page: PageResult<T>, mapper: (item: T) => T): PageResult<T> {
  return {
    ...page,
    records: page.records.map(mapper)
  }
}

export function getUserProfile(userId: number) {
  return request<UserProfile>({
    url: `/user/${userId}/profile`,
    method: 'get'
  }).then(normalizeUserProfile)
}

export function updateCurrentUserProfile(data: UserProfileUpdateReq) {
  return request<void>({
    url: '/user/profile',
    method: 'put',
    data
  })
}

export function changePassword(data: ChangePasswordReq) {
  return request<void>({
    url: '/user/password',
    method: 'put',
    data
  })
}

export function listUserArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/articles`,
    method: 'get',
    params: { page, size }
  }).then((result) => normalizePageResult(result, normalizeArticleListItem))
}

export function listUserCollectionArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/collections/articles`,
    method: 'get',
    params: { page, size }
  }).then((result) => normalizePageResult(result, normalizeArticleListItem))
}

export function listUserLikeArticles(userId: number, page = 1, size = 10) {
  return request<PageResult<ArticleListItem>>({
    url: `/user/${userId}/likes/articles`,
    method: 'get',
    params: { page, size }
  }).then((result) => normalizePageResult(result, normalizeArticleListItem))
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
  }).then((result) => normalizePageResult(result, normalizeFollowUser))
}

export function getFanList(userId: number, page = 1, size = 10) {
  return request<PageResult<UserFollowListItem>>({
    url: `/user/${userId}/fans`,
    method: 'get',
    params: { page, size }
  }).then((result) => normalizePageResult(result, normalizeFollowUser))
}
