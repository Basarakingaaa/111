<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type AgentResult, type Me, type Member, type Project, type Resource, type User } from './api'

const me = ref<Me|null>(null), loading = ref(true), error = ref(''), tab = ref('resources')
const projects = ref<Project[]>([]), selectedProjectId = ref(''), resources = ref<Resource[]>([]), users = ref<User[]>([])
const members = ref<Member[]>([]), agentResult = ref<AgentResult|null>(null), agentBusy = ref(false)
const notice = ref(''), secretDialog = ref<{name:string;account?:string;secret?:string;token?:string}|null>(null)
const resourceForm = reactive({ name:'', resourceType:'SERVER', environment:'development', endpoint:'', host:'', port:undefined as number|undefined, account:'', secret:'', token:'', notes:'' })
const projectForm = reactive({name:'',code:'',description:''})
const memberForm = reactive({userId:'',role:'DEVELOPER'})
const agentForm = reactive({requestedAgent:'coordinator',message:''})
const isAdmin = computed(() => ['SUPER_ADMIN','SYSTEM_ADMIN'].includes(me.value?.systemRole || ''))

async function load() {
  loading.value = true; error.value = ''
  try {
    me.value = await api.me(); projects.value = await api.projects()
    if (!selectedProjectId.value && projects.value.length) selectedProjectId.value = projects.value[0].id
    users.value = isAdmin.value ? await api.users() : await api.userDirectory()
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) }
  finally { loading.value = false }
}
async function loadResources() { if (!selectedProjectId.value) { resources.value=[]; return }; try { resources.value=await api.resources(selectedProjectId.value) } catch(e){ error.value=String(e) } }
watch(selectedProjectId, loadResources)
async function saveResource() {
  error.value=''; try { await api.createResource({...resourceForm,projectId:selectedProjectId.value,port:resourceForm.port||null}); notice.value='资源已加密保存'; Object.assign(resourceForm,{name:'',resourceType:'SERVER',environment:'development',endpoint:'',host:'',port:undefined,account:'',secret:'',token:'',notes:''}); await loadResources() } catch(e){error.value=e instanceof Error?e.message:String(e)}
}
async function reveal(r:Resource) { try { const s=await api.revealResource(r.id); secretDialog.value={name:r.name,...s} } catch(e){error.value=e instanceof Error?e.message:String(e)} }
async function saveUser(u:User) { try { await api.updateUser(u.id,{systemRole:u.systemRole,active:u.active}); notice.value=`${u.githubLogin} 已更新` } catch(e){error.value=e instanceof Error?e.message:String(e); await load()} }
async function createProject(){ try { const p=await api.createProject(projectForm); projects.value.push(p); selectedProjectId.value=p.id; Object.assign(projectForm,{name:'',code:'',description:''}); notice.value='项目已创建' } catch(e){error.value=e instanceof Error?e.message:String(e)} }
async function loadMembers(){ if(!selectedProjectId.value)return; try{members.value=await api.members(selectedProjectId.value)}catch(e){error.value=e instanceof Error?e.message:String(e)} }
async function setMember(){try{await api.setMember(selectedProjectId.value,memberForm.userId,memberForm.role);notice.value='项目角色已保存';await loadMembers()}catch(e){error.value=e instanceof Error?e.message:String(e)}}
async function runAgent(){if(!selectedProjectId.value||!agentForm.message)return;agentBusy.value=true;error.value='';try{agentResult.value=await api.runAgent({projectId:selectedProjectId.value,message:agentForm.message,requestedAgent:agentForm.requestedAgent})}catch(e){error.value=e instanceof Error?e.message:String(e)}finally{agentBusy.value=false}}
onMounted(async()=>{await load(); await loadResources()})
</script>

