<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, ApiError, type AgentResult, type DocumentRequest, type Me, type Member, type Notification, type Project, type ProjectDocument, type Resource, type ResourceVersion, type Task, type User } from './api'

const me = ref<Me|null>(null), loading = ref(true), error = ref(''), tab = ref('resources')
const projects = ref<Project[]>([]), selectedProjectId = ref(''), resources = ref<Resource[]>([]), users = ref<User[]>([])
const members = ref<Member[]>([]), agentResult = ref<AgentResult|null>(null), agentBusy = ref(false)
const tasks = ref<Task[]>([])
const documentRequests = ref<DocumentRequest[]>([]), notifications = ref<Notification[]>([]), unreadCount = ref(0)
const projectDocuments = ref<ProjectDocument[]>([]), documentFile = ref<File|null>(null), documentUploading = ref(false)
const notice = ref(''), secretDialog = ref<{name:string;account?:string;secret?:string;token?:string}|null>(null)
const noticeKind = ref<'success'|'warning'>('success')
const resourceForm = reactive({ name:'', resourceType:'SERVER', environment:'development', endpoint:'', host:'', port:undefined as number|undefined, account:'', secret:'', token:'', notes:'' })
const editingResource = ref<Resource|null>(null), resourceVersions = ref<ResourceVersion[]>([]), resourceHistoryName = ref('')
const resourceEditForm = reactive({ name:'', resourceType:'SERVER', environment:'', endpoint:'', host:'', port:undefined as number|undefined, account:'', secret:'', token:'', notes:'' })
const projectDocumentForm = reactive({displayName:'',description:''})
const projectForm = reactive({name:'',code:'',description:'',managerId:''})
const memberForm = reactive({userId:'',role:'DEVELOPER'})
const agentForm = reactive({message:''})
const localLoginForm = reactive({username:'',password:''}), loginBusy = ref(false)
const createUserForm = reactive({username:'',password:'',displayName:'',email:'',systemRole:'STANDARD',active:true})
const passwordResets = reactive<Record<string,string>>({})
const taskForm = reactive({title:'',description:'',status:'TODO',priority:'MEDIUM',assigneeId:'',dueDate:''})
const documentForm = reactive({taskId:'',title:'',description:'',status:'REQUESTED',priority:'MEDIUM',assigneeId:'',dueDate:''})
const isAdmin = computed(() => ['SUPER_ADMIN','SYSTEM_ADMIN'].includes(me.value?.systemRole || ''))
const selectedProject = computed(() => projects.value.find(p=>p.id===selectedProjectId.value))
const canManageTasks = computed(() => !!selectedProject.value?.canManageTasks)
const canManageResources = computed(() => !!selectedProject.value?.canManageResources)
const canRevealSecrets = computed(() => !!selectedProject.value?.canRevealSecrets)
const canUploadDocuments = computed(() => !!selectedProject.value?.canUploadDocuments)
const myAssignedTasks = computed(() => tasks.value.filter(task=>task.assigneeId===me.value?.id))
let notificationTimer:number|undefined

function showSuccess(message:string){error.value='';noticeKind.value='success';notice.value=message}
function showActionFailure(e:unknown, fallback='操作未完成，请稍后重试'){
  error.value='';noticeKind.value='warning'
  notice.value=e instanceof ApiError && e.status===403?'当前账号没有执行此操作的权限':e instanceof ApiError && e.status===400?'操作未完成，请检查填写内容':fallback
}

