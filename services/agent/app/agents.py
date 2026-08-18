from dataclasses import dataclass
from .models import AgentName, AgentRunRequest, AgentResult, ProposedAction, DocumentLink

@dataclass(frozen=True)
class AgentDefinition:
    name: AgentName
    title: str
    mission: str
    allowed_tools: tuple[str, ...]
    keywords: tuple[str, ...]

    def run(self, request: AgentRunRequest) -> AgentResult:
        findings: list[str] = []
        actions: list[ProposedAction] = []
        downloads: list[DocumentLink] = []
        project = request.context.get("project", {})
        project_name = project.get("name", "当前项目") if isinstance(project, dict) else "当前项目"
        task_stats = request.context.get("task_stats", {})
        document_stats = request.context.get("document_stats", {})
        project_document_stats = request.context.get("project_document_stats", {})
        project_documents = request.context.get("project_documents", [])
        message = request.message.lower()

        if self.name == "task-progress" and isinstance(task_stats, dict):
            total = int(task_stats.get("total", 0))
            summary = f"项目“{project_name}”当前共有 {total} 个任务。"
            by_status = task_stats.get("by_status", {})
            if isinstance(by_status, dict):
                active_statuses = [f"{status} {count} 个" for status, count in by_status.items() if int(count) > 0]
                if active_statuses:
                    findings.append("状态分布：" + "、".join(active_statuses) + "。")
            findings.append(f"已分配 {int(task_stats.get('assigned', 0))} 个，未分配 {int(task_stats.get('unassigned', 0))} 个。")
        elif self.name == "document" and isinstance(project_documents, list) and any(
            word in message for word in ("下载", "访问", "文件", "项目文档", "有哪些文档", "文档列表", "查看文档")
        ):
            total = int(project_document_stats.get("total", len(project_documents))) if isinstance(project_document_stats, dict) else len(project_documents)
            summary = f"项目“{project_name}”当前共有 {total} 个可访问的项目文档。"
            for document in project_documents[:20]:
                if not isinstance(document, dict):
                    continue
                name = str(document.get("display_name") or document.get("original_name") or "未命名文档")
                downloads.append(DocumentLink(
                    document_id=str(document.get("id", "")), name=name,
                    url=str(document.get("download_url", "")),
                    content_type=str(document.get("content_type", "application/octet-stream")),
                    size_bytes=int(document.get("size_bytes", 0))))
                description = document.get("description")
                excerpt = document.get("text_excerpt")
                detail = f"{name}（{int(document.get('size_bytes', 0))} 字节）"
                if description:
                    detail += f"：{description}"
                findings.append(detail)
                if excerpt and (name.lower() in message or "内容" in message or "访问" in message):
                    findings.append(f"{name} 内容摘录：{str(excerpt)[:1000]}")
            if not project_documents:
                findings.append("当前项目尚未上传文档，可由有文档上传权限的项目成员在“项目文档”页面添加。")
        elif self.name == "document" and isinstance(document_stats, dict):
            total = int(document_stats.get("total", 0))
            summary = f"项目“{project_name}”当前共有 {total} 个材料需求。"
            by_status = document_stats.get("by_status", {})
            if isinstance(by_status, dict):
                active_statuses = [f"{status} {count} 个" for status, count in by_status.items() if int(count) > 0]
                if active_statuses:
                    findings.append("状态分布：" + "、".join(active_statuses) + "。")
        elif self.name == "coordinator":
            task_total = int(task_stats.get("total", 0)) if isinstance(task_stats, dict) else 0
            document_total = int(document_stats.get("total", 0)) if isinstance(document_stats, dict) else 0
            summary = f"项目“{project_name}”当前有 {task_total} 个任务、{document_total} 个材料需求。"
            findings.append(f"项目成员 {int(request.context.get('member_count', 0))} 人，已登记资源 {int(request.context.get('resource_count', 0))} 项。")
        else:
            summary = f"{self.title}已读取项目“{project_name}”的实时业务数据并完成分析。"

        if not request.evidence:
            findings.append("当前请求没有附带可引用证据，需要先读取项目事实。")
            actions.append(ProposedAction(
                tool="context.search", reason="获取与当前请求相关且经过权限过滤的项目证据",
                parameters={"project_id": request.project_id, "query": request.message}, risk="read"))
        domain_reads = [tool for tool in self.allowed_tools if tool.endswith((".list", ".search", ".assess", ".check", ".explain")) and tool != "context.search"]
        if domain_reads:
            actions.append(ProposedAction(tool=domain_reads[0], reason=f"由{self.title}读取领域事实并交叉核对版本与时间", parameters={"project_id":request.project_id}, risk="read"))
        draft_requested = any(word in request.message.lower() for word in ("新建", "创建", "生成", "催", "通知", "更新", "修改", "分配", "推送"))
        draft_tools = [tool for tool in self.allowed_tools if ".draft" in tool]
        if draft_requested and draft_tools:
            actions.append(ProposedAction(tool=draft_tools[0], reason="先生成可审阅草稿，不直接改变项目事实", parameters={"project_id":request.project_id}, risk="low", requires_approval=True))
        return AgentResult(
            run_id=request.run_id, agent=self.name,
            summary=summary,
            findings=findings, proposed_actions=actions, downloads=downloads, evidence=request.evidence,
            assumptions=[], needs_human_input=False)

