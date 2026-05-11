let symbol = "600519";
let companies = [];

const $ = (id) => document.getElementById(id);

async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json();
}

function item(title, body, meta = "") {
  return `<div class="item"><h3>${escapeHtml(title)}</h3><p>${escapeHtml(body || "")}</p>${meta ? `<div class="meta">${escapeHtml(meta)}</div>` : ""}</div>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function refresh() {
  companies = await request("/api/companies").catch(() => companies);
  updateCompanyCard();

  const [tasks, metrics, chunks, timeline, graph] = await Promise.all([
    request("/api/workflows"),
    request(`/api/metrics/${symbol}`),
    request(`/api/document-index/${symbol}/count`),
    request(`/api/intelligence/${symbol}/timeline`),
    request(`/api/intelligence/${symbol}/graph`)
  ]);

  $("taskCount").textContent = tasks.length;
  $("metricCount").textContent = metrics.length;
  $("chunkCount").textContent = chunks.count;
  $("eventCount").textContent = timeline.length;

  const currentTasks = tasks
    .filter(task => task.idempotencyKey.includes(symbol))
    .slice(0, 8);

  $("workflowList").innerHTML = currentTasks.map(task =>
    item(taskName(task.taskType), task.idempotencyKey, `${statusName(task.status)} · 第 ${task.attempts} 次执行`)
  ).join("") || item("暂无工作流任务", "点击“分析股票”后会自动生成该股票的任务链路。");

  $("metricList").innerHTML = metricTable(metrics);

  $("timelineList").innerHTML = timeline.slice(0, 8).map(event =>
    item(event.title, event.summary, `${event.happenedAt} · ${eventTypeName(event.type)}`)
  ).join("") || item("暂无时间线事件", "运行工作流后会生成公司事件时间线。");

  $("graphList").innerHTML = [
    item("实体节点", `${graph.entities.length} 个节点`, graph.entities.slice(0, 6).map(entity => entity.name).join(" · ")),
    item("关系边", `${graph.relations.length} 条关系`, graph.relations.slice(0, 6).map(relation => relationName(relation.relationType)).join(" · "))
  ].join("");
}

function updateCompanyCard() {
  const company = companies.find(item => item.symbol === symbol);
  const name = company?.name || `股票 ${symbol}`;
  const industry = company?.industry || "待分析";
  const exchange = company?.exchange || "CN";
  $("companyAvatar").textContent = name.slice(0, 1).toUpperCase();
  $("companyName").textContent = name;
  $("companyMeta").textContent = `股票代码 ${symbol} · ${exchange} · ${industry}`;
  $("companyDescription").textContent = `当前正在分析 ${name}。系统会围绕 ${industry} 行业特征，生成指标、证据、事件和图谱结果。`;
}

function metricTable(metrics) {
  const rows = metrics
    .filter(metric => ["REVENUE_YOY", "NET_PROFIT_YOY", "ROE", "OCF_NET_PROFIT", "RECEIVABLE_GROWTH_SPREAD"].includes(metric.code))
    .slice(-18)
    .map(metric => `<tr><td>${metric.fiscalYear}</td><td>${metric.code}</td><td>${escapeHtml(metric.name)}</td><td>${metric.value}</td><td>${metric.formulaVersion}</td></tr>`)
    .join("");
  return `<table><thead><tr><th>年份</th><th>指标代码</th><th>指标名称</th><th>数值</th><th>版本</th></tr></thead><tbody>${rows}</tbody></table>`;
}

async function runWorkflow() {
  $("runWorkflow").disabled = true;
  try {
    symbol = normalizeSymbol($("symbolInput").value);
    $("symbolInput").value = symbol;
    updateCompanyCard();
    await request(`/api/ingestion/${encodeURIComponent(symbol)}/async`, { method: "POST" });
    await new Promise(resolve => setTimeout(resolve, 500));
    await refresh();
    await search();
  } finally {
    $("runWorkflow").disabled = false;
  }
}

async function search() {
  const query = encodeURIComponent($("searchInput").value);
  const results = await request(`/api/document-index/${symbol}/search?q=${query}`);
  $("retrievalList").innerHTML = results.map(hit =>
    item(hit.title, hit.text, `${hit.section} · 相关度 ${Number(hit.score).toFixed(2)}`)
  ).join("") || item("暂无证据", "请先运行工作流并重建文档索引。");
}

async function runEvaluation() {
  $("runEvaluation").disabled = true;
  try {
    const run = await request("/api/evaluations/rag/run", { method: "POST" });
    $("evaluationSummary").innerHTML = `
      <span class="badge">通过 ${run.passedCases}/${run.totalCases}</span>
      <span class="badge ${run.averageScore < 0.8 ? "warn" : ""}">平均分 ${(run.averageScore * 100).toFixed(0)}%</span>
    `;
    $("evaluationList").innerHTML = run.results.map(result =>
      item(result.caseId, result.failures.length ? result.failures.join("; ") : "评估通过", `得分 ${(result.score * 100).toFixed(0)}% · 证据 ${result.metrics.evidenceCount} 条 · 延迟 ${result.metrics.latencyMillis}ms`)
    ).join("");
  } finally {
    $("runEvaluation").disabled = false;
  }
}

$("runWorkflow").addEventListener("click", runWorkflow);
$("runEvaluation").addEventListener("click", runEvaluation);
$("searchButton").addEventListener("click", search);
$("symbolInput").addEventListener("keydown", event => {
  if (event.key === "Enter") {
    runWorkflow();
  }
});

document.querySelectorAll(".symbol-chip").forEach(button => {
  button.addEventListener("click", () => {
    $("symbolInput").value = button.dataset.symbol;
    runWorkflow();
  });
});

refresh().then(search).catch(error => {
  $("workflowList").innerHTML = item("后端服务未就绪", error.message);
});

function normalizeSymbol(value) {
  const next = String(value || "").trim().toUpperCase();
  if (!next) {
    throw new Error("请输入股票代码");
  }
  return next;
}

function taskName(type) {
  return ({
    FINANCIAL_DATA_INGESTION: "金融数据接入",
    FINANCIAL_METRIC_RECALCULATION: "财务指标重算",
    DOCUMENT_INDEX_BUILD: "文档索引构建",
    COMPANY_INTELLIGENCE_BUILD: "公司智能构建"
  })[type] || type;
}

function statusName(status) {
  return ({
    CREATED: "已创建",
    RUNNING: "运行中",
    SUCCEEDED: "已完成",
    FAILED: "失败",
    DEAD_LETTER: "死信"
  })[status] || status;
}

function eventTypeName(type) {
  return ({
    FINANCIAL_RESULT: "财务事件",
    RISK_SIGNAL: "风险信号",
    MANAGEMENT_DISCUSSION: "管理层讨论",
    INDUSTRY_CHANGE: "行业变化",
    RESEARCH_VIEW: "研报观点"
  })[type] || type;
}

function relationName(type) {
  return ({
    BELONGS_TO_INDUSTRY: "所属行业",
    PUBLISHED_DOCUMENT: "发布文档",
    MENTIONS_KEYWORD: "提及关键词",
    HAS_FINANCIAL_METRIC: "拥有财务指标",
    HAS_RISK_SIGNAL: "存在风险信号",
    HAS_TIMELINE_EVENT: "包含时间线事件"
  })[type] || type;
}
