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
  const [nextCompanies, quote, metrics, risks] = await Promise.all([
    request("/api/companies").catch(() => companies),
    request(`/api/market/quotes/${symbol}`).catch(error => ({
      symbol,
      name: `股票 ${symbol}`,
      exchange: "CN",
      realtime: false,
      source: "LOCAL_ERROR",
      message: error.message
    })),
    request(`/api/metrics/${symbol}`).catch(() => []),
    request(`/api/metrics/${symbol}/risks`).catch(() => [])
  ]);

  companies = nextCompanies;
  updateCompanyCard(quote);
  renderAnalysis(metrics, risks, quote);
}

function updateCompanyCard(quote = null) {
  const company = companies.find(item => item.symbol === symbol);
  const name = quote?.name || company?.name || `股票 ${symbol}`;
  const industry = company?.industry || "待分析";
  const exchange = quote?.exchange || company?.exchange || "CN";
  $("companyAvatar").textContent = [...name][0]?.toUpperCase() || "股";
  $("companyName").textContent = name;
  $("companyMeta").textContent = `股票代码 ${symbol} · ${exchange} · ${industry}`;
  $("companyDescription").textContent = `当前正在分析 ${name}。系统会从行情、财务指标和文档证据中提炼投资关注点。`;
  renderQuote(quote);
}

function renderQuote(quote) {
  if (!quote) {
    $("marketQuote").innerHTML = "<span>行情加载中</span>";
    return;
  }
  const price = quote.currentPrice && Number(quote.currentPrice) > 0 ? Number(quote.currentPrice).toFixed(2) : "--";
  const change = quote.changePercent != null && Number(quote.currentPrice || 0) > 0 ? `${Number(quote.changePercent).toFixed(2)}%` : "--";
  const source = quote.realtime ? "实时行情" : "降级数据";
  $("marketQuote").innerHTML = `
    <span>${source}</span>
    <strong>${price}</strong>
    <em class="${Number(quote.changePercent || 0) < 0 ? "down" : ""}">${change}</em>
    <small>${escapeHtml(quote.message || quote.source || "")}</small>
  `;
}

function renderAnalysis(metrics, risks, quote) {
  if (!metrics.length) {
    $("ratingBadge").textContent = "等待分析";
    $("ratingBadge").className = "rating neutral";
    $("analysisConclusion").textContent = "点击“分析股票”后，系统会生成财务指标、风险信号和证据来源。";
    $("positivePoints").textContent = "暂无分析结果。";
    $("negativePoints").textContent = "暂无分析结果。";
    $("confidenceScore").textContent = "--";
    $("analysisUpdatedAt").textContent = "暂无数据";
    $("healthList").innerHTML = emptyHealth();
    $("riskList").innerHTML = item("暂无风险信号", "请先分析股票，系统会根据财务指标和文本证据识别风险。");
    return;
  }

  const checks = healthChecks(metrics);
  const warningCount = checks.filter(check => check.level !== "good").length + risks.length;
  const quoteWeak = Number(quote?.changePercent || 0) < -1;
  const rating = warningCount >= 4 || quoteWeak ? "谨慎" : warningCount >= 2 ? "中性" : "积极";
  const confidence = Math.max(68, Math.min(92, 86 - warningCount * 4 + (quote?.realtime ? 4 : 0)));
  const company = quote?.name || companies.find(item => item.symbol === symbol)?.name || `股票 ${symbol}`;

  $("ratingBadge").textContent = rating;
  $("ratingBadge").className = `rating ${rating === "积极" ? "positive" : rating === "谨慎" ? "cautious" : "neutral"}`;
  $("analysisConclusion").textContent = conclusionText(company, rating, checks, risks, quote);
  $("positivePoints").textContent = positiveText(checks);
  $("negativePoints").textContent = negativeText(checks, risks, quote);
  $("confidenceScore").textContent = `${confidence}%`;
  $("analysisUpdatedAt").textContent = quote?.tradeDate && quote?.tradeTime ? `${quote.tradeDate} ${quote.tradeTime}` : "基于当前数据";
  $("healthList").innerHTML = checks.map(healthCard).join("");
  $("riskList").innerHTML = riskItems(risks, checks, quote);
}

function healthChecks(metrics) {
  return [
    check("盈利能力", "ROE", metric(metrics, "ROE"), value => value >= 0.18, value => value >= 0.10, "ROE 代表净资产创造利润的效率。"),
    check("成长能力", "营收同比", metric(metrics, "REVENUE_YOY"), value => value >= 0.10, value => value >= 0, "收入增长决定公司基本面的扩张速度。"),
    check("利润增长", "净利润同比", metric(metrics, "NET_PROFIT_YOY"), value => value >= 0.10, value => value >= 0, "净利润增长体现盈利弹性。"),
    check("现金流质量", "经营现金流/净利润", metric(metrics, "OCF_NET_PROFIT"), value => value >= 1, value => value >= 0.8, "现金流越接近或超过净利润，利润质量越稳。"),
    check("杠杆水平", "资产负债率", metric(metrics, "DEBT_RATIO"), value => value <= 0.45, value => value <= 0.65, "负债率越高，财务安全边际越薄。", true),
    check("回款压力", "应收增速-营收增速", metric(metrics, "RECEIVABLE_GROWTH_SPREAD"), value => value <= 0.03, value => value <= 0.12, "应收增速明显高于收入增速时，需要关注回款压力。", true)
  ];
}

