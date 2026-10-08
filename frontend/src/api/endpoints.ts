import { del, download, get, okOrNoContent, patch, post, put, toSearchParams } from './client'

import type {
  AdminPageQuery,
  AdminUserQuery,
  AdminUserResponse,
  AiConfigurationResponse,
  AnalyticsResponse,
  AnalyticsSummary,
  AskRequest,
  AskResponse,
  AuditLogResponse,
  AuthResponse,
  BalanceScore,
  BalanceWeightsRequest,
  BudgetRequest,
  BudgetResponse,
  BulkTaskRequest,
  BulkTaskResponse,
  CalendarFeed,
  CategoryTotal,
  ChatRequest,
  ChatResponse,
  ChangePasswordRequest,
  CompleteTaskRequest,
  ConfirmGoalProposalRequest,
  ConfirmRoadmapRequest,
  ConversationDetail,
  ConversationSummary,
  CountResponse,
  DashboardResponse,
  DeleteAccountRequest,
  DependencyRequest,
  DocumentListResponse,
  DocumentResponse,
  ErrorLogResponse,
  EventMoveRequest,
  EventRequest,
  EventResponse,
  ExportResponse,
  FocusCompleteRequest,
  FocusSessionRequest,
  FocusSessionResponse,
  FocusStats,
  ForgotPasswordRequest,
  GoalProgressUpdate,
  GoalQuery,
  GoalRequest,
  GoalResponse,
  GoalSummary,
  GraphEdgeRequest,
  GraphNeighbourhoodResponse,
  GraphNodeRequest,
  GraphEdge,
  GraphNode,
  GraphNodeType,
  GraphRelation,
  GraphResponse,
  FinanceOverview,
  TodayResponse,
  NotificationResponse,
  DailyStudyPoint,
  HabitLogRequest,
  HabitLogResponse,
  HabitLogToggleRequest,
  HabitRequest,
  HabitResponse,
  HabitStats,
  HabitTrend,
  InsightDismissRequest,
  InsightGenerationResponse,
  InsightQuery,
  InsightResponse,
  JournalRequest,
  JournalResponse,
  JournalStats,
  KnowledgeSearchResponse,
  LearningGoalRequest,
  LearningGoalResponse,
  LearningSessionResponse,
  LearningStats,
  LoginRequest,
  MarkedCountResponse,
  MaterialiseResponse,
  MilestoneRequest,
  MilestoneResponse,
  MonthlyFinanceSummary,
  MonthlyPoint,
  NotificationPage,
  NotificationQuery,
  NotificationPreferenceRequest,
  NotificationPreferenceResponse,
  OnboardingRequest,
  OnboardingState,
  PageResponse,
  PlanDayApplyRequest,
  PlanDayRequest,
  PlanDayResponse,
  PositionedTask,
  PreferenceResponse,
  PredictionHistoryQuery,
  PredictionResponse,
  ProviderStatus,
  QuickCreateRequest,
  RecommendationsResponse,
  RegenerationResponse,
  RegisterRequest,
  RemovedResponse,
  RenameConversationRequest,
  ResetPasswordRequest,
  ResourceRequest,
  ResourceResponse,
  SavingsGoalRequest,
  SavingsGoalResponse,
  SavedItemRequest,
  SavedItemResponse,
  SearchResponse,
  SessionListResponse,
  SettingRequest,
  SettingResponse,
  SkillRequest,
  SkillResponse,
  SpendingPattern,
  StatusUpdateRequest,
  StudySessionRequest,
  SystemStats,
  TaskBoardColumn,
  TaskQuery,
  TaskRequest,
  TaskResponse,
  TopicRequest,
  TopicResponse,
  TransactionQuery,
  TransactionRequest,
  TransactionResponse,
  UnreadCountResponse,
  UpdatePreferenceRequest,
  UpdateProfileRequest,
  UserPrivacyMetadata,
  UserResponse,
  UserRoleRequest,
  UserStatusRequest,
  VerifyEmailRequest,
} from '@/types/api'

