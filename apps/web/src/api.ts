export type Me = { id:string; loginName:string; authType:'GITHUB'|'LOCAL'; displayName?:string; email?:string; systemRole:string; active:boolean }
export type Project = { id:string; name:string; code:string; description?:string; managerId?:string; managerLogin?:string; currentUserRole?:string; canManageProject:boolean; canManageTasks:boolean; canManageResources:boolean; canRevealSecrets:boolean; canUploadDocuments:boolean }
export type User = { id:string; loginName:string; authType:'GITHUB'|'LOCAL'; displayName?:string; email?:string; systemRole:string; active:boolean }
export type Resource = { id:string; projectId?:string; name:string; resourceType:string; environment?:string; endpoint?:string; host?:string; port?:number; hasAccount:boolean; hasSecret:boolean; hasToken:boolean; notes?:string; updatedAt:string; version:number }
export type ResourceVersion = { version:number; name:string; resourceType:string; environment?:string; endpoint?:string; host?:string; port?:number; hasAccount:boolean; hasSecret:boolean; hasToken:boolean; notes?:string; changedBy:string; changedByLogin?:string; changedAt:string }
export type ProjectDocument = { id:string; projectId:string; displayName:string; originalName:string; description?:string; contentType?:string; sizeBytes:number; sha256:string; uploadedBy:string; uploaderLogin?:string; createdAt:string; downloadUrl:string }
export type Member = { userId:string; loginName:string; displayName?:string; role:string }
export type Task = { id:string; projectId:string; title:string; description?:string; status:string; priority:string; assigneeId?:string; assigneeLogin?:string; reporterId:string; reporterLogin?:string; dueDate?:string; createdAt:string; updatedAt:string; documentRequestCount:number; canManage:boolean; canUpdateProgress:boolean }
export type DocumentRequest = { id:string; projectId:string; taskId?:string; taskTitle?:string; title:string; description?:string; status:string; priority:string; assigneeId?:string; assigneeLogin?:string; requesterId:string; requesterLogin?:string; dueDate?:string; deliveryNote?:string; createdAt:string; updatedAt:string; canManage:boolean; canDeliver:boolean }
export type Notification = { id:string; projectId?:string; notificationType:string; title:string; message:string; relatedType?:string; relatedId?:string; deliveryStatus:string; deliveryDetail?:string; readAt?:string; createdAt:string }
export type NotificationList = { unreadCount:number; items:Notification[] }
export type AgentResult = { run_id:string; agent:string; summary:string; findings:string[]; proposed_actions:{tool:string;reason:string;risk:string;requires_approval:boolean}[]; downloads:{document_id:string;name:string;url:string;content_type?:string;size_bytes:number}[]; needs_human_input:boolean }

export class ApiError extends Error {
  constructor(public status:number, message:string) { super(message); this.name='ApiError' }
}

