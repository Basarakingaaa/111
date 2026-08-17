export type Me = { id:string; githubLogin:string; displayName?:string; email?:string; systemRole:string; active:boolean }
export type Project = { id:string; name:string; code:string; description?:string }
export type User = { id:string; githubLogin:string; displayName?:string; email?:string; systemRole:string; active:boolean }
export type Resource = { id:string; projectId?:string; name:string; resourceType:string; environment?:string; endpoint?:string; host?:string; port?:number; hasAccount:boolean; hasSecret:boolean; hasToken:boolean; notes?:string; updatedAt:string; version:number }
export type Member = { userId:string; githubLogin:string; displayName?:string; role:string }
export type AgentResult = { run_id:string; agent:string; summary:string; findings:string[]; proposed_actions:{tool:string;reason:string;risk:string;requires_approval:boolean}[]; needs_human_input:boolean }

async function request<T>(path:string, options:RequestInit = {}):Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type','application/json')
  if (options.method && !['GET','HEAD'].includes(options.method)) {
    const csrf = await fetch('/api/csrf', { credentials:'same-origin' })
    if (csrf.ok) { const value = await csrf.json(); headers.set(value.headerName, value.token) }
  }
  const response = await fetch(path, { ...options, headers, credentials:'same-origin' })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message:`HTTP ${response.status}` }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

export const api = {
  me: () => request<Me>('/api/me'),
  projects: () => request<Project[]>('/api/projects'),
  createProject: (body:unknown) => request<Project>('/api/projects',{method:'POST',body:JSON.stringify(body)}),
  members: (projectId:string) => request<Member[]>(`/api/projects/${projectId}/members`),
  setMember: (projectId:string,userId:string,role:string) => request<Member>(`/api/projects/${projectId}/members/${userId}`,{method:'PUT',body:JSON.stringify({role})}),
  users: () => request<User[]>('/api/admin/users'),
  userDirectory: () => request<User[]>('/api/users/directory'),
  updateUser: (id:string, body:unknown) => request<User>(`/api/admin/users/${id}`,{method:'PATCH',body:JSON.stringify(body)}),
  resources: (projectId:string) => request<Resource[]>(`/api/resources?projectId=${encodeURIComponent(projectId)}`),
  createResource: (body:unknown) => request<Resource>('/api/resources',{method:'POST',body:JSON.stringify(body)}),
  revealResource: (id:string) => request<{account?:string;secret?:string;token?:string}>(`/api/resources/${id}/reveal`,{method:'POST'}),
  runAgent: (body:unknown) => request<AgentResult>('/api/agent/runs',{method:'POST',body:JSON.stringify(body)})
}
