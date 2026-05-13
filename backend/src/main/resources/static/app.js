let symbol = "600519";
let companies = [];
let companyTotal = 0;
let suggestionTimer = null;
let chartLimit = 120;
let latestQuote = null;
let latestCandles = [];
let latestAiAnalysis = null;

const $ = (id) => document.getElementById(id);

async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function refresh() {
  const [
    nextCompanies,
    countResult,
    quote,
    candles,
    metrics,
    risks,
    status,
    aiAnalysis
  ] = await Promise.all([
    request("/api/companies?limit=200").catch(() => companies),
    request("/api/companies/count").catch(() => ({ count: companies.length })),
    request(`/api/market/quotes/${symbol}`).catch(error => ({
      symbol,
      name: `股票 ${symbol}`,
      exchange: "CN",
      realtime: false,
      source: "LOCAL_ERROR",
      message: error.message
    })),
    request(`/api/market/history/${symbol}?limit=${chartLimit}`).catch(() => []),
    request(`/api/metrics/${symbol}`).catch(() => []),
    request(`/api/metrics/${symbol}/risks`).catch(() => []),
    request(`/api/companies/${symbol}/analysis-status`).catch(() => null),
    request(`/api/companies/${symbol}/ai-analysis/latest`).catch(() => null)
  ]);

  companies = nextCompanies;
  companyTotal = Number(countResult.count || companies.length || 0);
  latestQuote = quote;
  latestCandles = candles;
  latestAiAnalysis = aiAnalysis;

  renderUniverseStatus();
  renderStockList();
  updateCompanyCard(quote);
  renderDataStatus(status, quote, aiAnalysis);
  renderReportMeta(aiAnalysis, status, metrics, risks, quote);
  renderAnalysis(metrics, risks, quote, aiAnalysis);
  renderChart(candles, quote, aiAnalysis);
  renderChartStats(candles, quote);
}

function updateCompanyCard(quote = null) {
  const company = companies.find(item => item.symbol === symbol);
  const name = quote?.name || company?.name || `股票 ${symbol}`;
  const industry = company?.industry || "待分析";
  const exchange = quote?.exchange || company?.exchange || "CN";
  $("companyName").textContent = name;
  $("companyMeta").textContent = `${symbol}.${exchange}`;
  renderQuote(quote);
}

function renderUniverseStatus(message = "") {
  const updatedAt = latestQuote?.tradeTime || formatDateTime(latestAiAnalysis?.generatedAt) || "--";
  $("universeStatus").textContent = message || `更新时间 ${updatedAt}`;
  $("poolCount").textContent = `${companyTotal || companies.length || 0} 只`;
}

function renderStockList() {
  const activeCompany = companies.find(company => company.symbol === symbol);
  const visible = companies.slice(0, 24);
  if (activeCompany && !visible.some(company => company.symbol === symbol)) {
    visible.unshift(activeCompany);
  }
  $("stockList").innerHTML = visible.map(company => {
    const isActive = company.symbol === symbol;
    const changePercent = isActive && latestQuote ? numeric(latestQuote.changePercent) : null;
    const changeText = changePercent == null ? "--" : `${formatSigned(changePercent)}%`;
    const changeClass = changePercent == null ? "" : changePercent >= 0 ? "up" : "down";
    return `
    <button class="stock-row ${company.symbol === symbol ? "active" : ""}" data-symbol="${escapeHtml(company.symbol)}">
      <span class="stock-name">${escapeHtml(company.name)}</span>
      <span class="stock-code">${escapeHtml(company.symbol)}.${escapeHtml(company.exchange || "CN")}</span>
      <span class="stock-change ${changeClass}">${escapeHtml(changeText)}</span>
    </button>
  `;
  }).join("") || `<div class="item"><h3>暂无股票</h3><p>点击同步股票池后可加载 A 股列表。</p></div>`;
  document.querySelectorAll(".stock-row").forEach(button => {
    button.addEventListener("click", () => selectSymbol(button.dataset.symbol));
  });
}