export const authApi = {
  register: (body: RegisterRequest) => post<AuthResponse>('/api/auth/register', body),
  login: (body: LoginRequest) => post<AuthResponse>('/api/auth/login', body),
  refresh: (refreshToken: string) => post<AuthResponse>('/api/auth/refresh', { refreshToken }),
  logout: (body?: { refreshToken?: string; allDevices?: boolean }) =>
    post<CountResponse>('/api/auth/logout', body),
  sessions: (currentRefreshToken?: string) =>
    get<SessionListResponse>('/api/auth/sessions', { currentRefreshToken }),
  revokeSession: (sessionId: string) => del<CountResponse>(`/api/auth/sessions/${sessionId}`),
  forgotPassword: (body: ForgotPasswordRequest) =>
    post<CountResponse>('/api/auth/forgot-password', body),
  resetPassword: (body: ResetPasswordRequest) => post<CountResponse>('/api/auth/reset-password', body),
  verifyEmail: (body: VerifyEmailRequest) => post<CountResponse>('/api/auth/verify-email', body),
  resendVerification: () => post<CountResponse>('/api/auth/resend-verification'),
  changePassword: (body: ChangePasswordRequest) => post<CountResponse>('/api/auth/change-password', body),
  me: () => get<UserResponse>('/api/auth/me'),
  updateProfile: (body: UpdateProfileRequest) => put<UserResponse>('/api/auth/me', body),
  deleteAccount: (body: DeleteAccountRequest) => del<CountResponse>('/api/auth/me', { data: body }),
}

export const taskApi = {
  list: (query: TaskQuery = {}) => get<PageResponse<TaskResponse>>('/api/tasks', query),
  board: () => get<TaskBoardColumn[]>('/api/tasks/board'),
  get: (id: string) => get<TaskResponse>(`/api/tasks/${id}`),
  create: (body: TaskRequest) => post<TaskResponse>('/api/tasks', body),
  quickCreate: (body: QuickCreateRequest) => post<TaskResponse>('/api/tasks/quick', body),
  update: (id: string, body: TaskRequest) => put<TaskResponse>(`/api/tasks/${id}`, body),
  updateStatus: (id: string, body: StatusUpdateRequest) =>
    patch<TaskResponse>(`/api/tasks/${id}/status`, body),
  complete: (id: string, body: CompleteTaskRequest) =>
    post<TaskResponse>(`/api/tasks/${id}/complete`, body),
  remove: (id: string) => del<void>(`/api/tasks/${id}`),
  bulk: (body: BulkTaskRequest) => post<BulkTaskResponse>('/api/tasks/bulk', body),
  reorder: (tasks: PositionedTask[]) => post<TaskResponse[]>('/api/tasks/reorder', { tasks }),
  dependencies: (id: string) => get<string[]>(`/api/tasks/${id}/dependencies`),
  addDependency: (id: string, body: DependencyRequest) =>
    post<string[]>(`/api/tasks/${id}/dependencies`, body),
  removeDependency: (id: string, dependsOnTaskId: string) =>
    del<string[]>(`/api/tasks/${id}/dependencies/${dependsOnTaskId}`),
  materialiseRecurrence: (id: string) =>
    post<MaterialiseResponse>(`/api/tasks/${id}/recurrence/materialise`),
}

