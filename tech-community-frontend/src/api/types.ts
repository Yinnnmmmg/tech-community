export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface CursorPageResult<T> {
  nextCursor: number | null
  list: T[]
}

export interface User {
  id: number
  username: string
  userRole?: number
  photo?: string
  position?: string
  company?: string
  profile?: string
  thirdAccountId?: string
  loginType?: number
}

export interface ArticleCategory {
  id: number
  name: string
  sort: number
}

export interface ArticleAttachment {
  attachmentId: number
  fileName: string
  url: string
  contentType: string
  fileSize: number
}

export interface ArticleListItem {
  articleId: number
  authorId?: number
  title: string
  summary?: string
  categoryId?: number
  categoryName?: string
  authorName?: string
  createTime?: string
  coverUrl?: string
  likeCount: number
  collectionCount: number
  commentCount: number
  attachmentCount: number
  hasAttachment: boolean
}

export interface ArticleSearchHighlight {
  id: number
  title: string
  highlightedTitle?: string
  highlightedContent?: string
  author?: string
  authorId?: number
  tags?: string[]
  score?: number
  publishTime?: number
}

export interface ArticleDetail {
  articleId: number
  title: string
  content: string
  authorId?: number
  authorName?: string
  categoryId?: number
  categoryName?: string
  createTime?: string
  coverUrl?: string
  likeCount: number
  collectionCount: number
  commentCount: number
  likeStat: number
  collectionStat: number
  attachments: ArticleAttachment[]
}

export interface ArticlePostReq {
  title: string
  content: string
  categoryId: number
  attachmentIds: number[]
}

export interface ArticleLike {
  likeCount: number
  likeStat: number
}

export interface ArticleCollect {
  collectionCount: number
  collectionStat: number
}

export interface CommentPublishReq {
  articleId: number
  content: string
  parentCommentId?: number | null
  replyToCommentId?: number | null
  replyToUserId?: number | null
}

export interface CommentListItem {
  commentId: number
  articleId: number
  userId: number
  username?: string
  content: string
  status?: number
  rejectReason?: string
  likeCount: number
  likeStat: number
  replyCount: number
  replyToUserId?: number | null
  replyToUsername?: string | null
  createTime?: string
  canDelete?: boolean
}

export interface CommentPage {
  publicPage: PageResult<CommentListItem>
  mine: CommentListItem[]
}

export interface CommentLike {
  likeCount: number
  likeStat: number
}

export interface FollowAction {
  targetUserId: number
  followed: boolean
}

export interface FollowStats {
  followCount: number
  fanCount: number
  followed: boolean
}

export interface UserFollowListItem {
  userId: number
  username?: string
  photo?: string
  position?: string
  company?: string
  profile?: string
  followed: boolean
}

export interface UserProfile {
  userId: number
  username?: string
  userRole?: number
  photo?: string
  position?: string
  company?: string
  profile?: string
  articleCount: number
  followCount: number
  fanCount: number
  collectionCount: number
  likeCount: number
  followed: boolean
  self: boolean
  createTime?: string
}

export interface UserProfileUpdateReq {
  username?: string
  photo?: string
  position?: string
  company?: string
  profile?: string
}

export interface ChangePasswordReq {
  oldPassword: string
  newPassword: string
}

export interface Reference {
  articleId: number
  title: string
}

export interface ChatStream {
  content?: string
  isEnd?: boolean
  references?: Reference[]
  errorCode?: number
  errorMessage?: string
}

export interface AdminDashboardSummary {
  articleCount: number
  pendingArticleCount: number
  commentCount: number
  pendingCommentCount: number
  userCount: number
  adminCount: number
  recentArticles: AdminArticleListItem[]
  recentComments: AdminCommentListItem[]
}

export interface AdminArticleListItem {
  articleId: number
  authorId: number
  authorName?: string
  title: string
  summary?: string
  categoryId?: number
  categoryName?: string
  status: number
  coverUrl?: string
  likeCount: number
  collectionCount: number
  commentCount: number
  createTime?: string
  updateTime?: string
}

export interface AdminArticleDetail {
  articleId: number
  authorId: number
  authorName?: string
  title: string
  content: string
  summary?: string
  categoryId?: number
  categoryName?: string
  status: number
  coverUrl?: string
  likeCount: number
  collectionCount: number
  commentCount: number
  createTime?: string
  updateTime?: string
  attachments: ArticleAttachment[]
}

export interface AdminCommentListItem {
  commentId: number
  articleId: number
  articleTitle?: string
  userId: number
  username?: string
  content: string
  status: number
  rejectReason?: string
  parentCommentId?: number | null
  replyToCommentId?: number | null
  replyToUserId?: number | null
  replyToUsername?: string | null
  likeCount: number
  replyCount: number
  createTime?: string
  updateTime?: string
}

export interface AdminCategoryItem {
  id: number
  name: string
  sort: number
  status: number
  articleCount: number
  createTime?: string
  updateTime?: string
}

export interface AdminCategorySaveReq {
  name: string
  sort: number
  status: number
}

export interface AdminUserListItem {
  userId: number
  username: string
  userRole: number
  photo?: string
  position?: string
  company?: string
  profile?: string
  createTime?: string
}