function renderQuote(quote) {
  if (!quote) {
    $("marketQuote").innerHTML = "<span>行情加载中</span>";
    $("marketStatus").textContent = "市场状态 待连接";
    $("marketStatus").className = "market-status";
    return;
  }
  const price = numeric(quote.currentPrice);
  const change = numeric(quote.change);
  const changePercent = numeric(quote.changePercent);
  const direction = changePercent >= 0 ? "up" : "down";
  const latestCandle = Array.isArray(latestCandles) ? latestCandles[latestCandles.length - 1] : null;
  const amount = numeric(latestCandle?.amount);
  $("marketStatus").textContent = quote.realtime ? "市场状态 实时接入" : "市场状态 降级数据";
  $("marketStatus").className = `market-status ${quote.realtime ? "live" : "fallback"}`;
  $("marketQuote").innerHTML = `
    <section class="summary-price">
      <strong class="${direction}">${price > 0 ? price.toFixed(2) : "--"}</strong>
      <em class="${direction}">${formatSigned(change)} / ${formatSigned(changePercent)}%</em>
    </section>
  `;
}

function renderDataStatus(status, quote, aiAnalysis) {
  if (!status) {
    $("dataQualitySummary").textContent = "数据可信度待确认";
    $("dataQualityPanel").innerHTML = `<div class="empty-note">数据质量暂不可用，等待后端状态接口返回。</div>`;
    return;
  }
  const quoteTime = status.quoteTradedAt ? status.quoteTradedAt.replace("T", " ") : "--";
  const metricTime = status.latestMetricRunAt ? status.latestMetricRunAt.replace("T", " ").replace("Z", "") : "--";
  const workflow = status.latestWorkflowStatus || "暂无任务";
  const aiTime = status.latestAiReportAt ? status.latestAiReportAt.replace("T", " ").replace("Z", "") : "--";
  const qualities = [
    ["行情源", status.quoteRealtime ? "Sina 实时行情接入正常" : "行情源降级，需关注时效", status.quoteRealtime ? "good" : "watch"],
    ["行情接口", quote?.tradeTime ? `最新 ${quoteTime}` : "等待行情刷新", quote?.tradeTime ? "good" : "watch"],
    ["财报索引", `${Number(status.documentCount || 0)} 份文档 / ${Number(status.chunkCount || 0)} 段证据`, Number(status.chunkCount || 0) > 0 ? "good" : "watch"],
    ["指标任务", `${Number(status.metricRunCount || 0)} 次计算 / ${metricTime}`, Number(status.metricRunCount || 0) > 0 ? "good" : "watch"],
    ["规则兜底", aiAnalysis ? (aiAnalysis.aiGenerated ? "本地模型输出，规则兜底未启用" : "Ollama 不可用，已启用规则兜底") : "等待 AI 报告生成", aiAnalysis?.aiGenerated ? "good" : "watch"],
    ["报告时间", Number(status.aiReportCount || 0) > 0 ? `${Number(status.aiReportCount || 0)} 份 / ${aiTime}` : "暂无报告", Number(status.aiReportCount || 0) > 0 ? "good" : "watch"],
    ["工作流", workflow, workflow === "SUCCEEDED" ? "good" : workflow === "FAILED" ? "risk" : "watch"]
  ];
  const qualityGrade = Number(status.chunkCount || 0) > 0 && Number(status.metricRunCount || 0) > 0
    ? (status.quoteRealtime ? "A-" : "B+")
    : "B";
  $("dataQualitySummary").textContent = `数据可信度 ${qualityGrade} / ${status.quoteRealtime ? "行情实时" : "行情降级"} / ${aiAnalysis?.aiGenerated ? "模型输出" : "规则兜底"}`;
  $("dataQualityPanel").innerHTML = qualities.map(([label, value, level]) => qualityItem(label, value, level)).join("")
    + (status.latestWorkflowError ? `<div class="empty-note">${escapeHtml(status.latestWorkflowError)}</div>` : "");
}