<template>
  <div v-if="loading" class="center">正在加载…</div>
  <main v-else-if="!me" class="login-card">
    <div class="brand-mark">P</div><h1>项目协作中枢</h1><p>文档、任务、人员、代码、环境与部署的统一工作台。</p>
    <a class="primary button" href="/oauth2/authorization/github">使用 GitHub 登录</a>
    <p v-if="error" class="error">{{ error }}</p>
  </main>
  <main v-else-if="me.systemRole==='PENDING' || !me.active" class="login-card">
    <div class="brand-mark">P</div><h1>账号等待审批</h1><p>你的 GitHub 账号 <strong>{{me.githubLogin}}</strong> 已登记。请联系系统管理员完成用户分级和启用。</p><a class="ghost button" href="/logout">退出登录</a>
  </main>
  <div v-else class="shell">
    <aside>
      <div class="brand"><span class="brand-mark small">P</span><div><strong>项目协作中枢</strong><small>{{ me.githubLogin }} · {{ me.systemRole }}</small></div></div>
      <button :class="{active:tab==='resources'}" @click="tab='resources'">资源配置</button>
      <button :class="{active:tab==='agents'}" @click="tab='agents'">Agent 工作台</button>
      <button :class="{active:tab==='projects'}" @click="tab='projects'">项目管理</button>
      <button v-if="isAdmin" :class="{active:tab==='users'}" @click="tab='users'">用户分级</button>
      <a class="logout" href="/logout">退出登录</a>
    </aside>
    <section class="content">
      <header><div><span class="eyebrow">PROJECT OPERATIONS</span><h1>{{ tab==='resources'?'运行资源配置':tab==='users'?'用户分级管理':tab==='agents'?'多智能体工作台':'项目管理' }}</h1></div><select v-if="['resources','agents'].includes(tab)" v-model="selectedProjectId"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.code}} · {{p.name}}</option></select></header>
      <p v-if="notice" class="notice" @click="notice=''">{{notice}}</p><p v-if="error" class="error">{{error}}</p>

      <template v-if="tab==='resources'">
        <div class="grid two">
          <form class="panel" @submit.prevent="saveResource">
            <div class="panel-title"><h2>新增资源</h2><span>敏感字段将加密</span></div>
            <label>资源名称<input v-model="resourceForm.name" required placeholder="例如：生产数据库"/></label>
            <div class="form-grid"><label>类型<select v-model="resourceForm.resourceType"><option v-for="t in ['SERVER','DATABASE','API','GITHUB_APP','SLACK_APP','SMTP','MODEL_PROVIDER','STORAGE','CI_CD','OTHER']" :key="t">{{t}}</option></select></label><label>环境<input v-model="resourceForm.environment" placeholder="production"/></label></div>
            <label>访问地址<input v-model="resourceForm.endpoint" placeholder="https://service.example.com"/></label>
            <div class="form-grid"><label>主机/IP<input v-model="resourceForm.host" placeholder="10.0.0.10"/></label><label>端口<input v-model.number="resourceForm.port" type="number" min="1" max="65535"/></label></div>
            <label>账号<input v-model="resourceForm.account" autocomplete="off"/></label>
            <label>密码/密钥<input v-model="resourceForm.secret" type="password" autocomplete="new-password"/></label>
            <label>Token<input v-model="resourceForm.token" type="password" autocomplete="new-password"/></label>
            <label>说明<textarea v-model="resourceForm.notes" rows="3"/></label>
            <button class="primary" :disabled="!selectedProjectId">加密保存资源</button>
          </form>
          <div class="panel">
            <div class="panel-title"><h2>已配置资源</h2><span>{{resources.length}} 项</span></div>
            <div v-if="!resources.length" class="empty">尚未配置资源</div>
            <article v-for="r in resources" :key="r.id" class="resource-card">
              <div><span class="tag">{{r.resourceType}}</span><span class="muted">{{r.environment}}</span><h3>{{r.name}}</h3><p>{{r.endpoint || [r.host,r.port].filter(Boolean).join(':') || '未设置地址'}}</p><small>账号 {{r.hasAccount?'••••':'未设置'}} · 密码 {{r.hasSecret?'••••':'未设置'}} · Token {{r.hasToken?'••••':'未设置'}}</small></div>
              <button class="ghost" @click="reveal(r)">按权限查看</button>
            </article>
          </div>
        </div>
      </template>

      <template v-if="tab==='agents'">
        <div class="grid two">
          <form class="panel" @submit.prevent="runAgent">
            <div class="panel-title"><h2>发起分析</h2><span>所有写操作需审批</span></div>
            <label>选择 Agent<select v-model="agentForm.requestedAgent"><option value="coordinator">项目协调 Agent</option><option value="document">材料文档 Agent</option><option value="task-progress">任务进度 Agent</option><option value="project-knowledge">项目知识 Agent</option><option value="environment-deployment">环境部署 Agent</option><option value="notification">通知催办 Agent</option><option value="permission-audit">权限审计 Agent</option></select></label>
            <label>请求<textarea v-model="agentForm.message" rows="9" required placeholder="例如：检查下周版本还缺哪些上线材料，并生成催办计划。"/></label>
            <button class="primary" :disabled="agentBusy||!selectedProjectId">{{agentBusy?'分析中…':'运行 Agent'}}</button>
          </form>
          <div class="panel">
            <div class="panel-title"><h2>运行结果</h2><span>{{agentResult?.agent||'等待请求'}}</span></div>
            <div v-if="!agentResult" class="empty">Agent结果将在这里显示</div>
            <template v-else><h3>{{agentResult.summary}}</h3><h4>发现</h4><ul><li v-for="f in agentResult.findings" :key="f">{{f}}</li></ul><h4>建议动作</h4><article v-for="a in agentResult.proposed_actions" :key="a.tool" class="action-card"><div><strong>{{a.tool}}</strong><p>{{a.reason}}</p></div><span class="tag">{{a.risk}}</span></article></template>
          </div>
        </div>
      </template>

      <template v-if="tab==='users' && isAdmin">
        <div class="panel"><div class="panel-title"><h2>用户访问等级</h2><span>新用户默认待审批</span></div>
          <table><thead><tr><th>GitHub用户</th><th>显示名</th><th>系统等级</th><th>启用</th><th></th></tr></thead><tbody><tr v-for="u in users" :key="u.id"><td><strong>{{u.githubLogin}}</strong><small>{{u.email}}</small></td><td>{{u.displayName||'—'}}</td><td><select v-model="u.systemRole"><option v-for="r in ['SUPER_ADMIN','SYSTEM_ADMIN','STANDARD','READ_ONLY','PENDING']" :key="r">{{r}}</option></select></td><td><input v-model="u.active" type="checkbox"/></td><td><button class="ghost" @click="saveUser(u)">保存</button></td></tr></tbody></table>
        </div>
      </template>

      <template v-if="tab==='projects'">
        <div class="grid two"><form class="panel" @submit.prevent="createProject"><div class="panel-title"><h2>新建项目</h2></div><label>项目名称<input v-model="projectForm.name" required/></label><label>项目代码<input v-model="projectForm.code" pattern="[A-Za-z0-9_-]{2,64}" required/></label><label>说明<textarea v-model="projectForm.description" rows="4"/></label><button class="primary">创建项目</button></form><div class="panel"><div class="panel-title"><h2>项目列表</h2></div><article v-for="p in projects" :key="p.id" class="resource-card"><div><span class="tag">{{p.code}}</span><h3>{{p.name}}</h3><p>{{p.description||'暂无说明'}}</p></div><button class="ghost" @click="selectedProjectId=p.id;loadMembers()">管理成员</button></article></div></div>
        <div class="panel members"><div class="panel-title"><h2>项目成员分级</h2><select v-model="selectedProjectId" @change="loadMembers"><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.code}} · {{p.name}}</option></select></div><form class="member-form" @submit.prevent="setMember"><label>用户<select v-model="memberForm.userId" required><option value="">选择已启用用户</option><option v-for="u in users.filter(x=>x.active)" :key="u.id" :value="u.id">{{u.githubLogin}}</option></select></label><label>项目角色<select v-model="memberForm.role"><option v-for="r in ['OWNER','MANAGER','DEVELOPER','TESTER','OPERATIONS','VIEWER']" :key="r">{{r}}</option></select></label><button class="primary" :disabled="!selectedProjectId">保存项目角色</button></form><table><thead><tr><th>成员</th><th>角色</th></tr></thead><tbody><tr v-for="m in members" :key="m.userId"><td>{{m.githubLogin}}</td><td><span class="tag">{{m.role}}</span></td></tr></tbody></table></div>
      </template>
    </section>
    <div v-if="secretDialog" class="modal-backdrop" @click.self="secretDialog=null"><div class="modal"><h2>{{secretDialog.name}}</h2><p class="warning">本次查看已记录到审计日志，请勿复制到聊天或普通文档。</p><label>账号<code>{{secretDialog.account||'未设置'}}</code></label><label>密码/密钥<code>{{secretDialog.secret||'未设置'}}</code></label><label>Token<code>{{secretDialog.token||'未设置'}}</code></label><button class="primary" @click="secretDialog=null">关闭</button></div></div>
  </div>
</template>