AGENTS: dict[AgentName, AgentDefinition] = {
    "coordinator": AgentDefinition(
        "coordinator", "项目协调 Agent", "识别意图、拆解跨领域工作并汇总专业 Agent 结果",
        ("context.search", "agent.delegate", "approval.request"),
        ("项目", "整体", "协调", "总结", "上线准备", "缺什么")),
    "document": AgentDefinition(
        "document", "材料文档 Agent", "管理材料要求、版本、审核、分发、回执和缺失项",
        ("context.search", "document.list", "document.requirement.draft", "document.delivery.draft"),
        ("材料", "文档", "合同", "方案", "报告", "提交", "审核", "转发")),
    "task-progress": AgentDefinition(
        "task-progress", "任务进度 Agent", "管理任务、负责人、依赖、里程碑、负载和交付预测",
        ("context.search", "task.list", "task.draft", "milestone.assess", "workload.assess"),
        ("任务", "进度", "负责人", "延期", "里程碑", "排期", "负载", "阻塞")),
    "project-knowledge": AgentDefinition(
        "project-knowledge", "项目知识 Agent", "持续构建 README、架构说明和可追溯的项目知识",
        ("context.search", "knowledge.search", "readme.patch.draft", "document.patch.draft"),
        ("readme", "知识", "架构", "说明", "怎么运行", "项目介绍", "代码结构")),
    "environment-deployment": AgentDefinition(
        "environment-deployment", "环境部署 Agent", "维护环境资产、构建、部署、验证、上线和回滚知识",
        ("context.search", "resource.list", "deployment.assess", "deployment.document.draft", "release.check"),
        ("环境", "部署", "上线", "回滚", "ip", "端口", "服务器", "数据库", "配置")),
    "notification": AgentDefinition(
        "notification", "通知催办 Agent", "生成最小必要披露的 Slack/邮件通知、催办和升级计划",
        ("context.search", "notification.draft", "reminder.schedule.draft"),
        ("通知", "提醒", "催", "slack", "邮件", "转发", "推送")),
    "permission-audit": AgentDefinition(
        "permission-audit", "权限审计 Agent", "解释和检查系统级、项目级及资源级权限与审计风险",
        ("authorization.explain", "audit.search", "permission.change.draft"),
        ("权限", "用户", "角色", "审计", "谁能看", "授权", "账号")),
}

class Coordinator:
    @staticmethod
    def select(request: AgentRunRequest) -> AgentDefinition:
        message = request.message.lower()
        scores = {name: sum(1 for word in agent.keywords if word in message) for name, agent in AGENTS.items() if name != "coordinator"}
        best = max(scores, key=scores.get)
        return AGENTS[best] if scores[best] > 0 else AGENTS["coordinator"]