function renderReportMeta(aiAnalysis, status, metrics, risks, quote) {
  const rating = aiAnalysis?.rating || "待生成";
  const confidence = aiAnalysis?.confidence != null ? `${aiAnalysis.confidence}%` : "--";
  $("aiBriefMeta").textContent = aiAnalysis ? formatDateTime(aiAnalysis.generatedAt) || "Latest" : "Pending";
  $("aiBriefPanel").innerHTML = aiAnalysis ? `
    <div class="brief-rating">
      <span>综合判断</span>
      <strong>${escapeHtml(rating)}</strong>
    </div>
    <p>${escapeHtml(aiAnalysis.summary || "AI 已生成结构化观点，但摘要内容暂为空。")}</p>
    <dl class="brief-facts">
      <div><dt>置信度</dt><dd>${escapeHtml(confidence)}</dd></div>
      <div><dt>当前建议</dt><dd>${escapeHtml(recommendationText(rating))}</dd></div>
      <div><dt>核心原因</dt><dd>${escapeHtml(firstPoint(aiAnalysis.positivePoints) || "等待更多财务指标与公告证据。")}</dd></div>
    </dl>
  ` : `
    <div class="brief-rating pending">
      <span>综合判断</span>
      <strong>待生成</strong>
    </div>
    <p>点击“生成 AI 研报”后，系统会结合实时行情、财务指标、风险规则和证据索引输出机构化简报。</p>
  `;

  $("riskSignalPanel").innerHTML = riskSignalItems(risks, quote, aiAnalysis).join("");
}

function renderAnalysis(metrics, risks, quote, aiAnalysis = null) {
  const checks = metrics.length ? healthChecks(metrics) : [];
  if (!metrics.length) {
    const displayRating = aiAnalysis?.rating || "等待分析";
  $("ratingBadge").textContent = displayRating;
  $("ratingBadge").className = `rating ${ratingClass(displayRating)}`;
  $("summaryRatingBadge").textContent = `AI评级 ${displayRating}`;
  $("summaryRatingBadge").className = `rating ${ratingClass(displayRating)}`;
  $("analysisConclusion").textContent = aiAnalysis?.summary || "点击“生成 AI 研报”后，系统会回答这只股票能不能看、为什么、风险在哪里、证据来自哪里。";
    $("positivePoints").innerHTML = decisionList(aiAnalysis?.positivePoints, "暂无核心理由。");
    $("negativePoints").innerHTML = decisionList(aiAnalysis?.riskPoints, "暂无主要风险。");
    $("confidenceScore").textContent = aiAnalysis?.confidence != null ? `${aiAnalysis.confidence}%` : "--";
    $("analysisUpdatedAt").textContent = aiAnalysis ? analysisMeta(aiAnalysis) : "暂无数据";
    $("healthList").innerHTML = emptyHealth();
    return;
  }

  const warningCount = checks.filter(check => check.level !== "good").length + risks.length;
  const quoteWeak = Number(quote?.changePercent || 0) < -1;
  const rating = warningCount >= 4 || quoteWeak ? "谨慎" : warningCount >= 2 ? "中性" : "积极";
  const confidence = Math.max(68, Math.min(92, 86 - warningCount * 4 + (quote?.realtime ? 4 : 0)));
  const company = quote?.name || companies.find(item => item.symbol === symbol)?.name || `股票 ${symbol}`;
  const displayRating = aiAnalysis?.rating || rating;
  const displayConfidence = aiAnalysis?.confidence ?? confidence;

  $("ratingBadge").textContent = displayRating;
  $("ratingBadge").className = `rating ${ratingClass(displayRating)}`;
  $("summaryRatingBadge").textContent = `AI评级 ${displayRating}`;
  $("summaryRatingBadge").className = `rating ${ratingClass(displayRating)}`;
  $("analysisConclusion").textContent = aiAnalysis?.summary || conclusionText(company, rating, checks, risks, quote);
  $("positivePoints").innerHTML = decisionList(aiAnalysis?.positivePoints, positiveText(checks));
  $("negativePoints").innerHTML = decisionList(aiAnalysis?.riskPoints, negativeText(checks, risks, quote));
  $("confidenceScore").textContent = `${displayConfidence}%`;
  $("analysisUpdatedAt").textContent = aiAnalysis ? analysisMeta(aiAnalysis) : quote?.tradeDate && quote?.tradeTime ? `${quote.tradeDate} ${quote.tradeTime}` : "基于当前数据";
  $("healthList").innerHTML = checks.map(healthCard).join("");
}

