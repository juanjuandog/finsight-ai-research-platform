const symbol = "600519";

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

  $("workflowList").innerHTML = tasks.slice(0, 8).map(task =>
    item(task.taskType, task.idempotencyKey, `${task.status} · attempts ${task.attempts}`)
  ).join("") || item("No workflow tasks", "Run the workflow to seed demo data.");

  $("metricList").innerHTML = metricTable(metrics);

  $("timelineList").innerHTML = timeline.slice(0, 8).map(event =>
    item(event.title, event.summary, `${event.happenedAt} · ${event.type}`)
  ).join("") || item("No timeline events", "Run intelligence rebuild first.");

  $("graphList").innerHTML = [
    item("Entities", `${graph.entities.length} nodes`, graph.entities.slice(0, 6).map(entity => entity.name).join(" · ")),
    item("Relations", `${graph.relations.length} edges`, graph.relations.slice(0, 6).map(relation => relation.relationType).join(" · "))
  ].join("");
}

function metricTable(metrics) {
  const rows = metrics
    .filter(metric => ["REVENUE_YOY", "NET_PROFIT_YOY", "ROE", "OCF_NET_PROFIT", "RECEIVABLE_GROWTH_SPREAD"].includes(metric.code))
    .slice(-18)
    .map(metric => `<tr><td>${metric.fiscalYear}</td><td>${metric.code}</td><td>${escapeHtml(metric.name)}</td><td>${metric.value}</td><td>${metric.formulaVersion}</td></tr>`)
    .join("");
  return `<table><thead><tr><th>Year</th><th>Code</th><th>Name</th><th>Value</th><th>Version</th></tr></thead><tbody>${rows}</tbody></table>`;
}

async function runWorkflow() {
  $("runWorkflow").disabled = true;
  try {
    await request("/api/ingestion/demo/async", { method: "POST" });
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
    item(hit.title, hit.text, `${hit.section} · score ${Number(hit.score).toFixed(2)}`)
  ).join("") || item("No evidence", "Run the workflow and rebuild the document index.");
}

async function runEvaluation() {
  $("runEvaluation").disabled = true;
  try {
    const run = await request("/api/evaluations/rag/run", { method: "POST" });
    $("evaluationSummary").innerHTML = `
      <span class="badge">${run.passedCases}/${run.totalCases} passed</span>
      <span class="badge ${run.averageScore < 0.8 ? "warn" : ""}">avg ${(run.averageScore * 100).toFixed(0)}%</span>
    `;
    $("evaluationList").innerHTML = run.results.map(result =>
      item(result.caseId, result.failures.length ? result.failures.join("; ") : "Passed", `score ${(result.score * 100).toFixed(0)}% · evidence ${result.metrics.evidenceCount} · latency ${result.metrics.latencyMillis}ms`)
    ).join("");
  } finally {
    $("runEvaluation").disabled = false;
  }
}

$("runWorkflow").addEventListener("click", runWorkflow);
$("runEvaluation").addEventListener("click", runEvaluation);
$("searchButton").addEventListener("click", search);

refresh().then(search).catch(error => {
  $("workflowList").innerHTML = item("Backend not ready", error.message);
});

