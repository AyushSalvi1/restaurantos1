/** ISO-8601 instant in UTC, e.g. `2026-01-31T09:00:00Z`. */
export type Instant = string
/** Calendar date, `YYYY-MM-DD`. */
export type LocalDate = string
/** Wall-clock time, `HH:mm` or `HH:mm:ss`. */
export type LocalTime = string
/** Year-month, `yyyy-MM`. */
export type YearMonth = string

export type Role = 'USER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED'
export type EmploymentType = 'EMPLOYED' | 'STUDENT' | 'BOTH' | 'SELF_EMPLOYED' | 'OTHER'
export type ProductivityStyle = 'DEEP_WORK' | 'BALANCED' | 'POMODORO' | 'ADHOC'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type EnergyRequirement = 'LOW' | 'MEDIUM' | 'HIGH'
export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'PAUSED' | 'ARCHIVED' | 'CANCELLED'
export type GoalType = 'LONG_TERM' | 'SHORT_TERM'
export type Origin = 'USER' | 'AI'
export type HabitFrequency = 'DAILY' | 'WEEKLY'
export type FocusMode = 'POMODORO_25_5' | 'POMODORO_50_10' | 'DEEP_WORK' | 'CUSTOM'
export type EventType = 'EVENT' | 'DEADLINE' | 'FOCUS' | 'HABIT_REMINDER'
export type TransactionType = 'INCOME' | 'EXPENSE'
export type BudgetPeriod = 'WEEKLY' | 'MONTHLY' | 'YEARLY'
export type JournalMood = 'GREAT' | 'GOOD' | 'NEUTRAL' | 'LOW' | 'DIFFICULT'
export type LearningStatus = 'ACTIVE' | 'COMPLETED' | 'PAUSED' | 'ARCHIVED'
export type ResourceType =
  | 'ARTICLE'
  | 'VIDEO'
  | 'BOOK'
  | 'COURSE'
  | 'PROJECT'
  | 'DOCUMENTATION'
  | 'OTHER'
export type SavedItemType = 'NOTE' | 'URL' | 'BOOKMARK'
export type GraphNodeType =
  | 'GOAL'
  | 'TASK'
  | 'HABIT'
  | 'SKILL'
  | 'PROJECT'
  | 'LEARNING_GOAL'
  | 'LEARNING_TOPIC'
  | 'CALENDAR_EVENT'
  | 'FOCUS_SESSION'
  | 'JOURNAL_ENTRY'
  | 'KNOWLEDGE_DOCUMENT'
export type GraphRelation =
  | 'CONTRIBUTES_TO'
  | 'DEPENDS_ON'
  | 'RELATED_TO'
  | 'SUPPORTS'
  | 'DERIVED_FROM'
export type NotificationCategory =
  | 'TASK'
  | 'GOAL'
  | 'HABIT'
  | 'CALENDAR'
  | 'FINANCE'
  | 'LEARNING'
  | 'AI_INSIGHT'
  | 'SYSTEM'
export type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH'
export type InsightType =
  | 'PRODUCTIVITY'
  | 'HABIT'
  | 'GOAL'
  | 'FINANCE'
  | 'LEARNING'
  | 'FOCUS'
  | 'PLANNING'
  | 'JOURNAL'
export type InsightSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export type PredictionType =
  | 'DEADLINE_RISK'
  | 'GOAL_COMPLETION'
  | 'BUDGET_OVERSPEND'
  | 'HABIT_CONSISTENCY'
  | 'PRODUCTIVITY_CHANGE'
export type ProjectStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type BulkAction =
  | 'COMPLETE'
  | 'REOPEN'
  | 'CANCEL'
  | 'DELETE'
  | 'SET_PRIORITY'
  | 'MOVE_TO_GOAL'
  | 'MOVE_TO_PROJECT'
  | 'SET_CATEGORY'

export interface ApiErrorField {
  field: string
  message: string
}