function renderChart(candles, quote, aiAnalysis) {
  const canvas = $("priceChart");
  const tooltip = $("chartTooltip");
  const ctx = canvas.getContext("2d");
  const rect = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const width = Math.max(320, Math.floor(rect.width));
  const height = 520;
  canvas.width = Math.floor(width * dpr);
  canvas.height = Math.floor(height * dpr);
  canvas.style.height = `${height}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  tooltip.hidden = true;
  canvas.onmousemove = null;
  canvas.onmouseleave = null;

  if (!Array.isArray(candles) || candles.length < 2) {
    drawEmptyChart(ctx, width, height);
    return;
  }

  const data = candles.map(candle => ({
    date: candle.tradeDate,
    open: numeric(candle.open),
    close: numeric(candle.close),
    high: numeric(candle.high),
    low: numeric(candle.low),
    volume: numeric(candle.volume),
    changePercent: numeric(candle.changePercent)
  })).filter(candle => candle.close > 0);

  if (data.length < 2) {
    drawEmptyChart(ctx, width, height);
    return;
  }

  const ma5 = movingAverage(data, 5);
  const ma20 = movingAverage(data, 20);
  const ma60 = movingAverage(data, 60);
  const priceValues = [
    ...data.flatMap(candle => [candle.high, candle.low]),
    ...ma5.filter(Boolean),
    ...ma20.filter(Boolean),
    ...ma60.filter(Boolean)
  ];
  const minPrice = Math.min(...priceValues);
  const maxPrice = Math.max(...priceValues);
  const maxVolume = Math.max(...data.map(candle => candle.volume), 1);
  const pad = { left: 54, right: 62, top: 24, bottom: 92 };
  const volumeHeight = 72;
  const priceBottom = height - pad.bottom - volumeHeight;
  const priceHeight = priceBottom - pad.top;
  const volumeTop = priceBottom + 18;
  const span = Math.max(maxPrice - minPrice, 1);
  const xStep = (width - pad.left - pad.right) / Math.max(data.length - 1, 1);
  const x = index => pad.left + index * xStep;
  const y = value => pad.top + (maxPrice - value) / span * priceHeight;
  const vy = volume => volumeTop + volumeHeight - volume / maxVolume * volumeHeight;

  const drawBaseChart = () => {
    ctx.clearRect(0, 0, width, height);
    drawGrid(ctx, width, height, pad, priceBottom, volumeTop, minPrice, maxPrice);
    drawVolume(ctx, data, x, vy, volumeTop, volumeHeight, xStep);
    drawLine(ctx, data.map(candle => candle.close), x, y, "#252A2E", 1.7);
    drawLine(ctx, ma5, x, y, "#7F93A7", 1.05);
    drawLine(ctx, ma20, x, y, "#A98C56", 1.05);
    drawLine(ctx, ma60, x, y, "#7D9B8A", 1.05);
    drawAiMarker(ctx, data, aiAnalysis, x, y);
  };

  drawBaseChart();

  canvas.onmousemove = event => {
    const bounds = canvas.getBoundingClientRect();
    const mouseX = event.clientX - bounds.left;
    const index = Math.min(data.length - 1, Math.max(0, Math.round((mouseX - pad.left) / xStep)));
    const candle = data[index];
    const cx = x(index);
    drawBaseChart();
    drawCrosshair(ctx, cx, pad.top, height - 32);
    tooltip.hidden = false;
    tooltip.style.left = `${Math.min(width - 210, Math.max(10, cx + 12))}px`;
    tooltip.style.top = `${Math.max(10, y(candle.close) - 28)}px`;
    tooltip.innerHTML = `
      <strong>${escapeHtml(candle.date)}</strong>
      <div>收盘：${formatNumber(candle.close)}</div>
      <div>涨跌：<span class="${candle.changePercent >= 0 ? "up" : "down"}">${formatSigned(candle.changePercent)}%</span></div>
      <div>成交量：${formatVolume(candle.volume)}</div>
    `;
  };
  canvas.onmouseleave = () => {
    tooltip.hidden = true;
    drawBaseChart();
  };
}

function drawEmptyChart(ctx, width, height) {
  ctx.fillStyle = "#FFFFFF";
  ctx.fillRect(0, 0, width, height);
  ctx.strokeStyle = "#F0EDE7";
  ctx.lineWidth = 1;
  for (let i = 0; i < 6; i++) {
    const y = 36 + i * ((height - 92) / 5);
    ctx.beginPath();
    ctx.moveTo(48, y);
    ctx.lineTo(width - 48, y);
    ctx.stroke();
  }
  ctx.fillStyle = "#6B7280";
  ctx.font = "13px Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif";
  ctx.fillText("暂无历史行情数据，等待行情源返回 K 线。", 28, 42);
}

function drawGrid(ctx, width, height, pad, priceBottom, volumeTop, minPrice, maxPrice) {
  ctx.strokeStyle = "#F0EDE7";
  ctx.lineWidth = 1;
  ctx.fillStyle = "#9CA3AF";
  ctx.font = "11px ui-monospace, SFMono-Regular, Menlo, monospace";
  for (let i = 0; i <= 4; i++) {
    const yLine = pad.top + (priceBottom - pad.top) * i / 4;
    ctx.beginPath();
    ctx.moveTo(pad.left, yLine);
    ctx.lineTo(width - pad.right, yLine);
    ctx.stroke();
    const label = maxPrice - (maxPrice - minPrice) * i / 4;
    ctx.fillText(formatNumber(label), width - pad.right + 8, yLine + 4);
  }
  for (let i = 0; i <= 4; i++) {
    const xLine = pad.left + (width - pad.left - pad.right) * i / 4;
    ctx.beginPath();
    ctx.moveTo(xLine, pad.top);
    ctx.lineTo(xLine, volumeTop + 70);
    ctx.stroke();
  }
  ctx.beginPath();
  ctx.moveTo(pad.left, volumeTop);
  ctx.lineTo(width - pad.right, volumeTop);
  ctx.stroke();
}

function drawVolume(ctx, data, x, vy, volumeTop, volumeHeight, xStep) {
  const barWidth = Math.max(2, Math.min(8, xStep * .58));
  data.forEach((candle, index) => {
    ctx.fillStyle = candle.close >= candle.open ? "rgba(214, 69, 69, .22)" : "rgba(46, 139, 87, .22)";
    const top = vy(candle.volume);
    ctx.fillRect(x(index) - barWidth / 2, top, barWidth, volumeTop + volumeHeight - top);
  });
}

function drawLine(ctx, values, x, y, color, width) {
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.beginPath();
  let started = false;
  values.forEach((value, index) => {
    if (!value) {
      return;
    }
    if (!started) {
      ctx.moveTo(x(index), y(value));
      started = true;
    } else {
      ctx.lineTo(x(index), y(value));
    }
  });
  if (started) {
    ctx.stroke();
  }
}

function drawAiMarker(ctx, data, aiAnalysis, x, y) {
  if (!aiAnalysis || !data.length) {
    return;
  }
  const index = data.length - 1;
  const candle = data[index];
  ctx.fillStyle = aiAnalysis.rating === "谨慎" ? "#2E8B57" : aiAnalysis.rating === "积极" ? "#D64545" : "#9A7B3F";
  ctx.beginPath();
  ctx.arc(x(index), y(candle.close), 5, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#9A7B3F";
  ctx.font = "12px -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif";
  ctx.fillText(`AI ${aiAnalysis.rating || "评级"}`, x(index) - 48, y(candle.close) - 12);
}

function drawCrosshair(ctx, x, top, bottom) {
  ctx.save();
  ctx.strokeStyle = "rgba(154, 123, 63, .36)";
  ctx.setLineDash([4, 4]);
  ctx.beginPath();
  ctx.moveTo(x, top);
  ctx.lineTo(x, bottom);
  ctx.stroke();
  ctx.restore();
}

function renderChartStats(candles, quote) {
  const latest = candles?.[candles.length - 1] || null;
  const high = candles?.length ? Math.max(...candles.map(candle => numeric(candle.high))) : numeric(quote?.highPrice);
  const low = candles?.length ? Math.min(...candles.map(candle => numeric(candle.low))) : numeric(quote?.lowPrice);
  const totalVolume = candles?.length ? candles.reduce((sum, candle) => sum + numeric(candle.volume), 0) : 0;
  const first = candles?.[0];
  const rangeReturn = first && latest && numeric(first.close) > 0
    ? (numeric(latest.close) - numeric(first.close)) / numeric(first.close) * 100
    : 0;
  $("chartStats").innerHTML = [
    ["区间涨跌", `${formatSigned(rangeReturn)}%`, rangeReturn >= 0 ? "up" : "down"],
    ["区间最高", formatNumber(high), "up"],
    ["区间最低", formatNumber(low), "down"],
    ["成交量", formatVolume(totalVolume), ""],
    ["最新日期", latest?.tradeDate || "--", ""]
  ].map(([label, value, klass]) => `
    <article>
      <span>${label}</span>
      <strong class="${klass}">${escapeHtml(value)}</strong>
    </article>
  `).join("");
}

function movingAverage(data, windowSize) {
  return data.map((_, index) => {
    if (index + 1 < windowSize) {
      return null;
    }
    const slice = data.slice(index + 1 - windowSize, index + 1);
    return slice.reduce((sum, candle) => sum + candle.close, 0) / windowSize;
  });
}

function formatPoints(points) {
  if (!Array.isArray(points) || !points.length) {
    return "";
  }
  return points.slice(0, 4).join("；") + "。";
}

function splitPoints(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean).slice(0, 2);
  }
  return String(value || "")
    .replaceAll("。", "；")
    .split("；")
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 2);
}

function decisionList(points, fallback) {
  const rows = splitPoints(points || fallback);
  return rows.length
    ? `<ol>${rows.map(point => `<li>${escapeHtml(point)}</li>`).join("")}</ol>`
    : `<p>${escapeHtml(fallback)}</p>`;
}

function firstPoint(points) {
  return splitPoints(points)[0] || "";
}

function recommendationText(rating) {
  if (rating === "积极") {
    return "可进入重点跟踪，继续验证估值安全边际。";
  }
  if (rating === "谨慎") {
    return "暂以观察为主，等待风险项改善或更强证据确认。";
  }
  return "维持中性观察，适合放入候选池持续跟踪。";
}

function healthChecks(metrics) {
  return [
    check("盈利能力", "ROE", metric(metrics, "ROE"), value => value >= 0.18, value => value >= 0.10, "ROE 代表净资产创造利润的效率。"),
    check("成长能力", "营收同比", metric(metrics, "REVENUE_YOY"), value => value >= 0.10, value => value >= 0, "收入增长决定公司基本面的扩张速度。"),
    check("利润增长", "净利润同比", metric(metrics, "NET_PROFIT_YOY"), value => value >= 0.10, value => value >= 0, "净利润增长体现盈利弹性。"),
    check("现金流质量", "经营现金流/净利润", metric(metrics, "OCF_NET_PROFIT"), value => value >= 1, value => value >= 0.8, "现金流越接近或超过净利润，利润质量越稳。"),
    check("资产负债率", "资产负债率", metric(metrics, "DEBT_RATIO"), value => value <= 0.45, value => value <= 0.65, "负债率越高，财务安全边际越薄。", true)
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
  if (raw || Math.abs(value) <= 2) {
    return `${(value * 100).toFixed(2)}%`;
  }
  return Number(value).toLocaleString("zh-CN");
}

function healthCard(check) {
  return `
    <article class="health-card ${check.level}">
      <span>${escapeHtml(check.title)}</span>
      <strong>${escapeHtml(check.display)}</strong>
      <em>${statusName(check.level)}</em>
    </article>
  `;
}

function emptyHealth() {
  return ["ROE", "营收同比", "净利润同比", "现金流质量", "资产负债率"]
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

function riskItemsFromAi(aiAnalysis) {
  const risks = Array.isArray(aiAnalysis?.riskPoints) ? aiAnalysis.riskPoints : [];
  return risks.length
    ? risks.slice(0, 5).map((risk, index) => item(`AI 风险提示 ${index + 1}`, risk, aiAnalysis.source || "AI 分析")).join("")
    : item("暂未发现明显风险", "AI 分析暂未给出明确风险点，但仍需结合后续财报、公告和行业变化。");
}

function item(title, body, meta = "") {
  return `<div class="item"><h3>${escapeHtml(title)}</h3><p>${escapeHtml(body || "")}</p>${meta ? `<div class="meta">${escapeHtml(meta)}</div>` : ""}</div>`;
}

function qualityItem(label, value, level = "watch") {
  return `
    <div class="quality-item ${level}">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </div>
  `;
}

function riskSignalItems(risks, quote, aiAnalysis) {
  const latest = latestCandles?.[latestCandles.length - 1] || null;
  const last20 = Array.isArray(latestCandles) ? latestCandles.slice(-20) : [];
  const avgVolume = last20.length
    ? last20.reduce((sum, candle) => sum + numeric(candle.volume), 0) / last20.length
    : 0;
  const volumeRatio = avgVolume > 0 && latest ? numeric(latest.volume) / avgVolume : 0;
  const changePercent = numeric(quote?.changePercent);
  const trendLevel = changePercent <= -3 ? "risk" : changePercent < 0 ? "watch" : "good";
  const liquidityLevel = volumeRatio >= 1.8 ? "watch" : volumeRatio > 0 ? "good" : "watch";
  const financialLevel = risks?.length ? "risk" : "good";
  const evidenceCount = Array.isArray(aiAnalysis?.citations) ? aiAnalysis.citations.length : 0;
  const newsLevel = evidenceCount ? "good" : "watch";
  const valuationLevel = aiAnalysis?.rating === "积极" ? "watch" : "watch";

  return [
    signalItem("趋势风险", trendLevel),
    signalItem("估值风险", valuationLevel),
    signalItem("流动性风险", liquidityLevel),
    signalItem("财务风险", financialLevel),
    signalItem("舆情风险", newsLevel)
  ];
}

function signalItem(label, level) {
  const levelName = ({ good: "低", watch: "中", risk: "高" })[level] || "中";
  return `
    <article class="signal-item ${level}">
      <div><span>${escapeHtml(label)}</span><em>${escapeHtml(levelName)}</em></div>
    </article>
  `;
}

async function runWorkflow() {
  $("runWorkflow").disabled = true;
  renderUniverseStatus(`正在分析 ${symbol}...`);
  try {
    symbol = normalizeSymbol($("symbolInput").value);
    $("symbolInput").value = symbol;
    updateCompanyCard(latestQuote);
    await request("/api/companies/batch-analysis", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ symbols: [symbol] })
    });
    await refresh();
    await search();
  } finally {
    $("runWorkflow").disabled = false;
  }
}

async function syncUniverse() {
  $("syncUniverse").disabled = true;
  renderUniverseStatus("正在同步股票池...");
  try {
    const result = await request("/api/companies/sync-a-shares", { method: "POST" });
    companies = await request("/api/companies?limit=200").catch(() => companies);
    companyTotal = Number(result.companyCount || companies.length || 0);
    renderUniverseStatus(`股票池 ${result.companyCount} 只 · 本次保存 ${result.saved} 只`);
    renderStockList();
    await suggestStocks($("symbolInput").value);
  } catch (error) {
    renderUniverseStatus(`同步失败：${error.message}`);
  } finally {
    $("syncUniverse").disabled = false;
  }
}

async function batchAnalyze() {
  $("batchAnalyze").disabled = true;
  renderUniverseStatus("正在提交批量分析...");
  try {
    const result = await request("/api/companies/batch-analysis", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ limit: 20 })
    });
    renderUniverseStatus(`已提交 ${result.submitted} 只，失败 ${result.failed} 只`);
    await refresh();
  } catch (error) {
    renderUniverseStatus(`批量分析失败：${error.message}`);
  } finally {
    $("batchAnalyze").disabled = false;
  }
}

async function suggestStocks(query) {
  const normalized = String(query || "").trim();
  const suggestions = normalized
    ? await request(`/api/companies/search?q=${encodeURIComponent(normalized)}&limit=12`).catch(() => [])
    : companies.slice(0, 12);
  $("stockSuggestions").innerHTML = suggestions.map(company =>
    `<option value="${escapeHtml(company.symbol)}">${escapeHtml(company.name)} · ${escapeHtml(company.exchange)} · ${escapeHtml(company.industry)}</option>`
  ).join("");
}

async function search() {
  const query = encodeURIComponent($("searchInput").value);
  const results = await request(`/api/document-index/${symbol}/search?q=${query}`).catch(() => []);
  const visible = results.slice(0, 3);
  $("retrievalList").innerHTML = results.map(hit =>
    item(hit.title, hit.text, `${hit.section} · 相关度 ${Number(hit.score).toFixed(2)}`)
  ).join("") || item("暂无证据", "点击生成 AI 研报后，系统会建立该股票的公开财报和公告证据索引。");
  $("evidencePreviewPanel").innerHTML = visible.map(hit => `
    <article class="evidence-mini">
      <strong>${escapeHtml(hit.title)}</strong>
      <p>${escapeHtml(hit.text)}</p>
      <span>${escapeHtml(hit.section)} · ${Number(hit.score).toFixed(2)}</span>
    </article>
  `).join("") || `<p>暂无证据预览。生成 AI 研报后，这里会显示最相关的 3 条证据。</p>`;
}

async function selectSymbol(nextSymbol) {
  symbol = normalizeSymbol(nextSymbol);
  $("symbolInput").value = symbol;
  await refresh();
  await search();
}

function ratingClass(rating) {
  return rating === "积极" ? "positive" : rating === "谨慎" ? "cautious" : "neutral";
}

function analysisMeta(aiAnalysis) {
  return `${aiAnalysis.aiGenerated ? "Ollama 本地模型" : "规则兜底"} · ${aiAnalysis.cacheHit ? "缓存命中" : "新报告"} · ${formatDateTime(aiAnalysis.generatedAt) || aiAnalysis.source || "AI 分析"}`;
}

function statusName(level) {
  return ({
    good: "健康",
    watch: "关注",
    risk: "风险",
    empty: "暂无数据"
  })[level] || level;
}

function formatPrice(value) {
  const number = numeric(value);
  return number > 0 ? number.toFixed(2) : "--";
}

function formatNumber(value, digits = 2) {
  if (value == null || value === "") {
    return "--";
  }
  const number = numeric(value);
  return Number.isFinite(number) ? number.toFixed(digits) : "--";
}

function formatSigned(value) {
  const number = numeric(value);
  if (!Number.isFinite(number)) {
    return "--";
  }
  return `${number > 0 ? "+" : ""}${number.toFixed(2)}`;
}

function formatVolume(value) {
  const number = numeric(value);
  if (number >= 100000000) {
    return `${(number / 100000000).toFixed(2)}亿`;
  }
  if (number >= 10000) {
    return `${(number / 10000).toFixed(2)}万`;
  }
  return number.toLocaleString("zh-CN");
}

function formatMoney(value) {
  const number = numeric(value);
  if (!number) {
    return "--";
  }
  if (number >= 100000000) {
    return `${(number / 100000000).toFixed(2)}亿`;
  }
  if (number >= 10000) {
    return `${(number / 10000).toFixed(2)}万`;
  }
  return number.toLocaleString("zh-CN");
}

function formatDateTime(value) {
  return value ? String(value).replace("T", " ").replace("Z", "") : "";
}

function numeric(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function normalizeSymbol(value) {
  const next = String(value || "").trim().toUpperCase();
  if (!next) {
    throw new Error("请输入股票代码");
  }
  return next;
}

$("runWorkflow").addEventListener("click", runWorkflow);
$("searchButton").addEventListener("click", search);
$("openEvidence").addEventListener("click", () => {
  $("evidence").scrollIntoView({ behavior: "smooth", block: "start" });
});
$("symbolInput").addEventListener("keydown", event => {
  if (event.key === "Enter") {
    runWorkflow();
  }
});
$("symbolInput").addEventListener("input", event => {
  clearTimeout(suggestionTimer);
  suggestionTimer = setTimeout(() => suggestStocks(event.target.value), 180);
});
document.querySelectorAll(".range-tab").forEach(button => {
  button.addEventListener("click", async () => {
    document.querySelectorAll(".range-tab").forEach(item => item.classList.remove("active"));
    button.classList.add("active");
    chartLimit = Number(button.dataset.limit || 120);
    await refresh();
  });
});
window.addEventListener("resize", () => renderChart(latestCandles, latestQuote, latestAiAnalysis));

refresh().then(() => Promise.all([search(), suggestStocks(symbol)])).catch(error => {
  $("analysisConclusion").textContent = `后端服务未就绪：${error.message}`;
});
