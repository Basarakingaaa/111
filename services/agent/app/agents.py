from dataclasses import dataclass
from .models import AgentName, AgentRunRequest, AgentResult, ProposedAction

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
            summary=f"{self.title}已接收请求，准备在授权范围内处理。",
            findings=findings, proposed_actions=actions, evidence=request.evidence,
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
        if request.requested_agent:
            return AGENTS[request.requested_agent]
        message = request.message.lower()
        scores = {name: sum(1 for word in agent.keywords if word in message) for name, agent in AGENTS.items() if name != "coordinator"}
        best = max(scores, key=scores.get)
        return AGENTS[best] if scores[best] > 0 else AGENTS["coordinator"]