export const goalApi = {
  list: (query: GoalQuery = {}) => get<PageResponse<GoalResponse>>('/api/goals', query),
  active: () => get<GoalSummary[]>('/api/goals/active'),
  get: (id: string) => get<GoalResponse>(`/api/goals/${id}`),
  create: (body: GoalRequest) => post<GoalResponse>('/api/goals', body),
  update: (id: string, body: GoalRequest) => put<GoalResponse>(`/api/goals/${id}`, body),
  updateProgress: (id: string, body: GoalProgressUpdate) =>
    patch<GoalResponse>(`/api/goals/${id}/progress`, body),
  remove: (id: string) => del<void>(`/api/goals/${id}`),
  milestones: (id: string) => get<MilestoneResponse[]>(`/api/goals/${id}/milestones`),
  addMilestone: (id: string, body: MilestoneRequest) =>
    post<MilestoneResponse>(`/api/goals/${id}/milestones`, body),
  updateMilestone: (id: string, milestoneId: string, body: MilestoneRequest) =>
    put<MilestoneResponse>(`/api/goals/${id}/milestones/${milestoneId}`, body),
  removeMilestone: (id: string, milestoneId: string) =>
    del<void>(`/api/goals/${id}/milestones/${milestoneId}`),
  confirmProposal: (body: ConfirmGoalProposalRequest) =>
    post<GoalResponse>('/api/goals/confirm-proposal', body),
}

export const habitApi = {
  list: (includeArchived = false) =>
    get<HabitResponse[]>('/api/habits', { includeArchived }),
  statistics: () => get<HabitStats[]>('/api/habits/statistics'),
  get: (id: string) => get<HabitResponse>(`/api/habits/${id}`),
  create: (body: HabitRequest) => post<HabitResponse>('/api/habits', body),
  update: (id: string, body: HabitRequest) => put<HabitResponse>(`/api/habits/${id}`, body),
  remove: (id: string) => del<void>(`/api/habits/${id}`),
  log: (id: string, body: HabitLogRequest) => post<HabitLogResponse>(`/api/habits/${id}/logs`, body),
  toggle: (id: string, body?: HabitLogToggleRequest) =>
    patch<HabitLogResponse>(`/api/habits/${id}/logs/toggle`, body ?? {}),
  logs: (id: string, from?: string, to?: string) =>
    get<HabitLogResponse[]>(`/api/habits/${id}/logs`, { from, to }),
  trend: (id: string, days = 30) => get<HabitTrend>(`/api/habits/${id}/trend`, { days }),
}

export const focusApi = {
  start: (body: FocusSessionRequest) => post<FocusSessionResponse>('/api/focus/sessions', body),
  complete: (id: string, body: FocusCompleteRequest) =>
    post<FocusSessionResponse>(`/api/focus/sessions/${id}/complete`, body),
  get: (id: string) => get<FocusSessionResponse>(`/api/focus/sessions/${id}`),
  remove: (id: string) => del<void>(`/api/focus/sessions/${id}`),
  sessions: (query: { from?: string; to?: string; limit?: number } = {}) =>
    get<FocusSessionResponse[]>('/api/focus/sessions', query),
  statistics: (days = 30) => get<FocusStats>('/api/focus/statistics', { days }),
}

export const calendarApi = {
  feed: (from: string, to: string) => get<CalendarFeed>('/api/calendar', { from, to }),
  deadlines: (from: string, to: string) => get<EventResponse[]>('/api/calendar/deadlines', { from, to }),
  get: (id: string) => get<EventResponse>(`/api/calendar/${id}`),
  create: (body: EventRequest) => post<EventResponse>('/api/calendar', body),
  update: (id: string, body: EventRequest) => put<EventResponse>(`/api/calendar/${id}`, body),
  move: (id: string, body: EventMoveRequest) =>
    patch<EventResponse>(`/api/calendar/${id}/move`, body),
  remove: (id: string) => del<void>(`/api/calendar/${id}`),
}

