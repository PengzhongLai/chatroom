export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  errors?: Record<string, string>
}

export interface LoginResponse {
  token: string
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
}

export interface Message {
  id: number | null
  channelId: number
  sender: { id: number; username: string; nickname: string } | null
  type: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM'
  content: string
  fileName: string | null
  filePath: string | null
  isRecalled: boolean
  createdAt: string
}

export interface TypingEvent {
  channelId: number
  userId: number
  nickname: string
  typing: boolean
}

export interface PrivateChatInfo {
  id: number
  initiatorId: number
  iAmInitiator?: boolean
  status?: 'PENDING' | 'ACTIVE' | 'REJECTED'
  otherUser: {
    id: number
    username: string
    nickname: string
  }
  lastMessage?: string
  lastMessageTime?: string
  lastSenderId?: number
}
