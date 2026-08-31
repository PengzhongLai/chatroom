import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChannelInfo, ChannelMember } from '@/api/channels'
import * as channelApi from '@/api/channels'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref<ChannelInfo[]>([])
  const myChannels = ref<ChannelMember[]>([])
  const currentChannel = ref<ChannelInfo | null>(null)
  const members = ref<ChannelMember[]>([])
  const loading = ref(false)

  async function fetchChannels(keyword?: string) {
    loading.value = true
    try {
      const res: any = await channelApi.listChannels(keyword)
      channels.value = res.data.content
    } finally {
      loading.value = false
    }
  }

  async function fetchMyChannels() {
    const res: any = await channelApi.myChannels()
    myChannels.value = res.data
  }

  async function createChannel(name: string, description: string, isPublic: boolean) {
    await channelApi.createChannel(name, description, isPublic)
    await fetchMyChannels()
    await fetchChannels()
  }

  async function selectChannel(id: number) {
    const res: any = await channelApi.getChannel(id)
    currentChannel.value = res.data
    const mRes: any = await channelApi.getMembers(id)
    members.value = mRes.data
  }

  async function joinChannel(id: number, inviteCode?: string) {
    await channelApi.joinChannel(id, inviteCode)
    await fetchMyChannels()
    await selectChannel(id)
  }

  async function joinByCode(inviteCode: string) {
    const res: any = await channelApi.joinByCode(inviteCode)
    await fetchMyChannels()
    if (res.data?.channel?.id) {
      await selectChannel(res.data.channel.id)
    }
  }

  async function leaveChannel(id: number) {
    await channelApi.leaveChannel(id)
    currentChannel.value = null
    members.value = []
    await fetchMyChannels()
  }

  async function toggleMute(id: number) {
    const res: any = await channelApi.toggleMute(id)
    if (currentChannel.value?.id === id) {
      currentChannel.value = res.data
    }
  }

  async function kickMember(channelId: number, userId: number) {
    await channelApi.updateMember(channelId, userId, 'kick')
    members.value = members.value.filter(m => m.user.id !== userId)
  }

  function getMyRole(): string | null {
    const me = members.value.find(m => m.user.id === getUserId())
    return me?.role ?? null
  }

  function getUserId(): number {
    // Read from localStorage directly to avoid circular Pinia dependency
    try {
      const token = localStorage.getItem('token')
      if (!token) return 0
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.sub ? Number(payload.sub) : 0
    } catch { return 0 }
  }

  return {
    channels, myChannels, currentChannel, members, loading,
    fetchChannels, fetchMyChannels, createChannel, selectChannel, joinByCode,
    joinChannel, leaveChannel, toggleMute, kickMember, getMyRole, getUserId
  }
})
