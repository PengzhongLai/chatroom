import axios from 'axios'

const BACKEND_URL = 'http://localhost:8080'

/**
 * Upload a file with progress callback.
 * Uses raw axios for progress support, but reuses the token from request's auth store.
 */
export function uploadFile(
  file: File,
  onProgress?: (percent: number) => void
): Promise<{ fileName: string; filePath: string; fileType: 'IMAGE' | 'FILE' }> {
  const formData = new FormData()
  formData.append('file', file)

  // Read token from localStorage directly to avoid Pinia import issues
  const token = localStorage.getItem('token')

  return axios.post(`${BACKEND_URL}/api/files/upload`, formData, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    onUploadProgress: (e) => {
      if (e.total && onProgress) {
        onProgress(Math.round((e.loaded * 100) / e.total))
      }
    }
  }).then(res => res.data.data)
}

export async function fetchProtectedFile(filePath: string): Promise<Blob> {
  if (!filePath.startsWith('/files/')) {
    throw new Error('非法文件路径')
  }

  const token = localStorage.getItem('token')
  if (!token) {
    throw new Error('请先登录')
  }

  const response = await axios.get(`${BACKEND_URL}${filePath}`, {
    headers: { Authorization: `Bearer ${token}` },
    responseType: 'blob'
  })
  return response.data
}

export async function downloadProtectedFile(filePath: string, fileName: string): Promise<void> {
  const blob = await fetchProtectedFile(filePath)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
}