export const financeApi = {
  transactions: (query: TransactionQuery = {}) =>
    get<PageResponse<TransactionResponse>>('/api/finance/transactions', query),
  createTransaction: (body: TransactionRequest) =>
    post<TransactionResponse>('/api/finance/transactions', body),
  updateTransaction: (id: string, body: TransactionRequest) =>
    put<TransactionResponse>(`/api/finance/transactions/${id}`, body),
  removeTransaction: (id: string) =>
    del<void>(`/api/finance/transactions/${id}`, okOrNoContent),
  budgets: () => get<BudgetResponse[]>('/api/finance/budgets'),
  createBudget: (body: BudgetRequest) => post<BudgetResponse>('/api/finance/budgets', body),
  removeBudget: (id: string) => del<void>(`/api/finance/budgets/${id}`),
  savingsGoals: () => get<SavingsGoalResponse[]>('/api/finance/savings-goals'),
  createSavingsGoal: (body: SavingsGoalRequest) =>
    post<SavingsGoalResponse>('/api/finance/savings-goals', body),
  updateSavingsGoal: (id: string, body: SavingsGoalRequest) =>
    put<SavingsGoalResponse>(`/api/finance/savings-goals/${id}`, body),
  removeSavingsGoal: (id: string) => del<void>(`/api/finance/savings-goals/${id}`),
  overview: () => get<FinanceOverview>('/api/finance/overview'),
  summary: (month?: string) =>
    get<MonthlyFinanceSummary>('/api/finance/summary', { month }),
  trend: (months = 12) => get<MonthlyPoint[]>('/api/finance/trend', { months }),
  spendingPatterns: () => get<SpendingPattern[]>('/api/finance/spending-patterns'),
  categories: () => get<string[]>('/api/finance/categories'),
}

export const journalApi = {
  list: (query: { from?: string; to?: string; page?: number; size?: number } = {}) =>
    get<PageResponse<JournalResponse>>('/api/journal', query),
  statistics: () => get<JournalStats>('/api/journal/statistics'),
  get: (id: string) => get<JournalResponse>(`/api/journal/${id}`),
  create: (body: JournalRequest) => post<JournalResponse>('/api/journal', body),
  update: (id: string, body: JournalRequest) => put<JournalResponse>(`/api/journal/${id}`, body),
  remove: (id: string) => del<void>(`/api/journal/${id}`),
  analyse: (id: string) => post<JournalResponse>(`/api/journal/${id}/analysis`),
}

export const learningApi = {
  goals: (status?: string) => get<LearningGoalResponse[]>('/api/learning/goals', { status }),
  goal: (id: string) => get<LearningGoalResponse>(`/api/learning/goals/${id}`),
  createGoal: (body: LearningGoalRequest) => post<LearningGoalResponse>('/api/learning/goals', body),
  updateGoal: (id: string, body: LearningGoalRequest) =>
    put<LearningGoalResponse>(`/api/learning/goals/${id}`, body),
  removeGoal: (id: string) => del<void>(`/api/learning/goals/${id}`),
  addTopic: (goalId: string, body: TopicRequest) =>
    post<TopicResponse>(`/api/learning/goals/${goalId}/topics`, body),
  completeTopic: (topicId: string, completed = true) =>
    patch<TopicResponse>(`/api/learning/topics/${topicId}/complete`, undefined, {
      params: toSearchParams({ completed }),
    }),
  removeTopic: (topicId: string) => del<void>(`/api/learning/topics/${topicId}`),
  startSession: (body: StudySessionRequest) =>
    post<LearningSessionResponse>('/api/learning/sessions', body),
  sessions: (limit = 50) => get<LearningSessionResponse[]>('/api/learning/sessions', { limit }),
  study: (days = 30) => get<DailyStudyPoint[]>('/api/learning/study', { days }),
  createResource: (body: ResourceRequest) => post<ResourceResponse>('/api/learning/resources', body),
  resources: (goalId?: string) => get<ResourceResponse[]>('/api/learning/resources', { goalId }),
  removeResource: (id: string) => del<void>(`/api/learning/resources/${id}`),
  createSkill: (body: SkillRequest) => post<SkillResponse>('/api/learning/skills', body),
  skills: () => get<SkillResponse[]>('/api/learning/skills'),
  removeSkill: (id: string) => del<void>(`/api/learning/skills/${id}`),
  statistics: () => get<LearningStats>('/api/learning/statistics'),
  confirmRoadmap: (body: ConfirmRoadmapRequest) =>
    post<LearningGoalResponse>('/api/learning/roadmaps/confirm', body),
}