async function request<T>(path:string, options:RequestInit = {}):Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type','application/json')
  if (options.method && !['GET','HEAD'].includes(options.method)) {
    const csrf = await fetch('/api/csrf', { credentials:'same-origin' })
    if (csrf.ok) { const value = await csrf.json(); headers.set(value.headerName, value.token) }
  }
  const response = await fetch(path, { ...options, headers, credentials:'same-origin' })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message:`HTTP ${response.status}` }))
    throw new ApiError(response.status, error.message || `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

async function csrf() {
  const response = await fetch('/api/csrf', { credentials:'same-origin' })
  if (!response.ok) throw new Error(`无法初始化安全会话（HTTP ${response.status}）`)
  return response.json() as Promise<{headerName:string;token:string}>
}

async function localLogin(username:string, password:string) {
  const token = await csrf()
  const body = new URLSearchParams({ username, password })
  const response = await fetch('/login/local', {
    method:'POST', credentials:'same-origin', body,
    headers:{ 'Content-Type':'application/x-www-form-urlencoded', [token.headerName]:token.token }
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({message:`HTTP ${response.status}`}))
    throw new Error(error.message || '登录失败')
  }
}

async function logout() {
  await request<void>('/api/logout', { method:'POST' })
}

export const api = {
  localLogin,
  logout,
  me: () => request<Me>('/api/me'),
  projects: () => request<Project[]>('/api/projects'),
  createProject: (body:unknown) => request<Project>('/api/projects',{method:'POST',body:JSON.stringify(body)}),
  updateProject: (id:string,body:unknown) => request<Project>(`/api/projects/${id}`,{method:'PUT',body:JSON.stringify(body)}),
  members: (projectId:string) => request<Member[]>(`/api/projects/${projectId}/members`),
  setMember: (projectId:string,userId:string,role:string) => request<Member>(`/api/projects/${projectId}/members/${userId}`,{method:'PUT',body:JSON.stringify({role})}),
  removeMember: (projectId:string,userId:string) => request<void>(`/api/projects/${projectId}/members/${userId}`,{method:'DELETE'}),
  tasks: (projectId:string) => request<Task[]>(`/api/tasks?projectId=${encodeURIComponent(projectId)}`),
  createTask: (body:unknown) => request<Task>('/api/tasks',{method:'POST',body:JSON.stringify(body)}),
  updateTask: (id:string,body:unknown) => request<Task>(`/api/tasks/${id}`,{method:'PUT',body:JSON.stringify(body)}),
  updateTaskProgress: (id:string,status:string) => request<Task>(`/api/tasks/${id}/progress`,{method:'PATCH',body:JSON.stringify({status})}),
  documentRequests: (projectId:string) => request<DocumentRequest[]>(`/api/document-requests?projectId=${encodeURIComponent(projectId)}`),
  createDocumentRequest: (body:unknown) => request<DocumentRequest>('/api/document-requests',{method:'POST',body:JSON.stringify(body)}),
  updateDocumentRequest: (id:string,body:unknown) => request<DocumentRequest>(`/api/document-requests/${id}`,{method:'PUT',body:JSON.stringify(body)}),
  deliverDocumentRequest: (id:string,status:string,deliveryNote:string) => request<DocumentRequest>(`/api/document-requests/${id}/delivery`,{method:'PATCH',body:JSON.stringify({status,deliveryNote})}),
  notifications: () => request<NotificationList>('/api/notifications'),
  readNotification: (id:string) => request<Notification>(`/api/notifications/${id}/read`,{method:'PUT'}),
  readAllNotifications: () => request<{updated:number}>('/api/notifications/read-all',{method:'PUT'}),
  users: () => request<User[]>('/api/admin/users'),
  createUser: (body:unknown) => request<User>('/api/admin/users',{method:'POST',body:JSON.stringify(body)}),
  resetUserPassword: (id:string,password:string) => request<void>(`/api/admin/users/${id}/password`,{method:'PUT',body:JSON.stringify({password})}),
  userDirectory: () => request<User[]>('/api/users/directory'),
  updateUser: (id:string, body:unknown) => request<User>(`/api/admin/users/${id}`,{method:'PATCH',body:JSON.stringify(body)}),
  resources: (projectId:string) => request<Resource[]>(`/api/resources?projectId=${encodeURIComponent(projectId)}`),
  createResource: (body:unknown) => request<Resource>('/api/resources',{method:'POST',body:JSON.stringify(body)}),
  updateResource: (id:string,body:unknown) => request<Resource>(`/api/resources/${id}`,{method:'PUT',body:JSON.stringify(body)}),
  resourceVersions: (id:string) => request<ResourceVersion[]>(`/api/resources/${id}/versions`),
  revealResource: (id:string) => request<{account?:string;secret?:string;token?:string}>(`/api/resources/${id}/reveal`,{method:'POST'}),
  projectDocuments: (projectId:string) => request<ProjectDocument[]>(`/api/project-documents?projectId=${encodeURIComponent(projectId)}`),
  uploadProjectDocument: (body:FormData) => request<ProjectDocument>('/api/project-documents',{method:'POST',body}),
  runAgent: (body:unknown) => request<AgentResult>('/api/agent/runs',{method:'POST',body:JSON.stringify(body)})
}