export interface ApiError {
  code: string
  message: string
  fieldErrors: ApiErrorField[]
  path: string
  requestId: string
  timestamp: Instant
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/** Generic acknowledgement envelope returned by endpoints that only report an outcome. */
export interface OperationMessage {
  message: string
}

export interface CountResponse {
  count: number
}

/** Returned by the prediction regeneration endpoint. */
export interface RegenerationResponse {
  created: number
}

/* -------------------------------------------------------------------------- */
/* Identity                                                                    */
/* -------------------------------------------------------------------------- */

export interface UserResponse {
  id: string
  email: string
  fullName: string
  role: Role
  avatarUrl: string
  timezone: string
  locale: string
  occupation: string
  employmentType: EmploymentType
  emailVerified: boolean
  onboardingCompleted: boolean
  createdAt: Instant
  lastLoginAt: Instant
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresAt: Instant
  user: UserResponse
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  timezone?: string
  avatarUrl?: string
}

export interface LoginRequest {
  email: string
  password: string
  deviceName?: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken?: string
  allDevices?: boolean
}

export interface SessionResponse {
  id: string
  userAgent: string
  ipAddress: string
  issuedAt: Instant
  expiresAt: Instant
  current: boolean
}

export interface SessionListResponse {
  sessions: SessionResponse[]
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface VerifyEmailRequest {
  token: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface UpdateProfileRequest {
  fullName?: string
  avatarUrl?: string
  timezone?: string
  locale?: string
  occupation?: string
  employmentType?: EmploymentType
}

export interface DeleteAccountRequest {
  password: string
  confirmEmail?: boolean
}

/* -------------------------------------------------------------------------- */
/* Preferences                                                                 */
/* -------------------------------------------------------------------------- */

export interface PreferenceResponse {
  workingHoursStart: LocalTime
  workingHoursEnd: LocalTime
  dayStart: LocalTime
  dayEnd: LocalTime
  preferredProductivityStyle: ProductivityStyle
  preferredFocusMinutes: number
  breakMinutes: number
  weeklyProductivityHours: number
  areasOfInterest: string[]
  currentSkills: string[]
  primaryGoalAreas: string[]
  financialGoals: string[]
  learningGoals: string[]
  lifeBalanceWeights: Record<string, number>
}

export interface UpdatePreferenceRequest {
  workingHoursStart?: LocalTime
  workingHoursEnd?: LocalTime
  dayStart?: LocalTime
  dayEnd?: LocalTime
  preferredProductivityStyle?: ProductivityStyle
  preferredFocusMinutes?: number
  breakMinutes?: number
  weeklyProductivityHours?: number
  areasOfInterest?: string[]
  currentSkills?: string[]
  primaryGoalAreas?: string[]
  financialGoals?: string[]
  learningGoals?: string[]
  lifeBalanceWeights?: Record<string, number>
}

export interface NotificationPreferenceResponse {
  inAppEnabled: boolean
  emailEnabled: boolean
  taskEnabled: boolean
  goalEnabled: boolean
  habitEnabled: boolean
  calendarEnabled: boolean
  financeEnabled: boolean
  learningEnabled: boolean
  aiInsightEnabled: boolean
  reminderEnabled: boolean
  quietHoursStart: LocalTime
  quietHoursEnd: LocalTime
}

export type NotificationPreferenceRequest = Partial<NotificationPreferenceResponse>

export interface OnboardingState {
  completed: boolean
  skipped: boolean
  user: UserResponse
  preferences: PreferenceResponse
}

export interface OnboardingRequest {
  name?: string
  avatarUrl?: string
  occupation?: string
  employmentType?: EmploymentType
  timezone?: string
  workingHoursStart?: LocalTime
  workingHoursEnd?: LocalTime
  primaryGoals?: string[]
  areasOfInterest?: string[]
  currentSkills?: string[]
  preferredProductivityStyle?: ProductivityStyle
  preferredFocusMinutes?: number
  breakMinutes?: number
  weeklyProductivityHours?: number
  financialGoals?: string
  learningGoals?: string
  lifeBalanceWeights?: Record<string, number>
  skipped?: boolean
}

/* -------------------------------------------------------------------------- */
/* Tasks                                                                       */
/* -------------------------------------------------------------------------- */

export interface DependencyResponse {
  taskId: string
  title: string
  status: TaskStatus
}

export interface TaskResponse {
  id: string
  title: string
  description: string
  notes: string
  status: TaskStatus
  priority: Priority
  category: string
  deadline: Instant
  estimatedMinutes: number
  actualMinutes: number
  difficulty: Difficulty
  energyRequirement: EnergyRequirement
  tags: string[]
  goalId: string
  milestoneId: string
  projectId: string
  recurrenceRule: string
  recurrenceParentId: string
  recurrenceEndDate: Instant
  position: number
  completedAt: Instant
  createdAt: Instant
  updatedAt: Instant
  dependsOnTaskIds: string[]
  blocks: DependencyResponse[]
}

export interface TaskRequest {
  title: string
  description?: string
  notes?: string
  status?: TaskStatus
  priority?: Priority
  category?: string
  deadline?: Instant
  estimatedMinutes?: number
  actualMinutes?: number
  difficulty?: Difficulty
  energyRequirement?: EnergyRequirement
  tags?: string[]
  goalId?: string
  milestoneId?: string
  projectId?: string
  recurrenceRule?: string
  recurrenceEndDate?: Instant
  dependsOnTaskIds?: string[]
  position?: number
}

export interface QuickCreateRequest {
  title: string
  priority?: Priority
  deadline?: Instant
  estimatedMinutes?: number
  goalId?: string
  category?: string
}

export interface TaskQuery {
  q?: string
  status?: TaskStatus[]
  priority?: Priority[]
  category?: string
  goalId?: string
  projectId?: string
  milestoneId?: string
  tag?: string
  deadlineFrom?: LocalDate
  deadlineTo?: LocalDate
  overdue?: boolean
  sort?: string
  page?: number
  size?: number
  zoneId?: string
}

export interface TaskBoardColumn {
  status: TaskStatus
  label: string
  tasks: TaskResponse[]
}

export interface StatusUpdateRequest {
  status: TaskStatus
  actualMinutes?: number
}

export interface CompleteTaskRequest {
  actualMinutes?: number
  notes?: string
}

export interface BulkTaskRequest {
  taskIds: string[]
  action: BulkAction
  status?: TaskStatus
  priority?: Priority
  goalId?: string
  projectId?: string
  category?: string
}

export interface BulkTaskResponse {
  affected: number
  failedIds: string[]
  message: string
}

export interface PositionedTask {
  id: string
  position?: number
}

export interface DependencyRequest {
  taskId: string
}

export interface MaterialiseResponse {
  created: number
}

/* -------------------------------------------------------------------------- */
/* Goals                                                                       */
/* -------------------------------------------------------------------------- */

export interface MilestoneRequest {
  title: string
  description?: string
  dueDate?: Instant
  completed?: boolean
  progress?: number
  position?: number
}

export interface MilestoneResponse {
  id: string
  goalId: string
  title: string
  description: string
  dueDate: Instant
  completed: boolean
  progress: number
  position: number
}

export interface GoalRequest {
  title: string
  description?: string
  category?: string
  goalType?: GoalType
  targetDate?: Instant
  progress?: number
  priority?: Priority
  status?: GoalStatus
  color?: string
  parentGoalId?: string
  position?: number
  milestones?: MilestoneRequest[]
}

export interface GoalResponse {
  id: string
  title: string
  description: string
  category: string
  goalType: GoalType
  targetDate: Instant
  progress: number
  priority: Priority
  status: GoalStatus
  color: string
  source: Origin
  aiConfirmed: boolean
  parentGoalId: string
  position: number
  createdAt: Instant
  updatedAt: Instant
  taskCount: number
  completedTaskCount: number
  milestoneCount: number
  completedMilestoneCount: number
  milestones: MilestoneResponse[]
  subGoals: GoalResponse[]
}

export interface GoalSummary {
  id: string
  title: string
  progress: number
  status: GoalStatus
  targetDate: Instant
  taskCount: number
  completedTaskCount: number
}

export interface GoalProgressUpdate {
  progress?: number
  status?: GoalStatus
}

export interface GoalQuery {
  q?: string
  status?: GoalStatus
  page?: number
  size?: number
}

export interface ConfirmGoalProposalRequest {
  goal: GoalRequest
  milestones?: MilestoneRequest[]
}

/* -------------------------------------------------------------------------- */
/* Habits                                                                      */
/* -------------------------------------------------------------------------- */

export interface HabitRequest {
  name: string
  description?: string
  category?: string
  frequencyType?: HabitFrequency
  timesPerPeriod?: number
  targetDays?: number[]
  reminderTime?: LocalTime
  color?: string
  archived?: boolean
}

export interface HabitResponse {
  id: string
  name: string
  description: string
  category: string
  frequencyType: HabitFrequency
  timesPerPeriod: number
  targetDays: number[]
  reminderTime: LocalTime
  color: string
  archived: boolean
  currentStreak: number
  longestStreak: number
  completedLast30Days: number
  scheduledLast30Days: number
  consistencyRate30d: number
  lastCompletedDate: LocalDate
  completedToday: boolean
  createdAt: Instant
}

export interface HabitLogRequest {
  logDate: LocalDate
  completed?: boolean
  quantity?: number
  note?: string
}

export interface HabitLogToggleRequest {
  logDate?: LocalDate
}

export interface HabitLogResponse {
  id: string
  habitId: string
  logDate: LocalDate
  completed: boolean
  quantity: number
  note: string
}

export interface HabitStats {
  habitId: string
  name: string
  currentStreak: number
  longestStreak: number
  completedLast30Days: number
  scheduledLast30Days: number
  consistencyRate30d: number
  missedLast30Days: number
  bestDayOfWeek: string
  suggestion: string
}

export interface HabitTrendPoint {
  date: LocalDate
  completed: boolean
  scheduled: boolean
}

export interface HabitTrend {
  habitId: string
  name: string
  points: HabitTrendPoint[]
}

/* -------------------------------------------------------------------------- */
/* Focus                                                                       */
/* -------------------------------------------------------------------------- */

export interface FocusSessionRequest {
  mode?: FocusMode
  plannedMinutes?: number
  taskId?: string
  goalId?: string
  startedAt?: Instant
}

export interface FocusCompleteRequest {
  actualMinutes?: number
  completed?: boolean
  interruptedCount?: number
  outcome?: string
  notes?: string
  rating?: number
  endedAt?: Instant
}

export interface FocusSessionResponse {
  id: string
  taskId: string
  taskTitle: string
  goalId: string
  mode: FocusMode
  plannedMinutes: number
  actualMinutes: number
  startedAt: Instant
  endedAt: Instant
  completed: boolean
  interruptedCount: number
  outcome: string
  notes: string
  rating: number
}

export interface DailyFocusPoint {
  date: LocalDate
  minutes: number
  sessions: number
}

export interface FocusStats {
  totalSessions: number
  completedSessions: number
  totalMinutes: number
  averageMinutes: number
  completionRate: number
  todayMinutes: number
  thisWeekSessions: number
  thisWeekMinutes: number
  bestStreak: number
  daily: DailyFocusPoint[]
}

/* -------------------------------------------------------------------------- */
/* Calendar                                                                    */
/* -------------------------------------------------------------------------- */

export interface Occurrence {
  id: string
  seriesId: string
  title: string
  eventType: EventType
  startAt: Instant
  endAt: Instant
  color: string
  taskId: string
}

export interface CalendarFeed {
  rangeStart: LocalDate
  rangeEnd: LocalDate
  occurrences: Occurrence[]
  deadlines: TaskResponse[]
  habitSchedules: HabitResponse[]
  totalEvents: number
}

export interface EventRequest {
  title: string
  description?: string
  eventType?: EventType
  startAt: Instant
  endAt: Instant
  allDay?: boolean
  recurrenceRule?: string
  taskId?: string
  goalId?: string
  habitId?: string
  location?: string
  color?: string
}

export interface EventResponse {
  id: string
  title: string
  description: string
  eventType: EventType
  startAt: Instant
  endAt: Instant
  allDay: boolean
  recurrenceRule: string
  taskId: string
  goalId: string
  habitId: string
  location: string
  color: string
  externalSource: string
  createdAt: Instant
}

export interface EventMoveRequest {
  startAt: Instant
  endAt: Instant
}

export interface RangeQuery {
  from: LocalDate
  to: LocalDate
}

/* -------------------------------------------------------------------------- */
/* Finance                                                                     */
/* -------------------------------------------------------------------------- */

export interface TransactionRequest {
  transactionType: TransactionType
  amount: number
  category: string
  description?: string
  occurredOn: LocalDate
  recurring?: boolean
  recurrenceRule?: string
  account?: string
  currency?: string
}

export type TransactionResponse = TransactionRequest & {
  id: string
  createdAt: Instant
}

export interface BudgetRequest {
  category?: string
  period: BudgetPeriod
  amount: number
  startDate: LocalDate
  endDate: LocalDate
}

export interface BudgetResponse {
  id: string
  category: string
  period: BudgetPeriod
  amount: number
  startDate: LocalDate
  endDate: LocalDate
  spent: number
  utilisationPercent: number
  exceeded: boolean
}

export interface SavingsGoalRequest {
  name: string
  targetAmount: number
  savedAmount?: number
  targetDate?: LocalDate
  notes?: string
}

export interface SavingsGoalResponse {
  id: string
  name: string
  targetAmount: number
  savedAmount: number
  progressPercent: number
  targetDate: LocalDate
  notes: string
}

export interface CategoryTotal {
  category: string
  total: number
  sharePercent: number
  transactionCount: number
}

export interface MonthlyPoint {
  month: LocalDate
  income: number
  expenses: number
  savings: number
}

export interface SpendingPattern {
  category: string
  currentPeriod: number
  previousPeriod: number
  changePercent: number
  direction: string
}

export interface FinanceOverview {
  incomeThisMonth: number
  expenseThisMonth: number
  savingsThisMonth: number
  remainingBudget: number
  budgets: BudgetResponse[]
  expenseByCategory: CategoryTotal[]
  monthlyTrend: MonthlyPoint[]
  savingsGoals: SavingsGoalResponse[]
  currency: string
}

export interface MonthlyFinanceSummary {
  month: LocalDate
  income: number
  expenses: number
  savings: number
  remainingBudget: number
  expenseByCategory: CategoryTotal[]
  incomeByCategory: CategoryTotal[]
}

export interface TransactionQuery {
  type?: TransactionType
  category?: string
  from?: LocalDate
  to?: LocalDate
  page?: number
  size?: number
}

/* -------------------------------------------------------------------------- */
/* Journal                                                                     */
/* -------------------------------------------------------------------------- */

export interface JournalRequest {
  title?: string
  content: string
  entryDate: LocalDate
  mood?: JournalMood
  moodScore?: number
  tags?: string[]
}

export interface JournalResponse {
  id: string
  title: string
  content: string
  entryDate: LocalDate
  mood: JournalMood
  moodScore: number
  tags: string[]
  aiAnalyzed: boolean
  aiSummary: string
  wordCount: number
  createdAt: Instant
  updatedAt: Instant
}

export interface MoodPoint {
  mood: string
  count: number
}

export interface TagCount {
  tag: string
  count: number
}

export interface JournalStats {
  totalEntries: number
  entriesThisMonth: number
  daysWithEntriesLast30: number
  wordsLast30: number
  moodDistribution: MoodPoint[]
  topTags: TagCount[]
  mostFrequentMood: string
  reflection: string
}

/* -------------------------------------------------------------------------- */
/* Learning                                                                    */
/* -------------------------------------------------------------------------- */

export interface TopicRequest {
  title: string
  description?: string
  estimatedMinutes?: number
  position?: number
}

export interface TopicResponse {
  id: string
  title: string
  description: string
  position: number
  completed: boolean
  completedAt: Instant
  estimatedMinutes: number
}

export interface LearningGoalRequest {
  title: string
  description?: string
  category?: string
  targetDate?: Instant
  status?: LearningStatus
  skillId?: string
  topics?: TopicRequest[]
}

export interface LearningGoalResponse {
  id: string
  title: string
  description: string
  category: string
  targetDate: Instant
  progress: number
  status: LearningStatus
  hoursSpent: number
  skillId: string
  source: Origin
  aiConfirmed: boolean
  topicCount: number
  completedTopicCount: number
  topics: TopicResponse[]
}

export interface StudySessionRequest {
  learningGoalId?: string
  topicId?: string
  startedAt: Instant
  endedAt?: Instant
  minutes?: number
  notes?: string
  quizScore?: number
}

export interface LearningSessionResponse {
  id: string
  learningGoalId: string
  learningGoalTitle: string
  topicId: string
  topicTitle: string
  startedAt: Instant
  endedAt: Instant
  minutes: number
  notes: string
  quizScore: number
}

export interface DailyStudyPoint {
  date: LocalDate
  minutes: number
  topicsCompleted: number
}

export interface ResourceRequest {
  title: string
  url?: string
  resourceType?: ResourceType
  learningGoalId?: string
  completed?: boolean
}

export interface ResourceResponse {
  id: string
  title: string
  url: string
  resourceType: ResourceType
  learningGoalId: string
  completed: boolean
}

export interface SkillRequest {
  name: string
  category?: string
  proficiency?: number
}

export interface SkillResponse {
  id: string
  name: string
  category: string
  proficiency: number
}

export interface LearningStats {
  activeGoals: number
  hoursThisWeek: number
  hoursTotal: number
  minutesThisWeek: number
  sessionsThisWeek: number
  averageQuizScore: number
  topGoals: LearningGoalResponse[]
}

export interface RoadmapStageRequest {
  title: string
  description?: string
  estimatedHours?: number
  topics: string[]
}

export interface ConfirmRoadmapRequest {
  title: string
  description?: string
  category?: string
  targetDate?: Instant
  stages: RoadmapStageRequest[]
}

/* -------------------------------------------------------------------------- */
/* Knowledge                                                                   */
/* -------------------------------------------------------------------------- */

export interface DocumentResponse {
  id: string
  title: string
  filename: string
  contentType: string
  extension: string
  sizeBytes: number
  status: string
  failureReason: string
  chunkCount: number
  wordCount: number
  createdAt: Instant
}

export interface DocumentListResponse {
  documents: DocumentResponse[]
  totalDocuments: number
  totalChunks: number
  totalBytes: number
}

export interface SearchHit {
  chunkId: string
  documentId: string
  documentTitle: string
  filename: string
  chunkIndex: number
  snippet: string
  score: number
}

export interface KnowledgeSearchResponse {
  query: string
  hits: SearchHit[]
  totalHits: number
  grounded: boolean
  message: string
}

export interface AskRequest {
  question: string
  topK?: number
  conversationId?: string
}

export interface AskResponse {
  answer: string
  citations: SearchHit[]
  grounded: boolean
  provider: string
  model: string
  // Both are null unless the request supplied a conversationId. Without one the answer is a one-shot
  // lookup and nothing is written, so there is no id to return.
  conversationId: string | null
  messageId: string | null
}

export interface SavedItemRequest {
  title: string
  url?: string
  content?: string
  tags?: string[]
  itemType?: SavedItemType
}

export interface SavedItemResponse {
  id: string
  title: string
  url: string
  content: string
  tags: string[]
  itemType: string
  createdAt: Instant
}

/* -------------------------------------------------------------------------- */
/* AI                                                                          */
/* -------------------------------------------------------------------------- */

export interface ChatRequest {
  message: string
  conversationId?: string
  contextScopes?: string[]
  historyLimit?: number
}

export interface ChatResponse {
  conversationId: string
  messageId: string
  reply: string
  contextUsed: string[]
  provider: string
  model: string
  grounded: boolean
  citations: SearchHit[]
  createdAt: Instant
}

export interface ConversationSummary {
  id: string
  title: string
  messageCount: number
  lastMessageAt: Instant
  createdAt: Instant
}

/** A stored turn in an assistant conversation. Distinct from `OperationMessage`. */
export interface AiMessageResponse {
  id: string
  role: string
  content: string
  provider: string
  model: string
  citations: SearchHit[]
  createdAt: Instant
}

export interface ConversationDetail {
  summary: ConversationSummary
  messages: AiMessageResponse[]
}

export interface RenameConversationRequest {
  title?: string
}

export interface PlanDayRequest {
  date?: LocalDate
  availableMinutes?: number
  focusBlockMinutes?: number
  breakMinutes?: number
  energyLevel?: number
  includeHabits?: boolean
  includeBreaks?: boolean
  useAi?: boolean
  excludeTaskIds?: string[]
}

export interface PlanUpdateBlockRequest {
  blockId?: string
  startAt?: Instant
  endAt?: Instant
  title?: string
  taskId?: string
  locked?: boolean
}

export interface PlanDayApplyRequest {
  blocks?: PlanUpdateBlockRequest[]
}

export interface PlanBlock {
  id: string
  date: LocalDate
  startAt: Instant
  endAt: Instant
  durationMinutes: number
  kind: string
  title: string
  detail: string
  taskId: string
  goalId: string
  habitId: string
  priority: string
  energyFit: string
  movable: boolean
  locked: boolean
}

export interface PlanDayResponse {
  date: LocalDate
  availableMinutes: number
  scheduledMinutes: number
  breakMinutes: number
  utilisationPercent: number
  blocks: PlanBlock[]
  unscheduledTaskIds: string[]
  warnings: string[]
  rationale: string
  generatedBy: string
}

export interface Recommendation {
  id: string
  type: string
  title: string
  rationale: string
  score: number
  actionLabel: string
  actionPath: string
  supportingData: string[]
}

export interface RecommendationsResponse {
  recommendations: Recommendation[]
  generatedAt: string
  generatedBy: string
}

export interface ProviderAvailability {
  name: string
  configured: boolean
  detail: string
}

export interface ProviderStatus {
  configuredProvider: string
  activeProvider: string
  remoteProviderConfigured: boolean
  degraded: boolean
  providers: ProviderAvailability[]
  note: string
}

/* -------------------------------------------------------------------------- */
/* Analytics                                                                   */
/* -------------------------------------------------------------------------- */

export interface AnalyticsSummary {
  from: LocalDate
  to: LocalDate
  timezone: string
  tasksCreated: number
  tasksCompleted: number
  tasksMissed: number
  taskCompletionRate: number
  averageProductivityScore: number
  focusMinutes: number
  studyMinutes: number
  focusSessions: number
  habitCompletions: number
  habitConsistencyRate: number
  habitScheduled: number
  calendarEvents: number
  income: number
  expenses: number
  savings: number
  goalProgressPoints: number
  score: number
}

export interface ProductivityPoint {
  date: LocalDate
  tasksCompleted: number
  tasksCreated: number
  focusMinutes: number
  studyMinutes: number
  habitsCompleted: number
  productivityScore: number
}

export interface WeeklyProductivity {
  weekStart: LocalDate
  tasksCompleted: number
  focusMinutes: number
  studyMinutes: number
  averageScore: number
}

export interface MonthlyProductivity {
  month: { year: number; month: number }
  tasksCompleted: number
  focusMinutes: number
  studyMinutes: number
  averageScore: number
  completionRate: number
}

export interface GoalProgressPoint {
  goalId: string
  title: string
  progress: number
  status: string
  tasksCompleted: number
  tasksTotal: number
}

export interface CategoryShare {
  category: string
  total: number
  sharePercent: number
}

export interface BalanceDimension {
  key: string
  label: string
  score: number
  weight: number
  explanation: string
  inputs: string[]
  sufficientData: boolean
}

export interface BalanceScore {
  overallScore: number
  grade: string
  dimensions: BalanceDimension[]
  formula: string
  disclaimer: string
  sufficientData: boolean
}

/**
 * Server-generated observation about the user's own records. `factors` lists the evidence the rule
 * fired on and `source` names the detector, so the UI can show why it appeared.
 */
export interface InsightResponse {
  id: string
  insightType: string
  title: string
  body: string
  severity: string
  confidence: number
  factors: string[]
  periodStart: LocalDate
  periodEnd: LocalDate
  source: string
  dismissed: boolean
  createdAt: Instant
}

export interface InsightQuery {
  type?: InsightType
  includeDismissed?: boolean
  page?: number
  size?: number
}

export interface InsightGenerationResponse {
  created: InsightResponse[]
  evaluated: number
  skippedReasons: string[]
}

export interface InsightDismissRequest {
  dismissed: boolean
}

export interface PredictionResponse {
  id: string
  predictionType: string
  subjectType: string
  subjectId: string
  label: string
  probability: number
  confidence: number
  factors: string[]
  horizonDays: number
  createdAt: Instant
  expiresAt: Instant
  disclaimer: string
}

export interface PredictionHistoryQuery {
  type?: PredictionType
  days?: number
}

export interface BalanceWeightsRequest {
  weights: Record<string, number>
}

export interface AnalyticsResponse {
  from: LocalDate
  to: LocalDate
  summary: AnalyticsSummary
  daily: ProductivityPoint[]
  weekly: WeeklyProductivity[]
  monthly: MonthlyProductivity[]
  goalProgress: GoalProgressPoint[]
  spendingByCategory: CategoryShare[]
  incomeByCategory: CategoryShare[]
  insights: InsightResponse[]
  predictions: PredictionResponse[]
  lifeBalance: BalanceScore
  dataGaps: string[]
}

/* -------------------------------------------------------------------------- */
/* Dashboard                                                                   */
/* -------------------------------------------------------------------------- */

export interface Greeting {
  salutation: string
  message: string
  displayName: string
  date: LocalDate
  timezone: string
  serverTime: Instant
}

export interface TodaySummary {
  tasksDue: number
  tasksCompleted: number
  tasksOverdue: number
  openTasks: number
  estimatedMinutes: number
  focusMinutesToday: number
  studyMinutesToday: number
  habitsCompleted: number
  habitsScheduled: number
  calendarEvents: number
}

export interface FocusItem {
  rank: number
  taskId: string
  title: string
  priority: Priority
  category: string
  deadline: Instant
  estimatedMinutes: number
  reason: string
  blocked: boolean
  blockedBy: string[]
}

export interface ProgressBar {
  id: string
  label: string
  progress: number
  link: string
  meta: string
}

export interface HabitMini {
  id: string
  name: string
  completedToday: boolean
  currentStreak: number
  reminderTime: string
}

export interface UpcomingEvent {
  id: string
  title: string
  startAt: Instant
  endAt: Instant
  type: string
  location: string
  allDay: boolean
}

export interface CountCard {
  label: string
  value: number
  trend: string
  link: string
}

export interface DashboardPrediction {
  id: string
  type: string
  label: string
  probability: number
  factors: string[]
}

export interface DashboardResponse {
  greeting: Greeting
  timezone: string
  today: TodaySummary
  todaysFocus: FocusItem[]
  goalProgress: ProgressBar[]
  habits: HabitMini[]
  upcomingEvents: UpcomingEvent[]
  summaryCards: CountCard[]
  finance: FinanceOverview
  learning: LearningStats
  productivityScore: number
  productivityExplanation: string
  lifeBalance: BalanceScore
  insights: InsightResponse[]
  recommendations: Recommendation[]
  predictions: DashboardPrediction[]
  dataGaps: string[]
}

export interface TodayResponse {
  date: LocalDate
  greeting: Greeting
  summary: TodaySummary
  todaysFocus: FocusItem[]
  habits: HabitMini[]
  schedule: UpcomingEvent[]
  overdue: TaskResponse[]
  dueToday: TaskResponse[]
  lifeBalance: BalanceScore
  recommendations: Recommendation[]
}

/* -------------------------------------------------------------------------- */
/* Global search                                                               */
/* -------------------------------------------------------------------------- */

export interface SearchResultItem {
  id: string
  type: string
  title: string
  snippet: string
  status: string
  date: LocalDate
  link: string
  score: number
}

export interface SearchGroup {
  type: string
  label: string
  items: SearchResultItem[]
  total: number
}

export interface SearchResponse {
  query: string
  groups: SearchGroup[]
  totalResults: number
  tookMillis: number
  availableTypes: string[]
}

/* -------------------------------------------------------------------------- */
/* Life graph                                                                  */
/* -------------------------------------------------------------------------- */

export interface GraphNode {
  id: string
  type: string
  label: string
  subtitle: string
  status: string
  color: string
  weight: number
  detail: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  relation: string
  weight: number
}

export interface GraphResponse {
  nodes: GraphNode[]
  edges: GraphEdge[]
  nodeCount: number
  edgeCount: number
  focusNodeId: string
  neighbours: GraphNode[]
}

export interface GraphNeighbourhoodResponse {
  node: GraphNode
  edges: GraphEdge[]
  neighbours: GraphNode[]
  pathToGoal: string[]
}

export interface GraphEdgeRequest {
  sourceType: GraphNodeType
  sourceId: string
  targetType: GraphNodeType
  targetId: string
  relation: GraphRelation
  weight?: number
}

export interface GraphNodeRequest {
  type: GraphNodeType
  label: string
  subtitle?: string
  weight?: number
}

export interface RemovedResponse {
  removed: boolean
}

/* -------------------------------------------------------------------------- */
/* Notifications                                                               */
/* -------------------------------------------------------------------------- */

export interface NotificationResponse {
  id: string
  category: string
  title: string
  body: string
  link: string
  priority: string
  read: boolean
  createdAt: Instant
  readAt: Instant
}

export interface NotificationPage {
  notifications: NotificationResponse[]
  unreadCount: number
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface NotificationQuery {
  category?: NotificationCategory
  page?: number
  size?: number
}

export interface UnreadCountResponse {
  unread: number
}

export interface MarkedCountResponse {
  marked: number
}

/* -------------------------------------------------------------------------- */
/* Export                                                                      */
/* -------------------------------------------------------------------------- */

export interface ExportResponse {
  exportId: string
  fileName: string
  sizeBytes: number
  generatedAt: Instant
  status: string
  message: string
}

/* -------------------------------------------------------------------------- */
/* Admin                                                                       */
/* -------------------------------------------------------------------------- */

export interface SystemStats {
  totalUsers: number
  activeUsers: number
  disabledUsers: number
  usersRegisteredLast7Days: number
  totalTasks: number
  totalGoals: number
  totalHabits: number
  totalDocuments: number
  totalChunks: number
  totalJournalEntries: number
  totalFocusMinutes: number
  errorsLast24h: number
  warningsLast24h: number
  aiRequestsLast24h: number
  generatedAt: Instant
}

export interface AdminUserResponse {
  id: string
  email: string
  fullName: string
  role: string
  status: string
  emailVerified: boolean
  onboardingCompleted: boolean
  createdAt: Instant
  lastLoginAt: Instant
  taskCount: number
  goalCount: number
  activeSessions: number
}

export interface UserStatusRequest {
  status: UserStatus
}

export interface UserRoleRequest {
  role: Role
}

export interface UserPrivacyMetadata {
  userId: string
  email: string
  journalEntryCount: number
  knowledgeDocumentCount: number
  knowledgeChunkCount: number
  lastJournalEntryAt: Instant
  lastDocumentUploadAt: Instant
  policy: string
}

export interface AuditLogResponse {
  id: string
  actorUserId: string
  actorEmail: string
  action: string
  entityType: string
  entityId: string
  details: string
  ipAddress: string
  createdAt: Instant
}

export interface ErrorLogResponse {
  id: string
  severity: string
  message: string
  path: string
  method: string
  userId: string
  exceptionClass: string
  createdAt: Instant
}

export interface SettingResponse {
  key: string
  value: string
  valueType: string
  description: string
  updatedAt: Instant
  updatedBy: string
}

export interface SettingRequest {
  key: string
  value?: string
  valueType?: string
  description?: string
}

export interface AiConfigurationResponse {
  configuredProvider: string
  geminiConfigured: boolean
  openAiConfigured: boolean
  ollamaConfigured: boolean
  embeddingFallbackActive: boolean
  vectorDimensions: number
  models: Record<string, string>
  notes: string[]
}

export interface AdminUserQuery {
  q?: string
  roles?: string[]
  size?: number
  page?: number
}

export interface AdminPageQuery {
  page?: number
  size?: number
}