export const knowledgeApi = {
  upload: (file: File, title?: string, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return post<DocumentResponse>('/api/knowledge', form, {
      params: toSearchParams({ title }),
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      },
    })
  },
  documents: (query: { q?: string; page?: number; size?: number } = {}) =>
    get<DocumentListResponse>('/api/knowledge/documents', query),
  document: (id: string) => get<DocumentResponse>(`/api/knowledge/documents/${id}`),
  reprocess: (id: string) => post<DocumentResponse>(`/api/knowledge/documents/${id}/reprocess`),
  remove: (id: string) => del<void>(`/api/knowledge/documents/${id}`),
  search: (q: string, topK = 5) =>
    get<KnowledgeSearchResponse>('/api/knowledge/search', { q, topK }),
  ask: (body: AskRequest) => post<AskResponse>('/api/knowledge/ask', body),
  items: (query: { type?: string; q?: string; page?: number; size?: number } = {}) =>
    get<PageResponse<SavedItemResponse>>('/api/knowledge/items', query),
  createItem: (body: SavedItemRequest) => post<SavedItemResponse>('/api/knowledge/items', body),
  updateItem: (id: string, body: SavedItemRequest) =>
    put<SavedItemResponse>(`/api/knowledge/items/${id}`, body),
  removeItem: (id: string) => del<void>(`/api/knowledge/items/${id}`),
}

export const aiApi = {
  chat: (body: ChatRequest) => post<ChatResponse>('/api/ai/chat', body),
  scopes: () => get<string[]>('/api/ai/scopes'),
  conversations: (query: { page?: number; size?: number } = {}) =>
    get<PageResponse<ConversationSummary>>('/api/ai/conversations', query),
  conversation: (id: string) => get<ConversationDetail>(`/api/ai/conversations/${id}`),
  renameConversation: (id: string, body: RenameConversationRequest) =>
    put<ConversationDetail>(`/api/ai/conversations/${id}`, body),
  removeConversation: (id: string) => del<void>(`/api/ai/conversations/${id}`),
  planDay: (body: PlanDayRequest) => post<PlanDayResponse>('/api/ai/plan-day', body),
  applyPlan: (body: PlanDayApplyRequest) => post<PlanDayResponse>('/api/ai/plan-day/apply', body),
  recommendations: () => get<RecommendationsResponse>('/api/ai/recommendations'),
  providers: () => get<ProviderStatus>('/api/ai/providers'),
}

export const analyticsApi = {
  full: (from: string, to: string) => get<AnalyticsResponse>('/api/analytics', { from, to }),
  summary: (from: string, to: string) => get<AnalyticsSummary>('/api/analytics/summary', { from, to }),
  insights: (query: InsightQuery = {}) =>
    get<PageResponse<InsightResponse>>('/api/analytics/insights', query),
  generateInsights: () => post<InsightGenerationResponse>('/api/analytics/insights/generate'),
  updateInsight: (id: string, body: InsightDismissRequest) =>
    patch<InsightResponse>(`/api/analytics/insights/${id}`, body),
  predictions: () => get<PredictionResponse[]>('/api/analytics/predictions'),
  regeneratePredictions: () => post<RegenerationResponse>('/api/analytics/predictions/regenerate'),
  removePrediction: (id: string) => del<void>(`/api/analytics/predictions/${id}`),
  predictionHistory: (query: PredictionHistoryQuery = {}) =>
    get<PredictionResponse[]>('/api/analytics/predictions/history', query),
  balance: () => get<BalanceScore>('/api/analytics/balance'),
  updateWeights: (body: BalanceWeightsRequest) =>
    put<BalanceScore>('/api/analytics/balance/weights', body),
}

export const dashboardApi = {
  dashboard: () => get<DashboardResponse>('/api/dashboard'),
  today: () => get<TodayResponse>('/api/today'),
  search: (q: string, type?: string) => get<SearchResponse>('/api/search', { q, type }),
}