async function load() {
  loading.value = true; error.value = ''
  try {
    me.value = await api.me(); projects.value = await api.projects()
    if (!selectedProjectId.value && projects.value.length) selectedProjectId.value = projects.value[0].id
    users.value = isAdmin.value ? await api.users() : await api.userDirectory()
  } catch (e) {
    me.value = null
    const message = e instanceof Error ? e.message : String(e)
    if (!message.includes('HTTP 401')) error.value = message
  }
  finally { loading.value = false }
}
async function loadResources() { if (!selectedProjectId.value) { resources.value=[]; return }; try { resources.value=await api.resources(selectedProjectId.value) } catch(e){ error.value=String(e) } }
async function loadTasks() { if (!selectedProjectId.value) { tasks.value=[]; return }; try { tasks.value=await api.tasks(selectedProjectId.value) } catch(e){ error.value=e instanceof Error?e.message:String(e) } }
async function loadDocumentRequests() { if (!selectedProjectId.value) { documentRequests.value=[]; return }; try { documentRequests.value=await api.documentRequests(selectedProjectId.value) } catch(e){ error.value=e instanceof Error?e.message:String(e) } }
async function loadProjectDocuments() { if (!selectedProjectId.value) { projectDocuments.value=[]; return }; try { projectDocuments.value=await api.projectDocuments(selectedProjectId.value) } catch(e){ error.value=e instanceof Error?e.message:String(e) } }
async function loadNotifications(){if(!me.value)return;try{const result=await api.notifications();notifications.value=result.items;unreadCount.value=result.unreadCount}catch(e){error.value=e instanceof Error?e.message:String(e)}}
watch(selectedProjectId, async()=>{await Promise.all([loadResources(),loadTasks(),loadDocumentRequests(),loadProjectDocuments(),loadMembers()])})
async function saveResource() {
  error.value=''; try { await api.createResource({...resourceForm,projectId:selectedProjectId.value,port:resourceForm.port||null}); showSuccess('资源已加密保存'); Object.assign(resourceForm,{name:'',resourceType:'SERVER',environment:'development',endpoint:'',host:'',port:undefined,account:'',secret:'',token:'',notes:''}); await loadResources() } catch(e){showActionFailure(e,'资源保存失败，请稍后重试')}
}
async function reveal(r:Resource) { try { const s=await api.revealResource(r.id); secretDialog.value={name:r.name,...s} } catch(e){showActionFailure(e,'暂时无法查看该资源')} }
function editResource(r:Resource){editingResource.value=r;Object.assign(resourceEditForm,{name:r.name,resourceType:r.resourceType,environment:r.environment||'',endpoint:r.endpoint||'',host:r.host||'',port:r.port,account:'',secret:'',token:'',notes:r.notes||''})}
async function updateResource(){if(!editingResource.value)return;try{const updated=await api.updateResource(editingResource.value.id,{...resourceEditForm,projectId:selectedProjectId.value,account:resourceEditForm.account||null,secret:resourceEditForm.secret||null,token:resourceEditForm.token||null,port:resourceEditForm.port||null});Object.assign(editingResource.value,updated);editingResource.value=null;showSuccess('资源已更新并生成可追溯版本');await loadResources()}catch(e){showActionFailure(e,'资源修改失败，请稍后重试')}}
async function showResourceHistory(r:Resource){try{resourceVersions.value=await api.resourceVersions(r.id);resourceHistoryName.value=r.name}catch(e){showActionFailure(e,'资源版本记录加载失败')}}
function selectDocumentFile(event:Event){documentFile.value=(event.target as HTMLInputElement).files?.[0]||null}
async function uploadProjectDocument(){if(!documentFile.value||!selectedProjectId.value)return;documentUploading.value=true;try{const body=new FormData();body.append('projectId',selectedProjectId.value);body.append('file',documentFile.value);if(projectDocumentForm.displayName)body.append('displayName',projectDocumentForm.displayName);if(projectDocumentForm.description)body.append('description',projectDocumentForm.description);const uploaded=await api.uploadProjectDocument(body);projectDocuments.value.unshift(uploaded);documentFile.value=null;Object.assign(projectDocumentForm,{displayName:'',description:''});const input=document.getElementById('project-document-file') as HTMLInputElement|null;if(input)input.value='';showSuccess('文档已上传，可从 UI 或 Agent 访问和下载')}catch(e){showActionFailure(e,'文档上传失败，请检查文件大小和格式')}finally{documentUploading.value=false}}
function formatBytes(bytes:number){if(bytes<1024)return `${bytes} B`;if(bytes<1024*1024)return `${(bytes/1024).toFixed(1)} KB`;return `${(bytes/1024/1024).toFixed(1)} MB`}
async function localSignIn(){loginBusy.value=true;error.value='';try{await api.localLogin(localLoginForm.username,localLoginForm.password);localLoginForm.password='';await load();await loadResources()}catch(e){error.value=e instanceof Error?e.message:String(e)}finally{loginBusy.value=false}}
async function signOut(){
  error.value=''
  try {
    await api.logout()
    me.value=null; projects.value=[]; tasks.value=[]; documentRequests.value=[]; projectDocuments.value=[]; notifications.value=[]; unreadCount.value=0
    window.location.replace('/?signed-out=1')
  } catch(e) { showActionFailure(e,'退出未完成，请重试') }
}
async function saveUser(u:User) { try { await api.updateUser(u.id,{systemRole:u.systemRole,active:u.active}); showSuccess(`${u.loginName} 已更新`) } catch(e){showActionFailure(e,'用户更新失败，请稍后重试'); await load()} }
async function createLocalUser(){try{const created=await api.createUser(createUserForm);users.value.push(created);Object.assign(createUserForm,{username:'',password:'',displayName:'',email:'',systemRole:'STANDARD',active:true});showSuccess(`本地账号 ${created.loginName} 已创建`)}catch(e){showActionFailure(e,'账号创建失败，请检查填写内容')}}
async function resetPassword(u:User){const password=passwordResets[u.id]||'';if(!password)return;try{await api.resetUserPassword(u.id,password);passwordResets[u.id]='';showSuccess(`${u.loginName} 的密码已重置`)}catch(e){showActionFailure(e,'密码重置失败，请检查密码要求')}}
async function createTask(){try{const task=await api.createTask({...taskForm,projectId:selectedProjectId.value,assigneeId:taskForm.assigneeId||null,dueDate:taskForm.dueDate||null});tasks.value.unshift(task);Object.assign(taskForm,{title:'',description:'',status:'TODO',priority:'MEDIUM',assigneeId:'',dueDate:''});showSuccess('任务已创建，负责人已自动收到通知');await loadNotifications()}catch(e){showActionFailure(e,'任务创建失败，请检查填写内容')}}
async function saveTask(task:Task){try{const updated=task.canManage?await api.updateTask(task.id,{title:task.title,description:task.description||'',status:task.status,priority:task.priority,assigneeId:task.assigneeId||null,dueDate:task.dueDate||null}):await api.updateTaskProgress(task.id,task.status);Object.assign(task,updated);showSuccess(`任务“${task.title}”已更新，相关人员已自动收到通知`);await loadNotifications()}catch(e){showActionFailure(e,'任务修改失败，请刷新后重试')}}
async function createDocumentRequest(){try{const item=await api.createDocumentRequest({...documentForm,projectId:selectedProjectId.value,assigneeId:documentForm.assigneeId||null,dueDate:documentForm.dueDate||null});documentRequests.value.unshift(item);Object.assign(documentForm,{taskId:'',title:'',description:'',status:'REQUESTED',priority:'MEDIUM',assigneeId:'',dueDate:''});showSuccess('材料需求已创建，材料提供人已自动收到通知');await loadNotifications()}catch(e){showActionFailure(e,'材料需求创建失败，请检查填写内容')}}
async function saveDocumentRequest(item:DocumentRequest){try{const updated=item.canManage?await api.updateDocumentRequest(item.id,{title:item.title,description:item.description||'',status:item.status,priority:item.priority,assigneeId:item.assigneeId||null,dueDate:item.dueDate||null,deliveryNote:item.deliveryNote||''}):await api.deliverDocumentRequest(item.id,item.status,item.deliveryNote||'');Object.assign(item,updated);showSuccess(`材料“${item.title}”已更新，相关人员已自动收到通知`);await loadNotifications()}catch(e){showActionFailure(e,'材料修改失败，请刷新后重试')}}
async function readNotification(item:Notification){if(item.readAt)return;try{Object.assign(item,await api.readNotification(item.id));unreadCount.value=Math.max(0,unreadCount.value-1)}catch(e){showActionFailure(e,'通知状态更新失败，请稍后重试')}}
async function readAllNotifications(){try{await api.readAllNotifications();await loadNotifications()}catch(e){showActionFailure(e,'通知状态更新失败，请稍后重试')}}
async function createProject(){ try { const p=await api.createProject({...projectForm,managerId:projectForm.managerId||null}); projects.value.push(p); selectedProjectId.value=p.id; Object.assign(projectForm,{name:'',code:'',description:'',managerId:''}); showSuccess('项目已创建并完成管理员指派') } catch(e){showActionFailure(e,'项目创建失败，请检查填写内容')} }
async function saveProject(project:Project){try{const updated=await api.updateProject(project.id,{name:project.name,description:project.description||'',managerId:project.managerId||null});Object.assign(project,updated);showSuccess(`项目“${project.name}”已更新`)}catch(e){showActionFailure(e,'项目修改失败，请检查填写内容')}}
async function loadMembers(){ if(!selectedProjectId.value)return; try{members.value=await api.members(selectedProjectId.value)}catch(e){error.value=e instanceof Error?e.message:String(e)} }
async function setMember(){try{await api.setMember(selectedProjectId.value,memberForm.userId,memberForm.role);showSuccess('项目角色已保存');await loadMembers()}catch(e){showActionFailure(e,'人员分配失败，请检查当前项目状态')}}
async function removeMember(member:Member){if(!confirm(`确认从项目中移除 ${member.loginName}？`))return;try{await api.removeMember(selectedProjectId.value,member.userId);showSuccess(`${member.loginName} 已从项目移除`);await loadMembers()}catch(e){showActionFailure(e,'人员移除失败，请先检查其项目职责')}}
async function runAgent(){if(!selectedProjectId.value||!agentForm.message)return;agentBusy.value=true;error.value='';try{agentResult.value=await api.runAgent({projectId:selectedProjectId.value,message:agentForm.message})}catch(e){error.value=e instanceof Error?e.message:String(e)}finally{agentBusy.value=false}}
onMounted(async()=>{await load(); await Promise.all([loadResources(),loadTasks(),loadDocumentRequests(),loadProjectDocuments(),loadMembers(),loadNotifications()]);notificationTimer=window.setInterval(loadNotifications,30000)})
onUnmounted(()=>{if(notificationTimer)window.clearInterval(notificationTimer)})
</script>