function metric(metrics, code) {
  return metrics
    .filter(item => item.code === code)
    .sort((a, b) => String(b.fiscalYear).localeCompare(String(a.fiscalYear)))[0];
}

function check(title, label, metricValue, good, watch, help, inverse = false) {
  const value = Number(metricValue?.value ?? NaN);
  let level = "empty";
  if (!Number.isNaN(value)) {
    level = good(value) ? "good" : watch(value) ? "watch" : "risk";
  }
  return {
    title,
    label,
    value,
    display: Number.isNaN(value) ? "--" : formatMetric(value, inverse),
    fiscalYear: metricValue?.fiscalYear || "--",
    level,
    help
  };
}

function formatMetric(value, raw = false) {
  if (raw) {
    return `${(value * 100).toFixed(2)}%`;
  }
  if (Math.abs(value) <= 2) {
    return `${(value * 100).toFixed(2)}%`;
  }
  return Number(value).toLocaleString("zh-CN");
}

function healthCard(check) {
  return `
    <article class="health-card ${check.level}">
      <span>${escapeHtml(check.title)}</span>
      <strong>${escapeHtml(check.display)}</strong>
      <p>${escapeHtml(check.label)} · ${escapeHtml(check.fiscalYear)}</p>
      <em>${statusName(check.level)}</em>
      <small>${escapeHtml(check.help)}</small>
    </article>
  `;
}

function emptyHealth() {
  return ["盈利能力", "成长能力", "利润增长", "现金流质量", "杠杆水平", "回款压力"]
    .map(title => `<article class="health-card empty"><span>${title}</span><strong>--</strong><p>等待分析</p><em>暂无数据</em></article>`)
    .join("");
}

function conclusionText(company, rating, checks, risks, quote) {
  const good = checks.filter(check => check.level === "good").map(check => check.title);
  const weak = checks.filter(check => check.level === "risk").map(check => check.title);
  const priceText = quote?.realtime ? `实时行情显示涨跌幅为 ${Number(quote.changePercent || 0).toFixed(2)}%。` : "实时行情暂不可用，当前以本地分析数据为主。";
  if (rating === "积极") {
    return `${company} 当前基本面指标较稳，${good.slice(0, 2).join("、")}表现较好。${priceText} 可以继续关注盈利持续性和估值安全边际。`;
  }
  if (rating === "谨慎") {
    return `${company} 当前需要谨慎观察，${weak.slice(0, 2).join("、") || "部分核心指标"}存在压力，系统识别到 ${risks.length} 条风险信号。${priceText}`;
  }
  return `${company} 当前处于中性观察状态，基本面有支撑，但仍需关注${(weak[0] || "行业变化")}和短期价格波动。${priceText}`;
}

function positiveText(checks) {
  const good = checks.filter(check => check.level === "good").map(check => `${check.title}较好`);
  return good.length ? good.slice(0, 3).join("；") + "。" : "暂未看到特别突出的优势指标，需要结合后续财报继续观察。";
}

function negativeText(checks, risks, quote) {
  const weak = checks.filter(check => check.level === "risk").map(check => `${check.title}偏弱`);
  const riskTitles = risks.map(risk => risk.title);
  const priceRisk = Number(quote?.changePercent || 0) < -1 ? ["短期股价承压"] : [];
  const all = [...weak, ...riskTitles, ...priceRisk];
  return all.length ? all.slice(0, 3).join("；") + "。" : "暂未发现明显风险，但仍需关注行业景气度和后续公告变化。";
}

function riskItems(risks, checks, quote) {
  const ruleRisks = risks.map(risk =>
    item(risk.title, risk.explanation, `严重度 ${risk.severity} · ${risk.detectedAt}`)
  );
  const metricRisks = checks
    .filter(check => check.level === "risk")
    .map(check => item(`${check.title}需关注`, `${check.label} 当前为 ${check.display}，${check.help}`, `${check.fiscalYear} · 指标预警`));
  const marketRisk = Number(quote?.changePercent || 0) < -1
    ? [item("短期价格承压", `实时涨跌幅为 ${Number(quote.changePercent).toFixed(2)}%，需要结合成交量和消息面判断是否只是短期波动。`, "实时行情")]
    : [];
  const all = [...ruleRisks, ...metricRisks, ...marketRisk];
  return all.length ? all.join("") : item("暂未发现明显风险", "当前财务指标没有触发高风险规则，建议继续关注后续财报、公告和行业变化。");
}

function statusName(level) {
  return ({
    good: "健康",
    watch: "关注",
    risk: "风险",
    empty: "暂无数据"
  })[level] || level;
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
  ).join("") || item("暂无证据", "请先分析股票，系统会建立该股票的公开财报和公告证据索引。");
}

$("runWorkflow").addEventListener("click", runWorkflow);
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
  $("analysisConclusion").textContent = `后端服务未就绪：${error.message}`;
});

function normalizeSymbol(value) {
  const next = String(value || "").trim().toUpperCase();
  if (!next) {
    throw new Error("请输入股票代码");
  }
  return next;
}