export const settingsApi = {
  onboarding: () => get<OnboardingState>('/api/onboarding'),
  submitOnboarding: (body: OnboardingRequest) => post<OnboardingState>('/api/onboarding', body),
  skipOnboarding: () => post<OnboardingState>('/api/onboarding/skip'),
  preferences: () => get<PreferenceResponse>('/api/settings/preferences'),
  updatePreferences: (body: UpdatePreferenceRequest) =>
    put<PreferenceResponse>('/api/settings/preferences', body),
  notificationPreferences: () =>
    get<NotificationPreferenceResponse>('/api/settings/notifications'),
  updateNotificationPreferences: (body: NotificationPreferenceRequest) =>
    put<NotificationPreferenceResponse>('/api/settings/notifications', body),
}

export const notificationApi = {
  list: (query: NotificationQuery = {}) => get<NotificationPage>('/api/notifications', query),
  unreadCount: () => get<UnreadCountResponse>('/api/notifications/unread-count'),
  markRead: (id: string) => post<NotificationResponse>(`/api/notifications/${id}/read`),
  markAllRead: (category?: string) =>
    post<MarkedCountResponse>('/api/notifications/read-all', category ? { category } : {}),
  remove: (id: string) => del<void>(`/api/notifications/${id}`),
  clearAll: () => del<void>('/api/notifications'),
}

export const accountApi = {
  export: () => post<ExportResponse>('/api/account/export'),
  exportSections: () => get<string[]>('/api/account/export/sections'),
  downloadExport: (exportId: string, fileName: string) =>
    download(`/api/account/export/${exportId}`, fileName),
}

export const graphApi = {
  graph: (query: { types?: GraphNodeType[]; focusType?: string; focusId?: string } = {}) =>
    get<GraphResponse>('/api/graph', query),
  neighbourhood: (type: GraphNodeType, nodeId: string) =>
    get<GraphNeighbourhoodResponse>('/api/graph/neighbourhood', { type, nodeId }),
  paths: (startType: GraphNodeType, startId: string) =>
    get<string[]>(`/api/graph/paths`, { startType, startId }),
  addEdge: (body: GraphEdgeRequest) => post<GraphEdge>('/api/graph/edges', body),
  removeEdge: (query: {
    sourceType: GraphNodeType
    sourceId: string
    targetType: GraphNodeType
    targetId: string
    relation?: GraphRelation
  }) => del<RemovedResponse>('/api/graph/edges', { params: toSearchParams(query) }),
  addNode: (body: GraphNodeRequest) =>
    post<GraphNode>('/api/graph/nodes', body),
  removeNode: (type: GraphNodeType, nodeId: string) =>
    del<RemovedResponse>(`/api/graph/nodes/${type}/${nodeId}`),
  stats: () => get<CountResponse>('/api/graph/stats'),
}

export const adminApi = {
  stats: () => get<SystemStats>('/api/admin/stats'),
  users: (query: AdminUserQuery = {}) => get<PageResponse<AdminUserResponse>>('/api/admin/users', query),
  updateUserStatus: (userId: string, body: UserStatusRequest) =>
    patch<AdminUserResponse>(`/api/admin/users/${userId}/status`, body),
  updateUserRole: (userId: string, body: UserRoleRequest) =>
    patch<AdminUserResponse>(`/api/admin/users/${userId}/role`, body),
  privacy: (userId: string) => get<UserPrivacyMetadata>(`/api/admin/users/${userId}/privacy`),
  auditLogs: (query: AdminPageQuery = {}) => get<PageResponse<AuditLogResponse>>('/api/admin/audit-logs', query),
  errorLogs: (query: AdminPageQuery & { severity?: string } = {}) =>
    get<PageResponse<ErrorLogResponse>>('/api/admin/error-logs', query),
  settings: () => get<SettingResponse[]>('/api/admin/settings'),
  upsertSetting: (body: SettingRequest) => post<SettingResponse>('/api/admin/settings', body),
  aiConfiguration: () => get<AiConfigurationResponse>('/api/admin/ai-configuration'),
}

export type { CategoryTotal }