<template>
  <div v-if="loading" class="center">正在加载…</div>
  <main v-else-if="!me" class="login-card">
    <div class="brand-mark">P</div><h1>项目协作中枢</h1><p>文档、任务、人员、代码、环境与部署的统一工作台。</p>
    <form class="local-login" @submit.prevent="localSignIn">
      <label>账号<input v-model.trim="localLoginForm.username" autocomplete="username" required/></label>
      <label>密码<input v-model="localLoginForm.password" type="password" autocomplete="current-password" required/></label>
      <button class="primary" :disabled="loginBusy">{{loginBusy?'登录中…':'账号密码登录'}}</button>
    </form>
    <div class="login-divider"><span>或</span></div>
    <a class="primary button" href="/oauth2/authorization/github">使用 GitHub 登录</a>
    <p v-if="error" class="error">{{ error }}</p>
  </main>
  <main v-else-if="me.systemRole==='PENDING' || !me.active" class="login-card">
    <div class="brand-mark">P</div><h1>账号等待审批</h1><p>账号 <strong>{{me.loginName}}</strong> 尚未启用。请联系系统管理员完成用户分级和项目授权。</p><button class="ghost button" @click="signOut">退出登录</button>
  </main>
  <div v-else class="shell">
    <aside>
      <div class="brand"><span class="brand-mark small">P</span><div><strong>项目协作中枢</strong><small>{{ me.loginName }} · {{ me.systemRole }}</small></div></div>
      <button :class="{active:tab==='resources'}" @click="tab='resources'">资源配置</button>
      <button :class="{active:tab==='agents'}" @click="tab='agents'">Agent 工作台</button>
      <button :class="{active:tab==='tasks'}" @click="tab='tasks'">任务协作</button>
      <button :class="{active:tab==='documents'}" @click="tab='documents'">材料需求</button>
      <button :class="{active:tab==='project-documents'}" @click="tab='project-documents';loadProjectDocuments()">项目文档</button>
      <button :class="{active:tab==='notifications'}" @click="tab='notifications';loadNotifications()">通知中心 <span v-if="unreadCount" class="badge">{{unreadCount}}</span></button>
      <button v-if="isAdmin" :class="{active:tab==='projects'}" @click="tab='projects'">项目与人员管理</button>
      <button v-if="isAdmin" :class="{active:tab==='users'}" @click="tab='users'">用户分级</button>
      <button class="logout logout-button" @click="signOut">退出登录</button>
    </aside>
    <section class="content">
      <header><div><span class="eyebrow">PROJECT OPERATIONS</span><h1>{{ tab==='resources'?'运行资源配置':tab==='users'?'用户分级管理':tab==='agents'?'智能协作工作台':tab==='tasks'?'任务协作':tab==='documents'?'材料需求':tab==='project-documents'?'项目文档':tab==='notifications'?'通知中心':'项目管理' }}</h1></div><select v-if="['resources','agents','tasks','documents','project-documents'].includes(tab)" v-model="selectedProjectId"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.code}} · {{p.name}}</option></select></header>
      <p v-if="notice" class="notice" :class="noticeKind" @click="notice=''">{{notice}}</p><p v-if="error" class="error">{{error}}</p>

      <template v-if="tab==='resources'">
        <div class="grid two" :class="{single:!canManageResources}">
          <form v-if="canManageResources" class="panel" @submit.prevent="saveResource">
            <div class="panel-title"><h2>新增资源</h2><span>敏感字段将加密</span></div>
            <fieldset>
            <label>资源名称<input v-model="resourceForm.name" required placeholder="例如：生产数据库"/></label>
            <div class="form-grid"><label>类型<select v-model="resourceForm.resourceType"><option v-for="t in ['SERVER','DATABASE','API','GITHUB_APP','SLACK_APP','SMTP','MODEL_PROVIDER','STORAGE','CI_CD','OTHER']" :key="t">{{t}}</option></select></label><label>环境<input v-model="resourceForm.environment" placeholder="production"/></label></div>
            <label>访问地址<input v-model="resourceForm.endpoint" placeholder="https://service.example.com"/></label>
            <div class="form-grid"><label>主机/IP<input v-model="resourceForm.host" placeholder="10.0.0.10"/></label><label>端口<input v-model.number="resourceForm.port" type="number" min="1" max="65535"/></label></div>
            <label>账号<input v-model="resourceForm.account" autocomplete="off"/></label>
            <label>密码/密钥<input v-model="resourceForm.secret" type="password" autocomplete="new-password"/></label>
            <label>Token<input v-model="resourceForm.token" type="password" autocomplete="new-password"/></label>
            <label>说明<textarea v-model="resourceForm.notes" rows="3"/></label>
            <button class="primary" :disabled="!selectedProjectId">加密保存资源</button>
            </fieldset>
          </form>
          <div class="panel">
            <div class="panel-title"><h2>已配置资源</h2><span>{{resources.length}} 项</span></div>
            <div v-if="!resources.length" class="empty">尚未配置资源</div>
            <article v-for="r in resources" :key="r.id" class="resource-card">
              <div><span class="tag">{{r.resourceType}}</span><span class="muted">{{r.environment}}</span><h3>{{r.name}}</h3><p>{{r.endpoint || [r.host,r.port].filter(Boolean).join(':') || '未设置地址'}}</p><small>账号 {{r.hasAccount?'••••':'未设置'}} · 密码 {{r.hasSecret?'••••':'未设置'}} · Token {{r.hasToken?'••••':'未设置'}}</small></div>
              <div class="card-actions"><button v-if="canManageResources" class="ghost" @click="editResource(r)">修改</button><button class="ghost" @click="showResourceHistory(r)">版本记录</button><button v-if="canRevealSecrets" class="ghost" @click="reveal(r)">查看密钥</button></div>
            </article>
          </div>
        </div>
      </template>

      <template v-if="tab==='agents'">
        <div class="grid two">
          <form class="panel" @submit.prevent="runAgent">
            <div class="panel-title"><h2>描述你的需求</h2><span>系统自动选择专业 Agent</span></div>
            <p class="muted routing-note">无需选择智能体。协调器会根据请求内容自动路由，并在结果中说明实际处理的 Agent。</p>
            <label>请求<textarea v-model="agentForm.message" rows="9" required placeholder="例如：检查下周版本还缺哪些上线材料，并生成催办计划。"/></label>
            <button class="primary" :disabled="agentBusy||!selectedProjectId">{{agentBusy?'自动路由并分析中…':'提交给智能协作系统'}}</button>
          </form>
          <div class="panel">
            <div class="panel-title"><h2>运行结果</h2><span>{{agentResult?.agent||'等待请求'}}</span></div>
            <div v-if="!agentResult" class="empty">Agent结果将在这里显示</div>
            <template v-else><h3>{{agentResult.summary}}</h3><h4>发现</h4><ul><li v-for="f in agentResult.findings" :key="f">{{f}}</li></ul><template v-if="agentResult.downloads?.length"><h4>文档下载</h4><a v-for="d in agentResult.downloads" :key="d.document_id" class="download-card" :href="d.url" download><strong>{{d.name}}</strong><span>{{formatBytes(d.size_bytes)}} · 下载</span></a></template><h4>建议动作</h4><article v-for="a in agentResult.proposed_actions" :key="a.tool" class="action-card"><div><strong>{{a.tool}}</strong><p>{{a.reason}}</p></div><span class="tag">{{a.risk}}</span></article></template>
          </div>
        </div>
      </template>

      <template v-if="tab==='documents'">
        <div class="grid two" :class="{single:!myAssignedTasks.length}">
          <form v-if="myAssignedTasks.length" class="panel" @submit.prevent="createDocumentRequest">
            <div class="panel-title"><h2>新建材料需求</h2><span>创建与完成均自动通知</span></div>
            <fieldset>
            <label>所属任务<select v-model="documentForm.taskId" required><option value="">选择我负责的任务</option><option v-for="task in myAssignedTasks" :key="task.id" :value="task.id">{{task.title}} · {{task.status}}</option></select></label>
            <label>材料名称<input v-model.trim="documentForm.title" maxlength="255" required placeholder="例如：生产上线审批单"/></label>
            <label>材料要求<textarea v-model="documentForm.description" rows="5" placeholder="说明格式、内容、交付方式等"/></label>
            <div class="form-grid"><label>状态<select v-model="documentForm.status"><option v-for="s in ['REQUESTED','IN_PROGRESS','SUBMITTED','DONE','CANCELLED']" :key="s">{{s}}</option></select></label><label>优先级<select v-model="documentForm.priority"><option v-for="p in ['LOW','MEDIUM','HIGH','URGENT']" :key="p">{{p}}</option></select></label></div>
            <label>材料提供人<select v-model="documentForm.assigneeId" required><option value="">选择提供材料的项目成员</option><option v-for="m in members.filter(x=>x.userId!==me?.id)" :key="m.userId" :value="m.userId">{{m.loginName}} · {{m.role}}</option></select></label>
            <label>截止日期<input v-model="documentForm.dueDate" type="date"/></label>
            <button class="primary" :disabled="!selectedProjectId||!documentForm.taskId||!documentForm.assigneeId">创建并通知</button>
            </fieldset>
          </form>
          <div class="panel task-list">
            <div class="panel-title"><h2>材料需求列表</h2><span>{{documentRequests.length}} 项</span></div>
            <div v-if="!documentRequests.length" class="empty">当前项目暂无材料需求</div>
            <article v-for="item in documentRequests" :key="item.id" class="task-card" :class="{locked:!item.canManage&&!item.canDeliver}">
              <span class="tag">任务：{{item.taskTitle||'历史未关联'}}</span>
              <input v-model="item.title" class="task-title" maxlength="255" :disabled="!item.canManage"/>
              <textarea v-model="item.description" rows="2" placeholder="材料要求" :disabled="!item.canManage"/>
              <textarea v-model="item.deliveryNote" rows="2" placeholder="交付地址、文件链接或完成说明" :disabled="!item.canManage&&!item.canDeliver"/>
              <div class="task-fields"><select v-model="item.status" :disabled="!item.canManage&&!item.canDeliver"><option v-for="s in ['REQUESTED','IN_PROGRESS','SUBMITTED','DONE','CANCELLED']" :key="s">{{s}}</option></select><select v-model="item.priority" :disabled="!item.canManage"><option v-for="p in ['LOW','MEDIUM','HIGH','URGENT']" :key="p">{{p}}</option></select><select v-model="item.assigneeId" :disabled="!item.canManage"><option v-for="m in members" :key="m.userId" :value="m.userId">{{m.loginName}}</option></select><input v-model="item.dueDate" type="date" :disabled="!item.canManage"/><button v-if="item.canManage||item.canDeliver" class="ghost" @click="saveDocumentRequest(item)">{{item.canDeliver&&!item.canManage?'提交材料':'保存并通知'}}</button></div>
              <small>需求人 {{item.requesterLogin||'未知'}} · 材料提供人 {{item.assigneeLogin||'未知'}} · 最近更新 {{new Date(item.updatedAt).toLocaleString()}}</small>
            </article>
          </div>
        </div>
      </template>

      <template v-if="tab==='project-documents'">
        <div class="grid two" :class="{single:!canUploadDocuments}">
          <form v-if="canUploadDocuments" class="panel" @submit.prevent="uploadProjectDocument">
            <div class="panel-title"><h2>上传项目文档</h2><span>最大 100 MB</span></div>
            <label>文件<input id="project-document-file" type="file" required @change="selectDocumentFile"/></label>
            <label>显示名称<input v-model.trim="projectDocumentForm.displayName" maxlength="255" placeholder="默认使用原始文件名"/></label>
            <label>文档说明<textarea v-model="projectDocumentForm.description" rows="5" placeholder="用途、版本、适用环境等"/></label>
            <button class="primary" :disabled="!documentFile||documentUploading">{{documentUploading?'上传中…':'上传文档'}}</button>
          </form>
          <div class="panel document-list">
            <div class="panel-title"><h2>项目文档库</h2><span>{{projectDocuments.length}} 份</span></div>
            <div v-if="!projectDocuments.length" class="empty">当前项目暂无文档</div>
            <article v-for="doc in projectDocuments" :key="doc.id" class="document-card">
              <div><span class="tag">{{doc.contentType||'FILE'}}</span><h3>{{doc.displayName}}</h3><p>{{doc.description||doc.originalName}}</p><small>{{formatBytes(doc.sizeBytes)}} · 上传人 {{doc.uploaderLogin||'未知'}} · {{new Date(doc.createdAt).toLocaleString()}}</small><small class="checksum">SHA-256 {{doc.sha256}}</small></div>
              <a class="ghost button-link" :href="doc.downloadUrl" download>下载</a>
            </article>
          </div>
        </div>
      </template>

      <template v-if="tab==='notifications'">
        <div class="panel notification-list">
          <div class="panel-title"><h2>我的通知</h2><button class="ghost" :disabled="!unreadCount" @click="readAllNotifications">全部标为已读</button></div>
          <div v-if="!notifications.length" class="empty">暂无通知</div>
          <article v-for="item in notifications" :key="item.id" class="notification-card" :class="{unread:!item.readAt}" @click="readNotification(item)">
            <div><span class="tag">{{item.notificationType}}</span><h3>{{item.title}}</h3><p>{{item.message}}</p><small>{{new Date(item.createdAt).toLocaleString()}} · {{item.deliveryDetail||item.deliveryStatus}}</small></div>
            <span class="read-state">{{item.readAt?'已读':'未读'}}</span>
          </article>
        </div>
      </template>

      <template v-if="tab==='tasks'">
        <div class="grid two" :class="{single:!canManageTasks}">
          <form v-if="canManageTasks" class="panel" @submit.prevent="createTask">
            <div class="panel-title"><h2>新建任务</h2><span>任务与项目成员联动</span></div>
            <fieldset>
            <label>任务标题<input v-model.trim="taskForm.title" maxlength="255" required/></label>
            <label>任务说明<textarea v-model="taskForm.description" rows="5"/></label>
            <div class="form-grid"><label>状态<select v-model="taskForm.status"><option v-for="s in ['BACKLOG','TODO','IN_PROGRESS','BLOCKED','IN_REVIEW','DONE']" :key="s">{{s}}</option></select></label><label>优先级<select v-model="taskForm.priority"><option v-for="p in ['LOW','MEDIUM','HIGH','URGENT']" :key="p">{{p}}</option></select></label></div>
            <label>负责人<select v-model="taskForm.assigneeId"><option value="">暂不分配</option><option v-for="m in members" :key="m.userId" :value="m.userId">{{m.loginName}} · {{m.role}}</option></select></label>
            <label>截止日期<input v-model="taskForm.dueDate" type="date"/></label>
            <button class="primary" :disabled="!selectedProjectId">创建任务</button>
            </fieldset>
          </form>
          <div class="panel task-list">
            <div class="panel-title"><h2>任务列表</h2><span>{{tasks.length}} 项</span></div>
            <div v-if="!tasks.length" class="empty">当前项目暂无任务</div>
            <article v-for="task in tasks" :key="task.id" class="task-card" :class="{locked:!task.canManage&&!task.canUpdateProgress}">
              <input v-model="task.title" class="task-title" maxlength="255" :disabled="!task.canManage"/>
              <textarea v-model="task.description" rows="2" placeholder="任务说明" :disabled="!task.canManage"/>
              <div class="task-fields"><select v-model="task.status" :disabled="!task.canUpdateProgress"><option v-for="s in ['BACKLOG','TODO','IN_PROGRESS','BLOCKED','IN_REVIEW','DONE']" :key="s">{{s}}</option></select><select v-model="task.priority" :disabled="!task.canManage"><option v-for="p in ['LOW','MEDIUM','HIGH','URGENT']" :key="p">{{p}}</option></select><select v-model="task.assigneeId" :disabled="!task.canManage"><option value="">暂不分配</option><option v-for="m in members" :key="m.userId" :value="m.userId">{{m.loginName}}</option></select><input v-model="task.dueDate" type="date" :disabled="!task.canManage"/><button v-if="task.canManage||task.canUpdateProgress" class="ghost" @click="saveTask(task)">{{task.canManage?'保存任务':'更新进度'}}</button></div>
              <small>负责人 {{task.assigneeLogin||'未分配'}} · 材料需求 {{task.documentRequestCount}} 项 · 最近更新 {{new Date(task.updatedAt).toLocaleString()}}</small>
            </article>
          </div>
        </div>
      </template>

      <template v-if="tab==='users' && isAdmin">
        <div class="grid two">
          <form class="panel" @submit.prevent="createLocalUser">
            <div class="panel-title"><h2>创建本地账号</h2><span>管理员分配</span></div>
            <label>登录账号<input v-model.trim="createUserForm.username" pattern="[A-Za-z0-9._-]{3,64}" required placeholder="例如：zhangsan"/></label>
            <label>初始密码<input v-model="createUserForm.password" type="password" minlength="10" maxlength="128" autocomplete="new-password" required/></label>
            <label>显示名<input v-model.trim="createUserForm.displayName" maxlength="255"/></label>
            <label>邮箱<input v-model.trim="createUserForm.email" type="email" maxlength="320"/></label>
            <label>系统等级<select v-model="createUserForm.systemRole"><option v-for="r in ['SUPER_ADMIN','SYSTEM_ADMIN','STANDARD','READ_ONLY','PENDING']" :key="r">{{r}}</option></select></label>
            <label class="check-label"><input v-model="createUserForm.active" type="checkbox"/>创建后立即启用</label>
            <button class="primary">创建账号</button>
          </form>
          <div class="panel user-list"><div class="panel-title"><h2>用户访问等级</h2><span>{{users.length}} 个账号</span></div>
            <table><thead><tr><th>账号</th><th>显示名</th><th>系统等级</th><th>启用</th><th>密码</th><th></th></tr></thead><tbody><tr v-for="u in users" :key="u.id"><td><strong>{{u.loginName}}</strong><small>{{u.authType==='LOCAL'?'本地账号':'GitHub'}} · {{u.email||'无邮箱'}}</small></td><td>{{u.displayName||'—'}}</td><td><select v-model="u.systemRole"><option v-for="r in ['SUPER_ADMIN','SYSTEM_ADMIN','STANDARD','READ_ONLY','PENDING']" :key="r">{{r}}</option></select></td><td><input v-model="u.active" type="checkbox"/></td><td><div v-if="u.authType==='LOCAL'" class="password-reset"><input v-model="passwordResets[u.id]" type="password" minlength="10" placeholder="新密码"/><button class="ghost" :disabled="!(passwordResets[u.id]||'').length" @click="resetPassword(u)">重置</button></div><span v-else class="muted">由 GitHub 管理</span></td><td><button class="ghost" @click="saveUser(u)">保存</button></td></tr></tbody></table>
          </div>
        </div>
      </template>

      <template v-if="tab==='projects' && isAdmin">
        <div class="grid two"><form class="panel" @submit.prevent="createProject"><div class="panel-title"><h2>新建项目</h2><span>超级管理员职责</span></div><label>项目名称<input v-model="projectForm.name" required/></label><label>项目代码<input v-model="projectForm.code" pattern="[A-Za-z0-9_-]{2,64}" required/></label><label>项目管理员<select v-model="projectForm.managerId"><option value="">稍后指派</option><option v-for="u in users.filter(x=>x.active&&!['READ_ONLY','PENDING'].includes(x.systemRole))" :key="u.id" :value="u.id">{{u.loginName}}</option></select></label><label>说明<textarea v-model="projectForm.description" rows="4"/></label><button class="primary">创建项目</button></form><div class="panel"><div class="panel-title"><h2>项目与管理员</h2></div><article v-for="p in projects" :key="p.id" class="resource-card"><div><span class="tag">{{p.code}}</span><input v-model="p.name" class="task-title"/><textarea v-model="p.description" rows="2" placeholder="项目说明"/><label>项目管理员<select v-model="p.managerId"><option value="">未指派</option><option v-for="u in users.filter(x=>x.active&&!['READ_ONLY','PENDING'].includes(x.systemRole))" :key="u.id" :value="u.id">{{u.loginName}}</option></select></label></div><div><button class="ghost" @click="saveProject(p)">保存项目</button><button class="ghost" @click="selectedProjectId=p.id;loadMembers()">管理人员</button></div></article></div></div>
        <div class="panel members"><div class="panel-title"><h2>项目人员增减</h2><select v-model="selectedProjectId" @change="loadMembers"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.code}} · {{p.name}}</option></select></div><form class="member-form" @submit.prevent="setMember"><label>用户<select v-model="memberForm.userId" required><option value="">选择已启用用户</option><option v-for="u in users.filter(x=>x.active)" :key="u.id" :value="u.id">{{u.loginName}}</option></select></label><label>项目角色<select v-model="memberForm.role"><option v-for="r in ['DEVELOPER','TESTER','OPERATIONS','VIEWER']" :key="r">{{r}}</option></select></label><button class="primary" :disabled="!selectedProjectId">增加或更新人员</button></form><table><thead><tr><th>成员</th><th>角色</th><th>操作</th></tr></thead><tbody><tr v-for="m in members" :key="m.userId"><td>{{m.loginName}}</td><td><span class="tag">{{m.role}}</span></td><td><button class="ghost" @click="removeMember(m)">移除</button></td></tr></tbody></table></div>
      </template>
    </section>
    <div v-if="secretDialog" class="modal-backdrop" @click.self="secretDialog=null"><div class="modal"><h2>{{secretDialog.name}}</h2><p class="warning">本次查看已记录到审计日志，请勿复制到聊天或普通文档。</p><label>账号<code>{{secretDialog.account||'未设置'}}</code></label><label>密码/密钥<code>{{secretDialog.secret||'未设置'}}</code></label><label>Token<code>{{secretDialog.token||'未设置'}}</code></label><button class="primary" @click="secretDialog=null">关闭</button></div></div>
    <div v-if="editingResource" class="modal-backdrop" @click.self="editingResource=null"><form class="modal" @submit.prevent="updateResource"><h2>修改资源</h2><p class="muted">留空账号、密码或 Token 将保留当前密文。保存后自动生成新版本。</p><label>资源名称<input v-model="resourceEditForm.name" required/></label><div class="form-grid"><label>类型<select v-model="resourceEditForm.resourceType"><option v-for="t in ['SERVER','DATABASE','API','GITHUB_APP','SLACK_APP','SMTP','MODEL_PROVIDER','STORAGE','CI_CD','OTHER']" :key="t">{{t}}</option></select></label><label>环境<input v-model="resourceEditForm.environment"/></label></div><label>访问地址<input v-model="resourceEditForm.endpoint"/></label><div class="form-grid"><label>主机/IP<input v-model="resourceEditForm.host"/></label><label>端口<input v-model.number="resourceEditForm.port" type="number" min="1" max="65535"/></label></div><label>账号<input v-model="resourceEditForm.account" autocomplete="off"/></label><label>密码/密钥<input v-model="resourceEditForm.secret" type="password" autocomplete="new-password"/></label><label>Token<input v-model="resourceEditForm.token" type="password" autocomplete="new-password"/></label><label>说明<textarea v-model="resourceEditForm.notes" rows="3"/></label><div class="modal-actions"><button type="button" class="ghost" @click="editingResource=null">取消</button><button class="primary">保存新版本</button></div></form></div>
    <div v-if="resourceHistoryName" class="modal-backdrop" @click.self="resourceHistoryName=''" ><div class="modal history-modal"><div class="panel-title"><h2>{{resourceHistoryName}} · 版本记录</h2><button class="ghost" @click="resourceHistoryName=''">关闭</button></div><div v-if="!resourceVersions.length" class="empty">暂无版本记录</div><article v-for="version in resourceVersions" :key="version.version" class="version-card"><div><span class="tag">v{{version.version}}</span><strong>{{version.name}}</strong><p>{{version.endpoint||[version.host,version.port].filter(Boolean).join(':')||'未设置地址'}}</p><small>{{version.changedByLogin||'未知用户'}} · {{new Date(version.changedAt).toLocaleString()}}</small></div><small>{{version.resourceType}} · {{version.environment||'未指定环境'}}<br/>密钥状态：{{version.hasSecret||version.hasToken||version.hasAccount?'已配置':'未配置'}}</small></article></div></div>
  </div>
</template>
