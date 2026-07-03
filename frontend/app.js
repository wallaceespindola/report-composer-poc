'use strict';

const POLL_MS = 3000;
const STATS_POLL_MS = 10000;
const API = '/api/v1';

const state = {
  tenants: [], // enabled tenants only (job-start dropdown)
  tenantsAll: [], // all tenants (admin forms / doc filters)
  reportTypes: new Map(), // code -> description
  expandedJobId: null,
  docFilters: { tenantId: '', reportType: '', businessDate: '' },
};

const el = {
  errorBanner: document.getElementById('error-banner'),
  tenantSelect: document.getElementById('tenant'),
  reportTypeSelect: document.getElementById('report-type'),
  jobForm: document.getElementById('job-form'),
  jobFormMsg: document.getElementById('job-form-msg'),
  jobsTbody: document.getElementById('jobs-tbody'),
};

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function showError(message) {
  el.errorBanner.textContent = message;
  el.errorBanner.classList.remove('hidden');
}

function clearError() {
  el.errorBanner.classList.add('hidden');
}

async function api(path, options) {
  let res;
  try {
    res = await fetch(`${API}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (err) {
    throw new Error('Network error — is the backend reachable?');
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.message || body.error || `Request failed (${res.status})`);
  }
  return body;
}

function statusClass(status) {
  const s = String(status || '').toUpperCase();
  if (s === 'COMPLETED') return 'badge-green';
  if (s === 'FAILED') return 'badge-red';
  if (s.includes('RUNNING') || s.includes('STARTED')) return 'badge-blue';
  return 'badge-gray';
}

function fmtTime(t) {
  return t ? new Date(t).toLocaleString() : '—';
}

// ---- Tenant / report-type setup ----

function populateReportTypesFor(tenantId) {
  const tenant = state.tenants.find((t) => t.tenantId === tenantId);
  const codes = tenant ? tenant.reportTypes : [];
  el.reportTypeSelect.innerHTML = codes
    .map((code) => {
      const desc = state.reportTypes.get(code);
      const label = desc ? `${code} — ${desc}` : code;
      return `<option value="${escapeHtml(code)}">${escapeHtml(label)}</option>`;
    })
    .join('');
}

function tenantOptionsHtml(tenants) {
  return tenants
    .map((t) => `<option value="${escapeHtml(t.tenantId)}">${escapeHtml(t.countryCode)} — ${escapeHtml(t.tenantId)}</option>`)
    .join('');
}

function reportTypeOptionsHtml() {
  return Array.from(state.reportTypes.entries())
    .map(([code, desc]) => `<option value="${escapeHtml(code)}">${escapeHtml(desc ? `${code} — ${desc}` : code)}</option>`)
    .join('');
}

async function loadTenantsAndReportTypes() {
  const [tenantsRes, reportTypesRes] = await Promise.all([
    api('/tenants'),
    api('/report-types'),
  ]);

  state.reportTypes = new Map(reportTypesRes.reportTypes.map((rt) => [rt.code, rt.description]));
  state.tenantsAll = tenantsRes.tenants;
  state.tenants = tenantsRes.tenants.filter((t) => t.enabled);

  el.tenantSelect.innerHTML = tenantOptionsHtml(state.tenants);
  if (state.tenants.length > 0) {
    populateReportTypesFor(state.tenants[0].tenantId);
  }

  document.getElementById('contract-tenant').innerHTML = tenantOptionsHtml(state.tenantsAll);
  document.getElementById('contract-report-type').innerHTML = reportTypeOptionsHtml();
  document.getElementById('txn-tenant').innerHTML = tenantOptionsHtml(state.tenantsAll);

  const docTenantSelect = document.getElementById('doc-filter-tenant');
  docTenantSelect.innerHTML = `<option value="">All</option>${tenantOptionsHtml(state.tenantsAll)}`;
  const docTypeSelect = document.getElementById('doc-filter-type');
  docTypeSelect.innerHTML = `<option value="">All</option>${reportTypeOptionsHtml()}`;
}

el.tenantSelect.addEventListener('change', () => {
  populateReportTypesFor(el.tenantSelect.value);
});

// ---- Start job form ----

el.jobForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  el.jobFormMsg.textContent = '';
  el.jobFormMsg.className = 'form-msg';

  const tenantId = el.tenantSelect.value;
  const reportType = el.reportTypeSelect.value;
  const businessDate = document.getElementById('business-date').value;

  try {
    const res = await api('/jobs', {
      method: 'POST',
      body: JSON.stringify({ tenantId, reportType, businessDate }),
    });
    el.jobFormMsg.textContent = `Started job ${res.jobExecutionId} (${res.status})`;
    el.jobFormMsg.classList.add('ok');
    refreshJobs();
  } catch (err) {
    el.jobFormMsg.textContent = err.message;
    el.jobFormMsg.classList.add('err');
  }
});

// ---- Jobs table ----

async function restartJob(jobExecutionId) {
  try {
    await api(`/jobs/${jobExecutionId}/restart`, { method: 'POST' });
    refreshJobs();
  } catch (err) {
    showError(err.message);
  }
}

function progressBar(job) {
  const total = job.partitionsTotal || 0;
  const done = job.partitionsCompleted || 0;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;
  return `
    <div class="progress-text">${done}/${total} (failed ${job.partitionsFailed || 0})</div>
    <div class="progress-bar"><div class="progress-fill" style="width:${pct}%"></div></div>
  `;
}

function jobRowHtml(job) {
  const expanded = state.expandedJobId === job.jobExecutionId;
  return `
    <tr class="job-row" data-job-id="${escapeHtml(job.jobExecutionId)}">
      <td class="expand-toggle">${expanded ? '▾' : '▸'}</td>
      <td>${escapeHtml(job.jobExecutionId)}</td>
      <td>${escapeHtml(job.tenantId)}</td>
      <td>${escapeHtml(job.reportType)}</td>
      <td>${escapeHtml(job.businessDate)}</td>
      <td><span class="badge ${statusClass(job.status)}">${escapeHtml(job.status)}</span></td>
      <td>${progressBar(job)}</td>
      <td>${fmtTime(job.startTime)}</td>
      <td>${fmtTime(job.endTime)}</td>
      <td>${String(job.status).toUpperCase() === 'FAILED'
        ? `<button class="restart-btn" data-job-id="${escapeHtml(job.jobExecutionId)}">Restart</button>`
        : ''}</td>
    </tr>
    ${expanded ? `<tr class="partitions-row"><td colspan="10"><div id="partitions-${escapeHtml(job.jobExecutionId)}" class="partitions-container">Loading…</div></td></tr>` : ''}
  `;
}

function partitionsTableHtml(partitions) {
  if (partitions.length === 0) return '<p class="muted">No partitions.</p>';
  const rows = partitions.map((p) => `
    <tr>
      <td>${escapeHtml(p.accountId)}</td>
      <td><span class="badge ${statusClass(p.status)}">${escapeHtml(p.status)}</span></td>
      <td>${escapeHtml(p.attemptCount)}</td>
      <td>${String(p.status).toUpperCase() === 'COMPLETED'
        ? `<a href="${API}/reports/${encodeURIComponent(p.workUnitId)}">Download</a>`
        : '—'}</td>
    </tr>
  `).join('');
  return `
    <table class="partitions-table">
      <thead><tr><th>Account</th><th>Status</th><th>Attempts</th><th>Report</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

async function loadPartitions(jobExecutionId) {
  const container = document.getElementById(`partitions-${jobExecutionId}`);
  if (!container) return;
  try {
    const res = await api(`/jobs/${jobExecutionId}/partitions`);
    container.innerHTML = partitionsTableHtml(res.partitions);
  } catch (err) {
    container.innerHTML = `<p class="err">${escapeHtml(err.message)}</p>`;
  }
}

async function refreshJobs() {
  try {
    const res = await api('/jobs');
    el.jobsTbody.innerHTML = res.jobs.map(jobRowHtml).join('');
    clearError();
    if (state.expandedJobId) {
      loadPartitions(state.expandedJobId);
    }
  } catch (err) {
    showError(err.message);
  }
}

el.jobsTbody.addEventListener('click', (e) => {
  const restartBtn = e.target.closest('.restart-btn');
  if (restartBtn) {
    restartJob(restartBtn.dataset.jobId);
    return;
  }
  const row = e.target.closest('.job-row');
  if (!row) return;
  const jobId = row.dataset.jobId;
  state.expandedJobId = state.expandedJobId === jobId ? null : jobId;
  refreshJobs();
});

// ---- Stats bar ----

function formatBytes(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let val = n / 1024;
  let i = 0;
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024;
    i += 1;
  }
  return `${val.toFixed(1)} ${units[i]}`;
}

function statusBreakdown(byStatus) {
  return Object.entries(byStatus || {})
    .map(([status, count]) => `${count} ${status}`)
    .join(' · ');
}

async function refreshStats() {
  try {
    const stats = await api('/stats');
    document.getElementById('stat-workers').textContent = stats.activeWorkerPods > 0 ? stats.activeWorkerPods : '—';
    document.getElementById('stat-workers-sub').textContent =
      stats.activeWorkerPods > 0 ? `${stats.workerConsumerThreads} threads` : '';
    document.getElementById('stat-tenants').textContent = stats.tenants;
    document.getElementById('stat-contracts').textContent = stats.contracts;
    document.getElementById('stat-accounts').textContent = stats.accounts;
    document.getElementById('stat-transactions').textContent = stats.transactions;
    document.getElementById('stat-documents').textContent = stats.artifacts;
    document.getElementById('stat-documents-sub').textContent = formatBytes(stats.artifactBytes);

    const jobsTotal = Object.values(stats.jobsByStatus || {}).reduce((a, b) => a + b, 0);
    document.getElementById('stat-jobs').textContent = jobsTotal;
    document.getElementById('stat-jobs-sub').textContent = statusBreakdown(stats.jobsByStatus);

    const partitionsTotal = Object.values(stats.workUnitsByStatus || {}).reduce((a, b) => a + b, 0);
    document.getElementById('stat-partitions').textContent = partitionsTotal;
    document.getElementById('stat-partitions-sub').textContent = statusBreakdown(stats.workUnitsByStatus);
  } catch (err) {
    // ponytail: stats bar is best-effort, don't spam the global error banner every 10s
  }
}

// ---- Admin panel (tabs + forms) ----

document.querySelectorAll('.tab-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach((b) => {
      b.classList.remove('active');
      b.setAttribute('aria-selected', 'false');
    });
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
    btn.classList.add('active');
    btn.setAttribute('aria-selected', 'true');
    document.querySelector(`.tab-panel[data-tab-panel="${btn.dataset.tab}"]`).classList.add('active');
  });
});

function setFormMsg(name, message, ok) {
  const span = document.querySelector(`[data-msg-for="${name}"]`);
  span.textContent = message;
  span.className = `form-msg ${ok ? 'ok' : 'err'}`;
}

async function afterAdminAction() {
  await loadTenantsAndReportTypes();
  refreshStats();
  refreshDocuments();
}

document.getElementById('tenant-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const tenantId = document.getElementById('new-tenant-id').value.trim();
  const countryCode = document.getElementById('new-tenant-country').value.trim();
  const locale = document.getElementById('new-tenant-locale').value.trim();
  const currency = document.getElementById('new-tenant-currency').value.trim();
  const seedAccounts = Number(document.getElementById('new-tenant-seed').value) || undefined;
  const businessDate = document.getElementById('new-tenant-date').value
    || document.getElementById('business-date').value;

  try {
    const res = await api('/tenants', {
      method: 'POST',
      body: JSON.stringify({ tenantId, countryCode, locale, currency, seedAccounts, businessDate }),
    });
    setFormMsg('tenant', `Created ${res.tenantId}: ${res.accountsCreated} accounts, ${res.transactionsCreated} transactions`, true);
    await afterAdminAction();
  } catch (err) {
    setFormMsg('tenant', err.message, false);
  }
});

document.getElementById('contract-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const tenantId = document.getElementById('contract-tenant').value;
  const reportType = document.getElementById('contract-report-type').value;
  const effectiveFrom = document.getElementById('contract-effective-from').value || undefined;

  try {
    await api(`/tenants/${encodeURIComponent(tenantId)}/contracts`, {
      method: 'POST',
      body: JSON.stringify({ reportType, effectiveFrom }),
    });
    setFormMsg('contract', `Added contract ${reportType} for ${tenantId}`, true);
    await afterAdminAction();
  } catch (err) {
    setFormMsg('contract', err.message, false);
  }
});

document.getElementById('txn-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const tenantId = document.getElementById('txn-tenant').value;
  const businessDate = document.getElementById('txn-date').value;
  const perAccountRaw = document.getElementById('txn-per-account').value;
  const perAccount = perAccountRaw ? Number(perAccountRaw) : undefined;

  try {
    const res = await api(`/tenants/${encodeURIComponent(tenantId)}/transactions`, {
      method: 'POST',
      body: JSON.stringify({ businessDate, perAccount }),
    });
    setFormMsg('txn', `Generated ${res.transactionsCreated} transactions across ${res.accounts} accounts`, true);
    await afterAdminAction();
  } catch (err) {
    setFormMsg('txn', err.message, false);
  }
});

// ---- Documents ----

function docsTableRowHtml(doc) {
  const checksum = doc.checksum || '';
  return `
    <tr>
      <td>${escapeHtml(doc.fileName)}</td>
      <td>${escapeHtml(doc.tenantId)}</td>
      <td>${escapeHtml(doc.accountId)}</td>
      <td>${escapeHtml(doc.reportType)}</td>
      <td>${escapeHtml(doc.businessDate)}</td>
      <td>${formatBytes(doc.sizeBytes)}</td>
      <td>${fmtTime(doc.createdAt)}</td>
      <td title="${escapeHtml(checksum)}">${escapeHtml(checksum.slice(0, 8))}</td>
      <td>
        <button type="button" class="doc-view-btn" data-work-unit-id="${escapeHtml(doc.workUnitId)}" data-file-name="${escapeHtml(doc.fileName)}">View</button>
        <a href="${API}/reports/${encodeURIComponent(doc.workUnitId)}">Download</a>
      </td>
    </tr>
  `;
}

async function refreshDocuments() {
  const { tenantId, reportType, businessDate } = state.docFilters;
  const params = new URLSearchParams();
  if (tenantId) params.set('tenantId', tenantId);
  if (reportType) params.set('reportType', reportType);
  if (businessDate) params.set('businessDate', businessDate);
  const qs = params.toString();

  try {
    const res = await api(`/reports${qs ? `?${qs}` : ''}`);
    document.getElementById('docs-tbody').innerHTML = res.artifacts.map(docsTableRowHtml).join('');
    setFormMsg('docs', '', true);
  } catch (err) {
    setFormMsg('docs', err.message, false);
  }
}

document.getElementById('doc-filter-tenant').addEventListener('change', (e) => {
  state.docFilters.tenantId = e.target.value;
  refreshDocuments();
});
document.getElementById('doc-filter-type').addEventListener('change', (e) => {
  state.docFilters.reportType = e.target.value;
  refreshDocuments();
});
document.getElementById('doc-filter-date').addEventListener('change', (e) => {
  state.docFilters.businessDate = e.target.value;
  refreshDocuments();
});
document.getElementById('doc-refresh-btn').addEventListener('click', refreshDocuments);

const docModal = {
  overlay: document.getElementById('doc-view-modal'),
  title: document.getElementById('doc-view-title'),
  content: document.getElementById('doc-view-content'),
};

function closeDocModal() {
  docModal.overlay.classList.add('hidden');
}

document.getElementById('doc-view-close').addEventListener('click', closeDocModal);
docModal.overlay.addEventListener('click', (e) => {
  if (e.target === docModal.overlay) closeDocModal();
});

document.getElementById('docs-tbody').addEventListener('click', async (e) => {
  const btn = e.target.closest('.doc-view-btn');
  if (!btn) return;
  const { workUnitId, fileName } = btn.dataset;
  docModal.title.textContent = fileName;
  docModal.content.textContent = 'Loading…';
  docModal.overlay.classList.remove('hidden');
  try {
    const res = await fetch(`${API}/reports/${encodeURIComponent(workUnitId)}?inline=true`);
    const text = await res.text();
    docModal.content.textContent = text;
  } catch (err) {
    docModal.content.textContent = `Error loading document: ${err.message}`;
  }
});

// ---- System links ----

function renderSystemLinks() {
  const proxied = location.port === '3000';
  const apiOrigin = proxied ? 'http://localhost:8080' : '';
  const sameOriginLinks = [
    ['Swagger UI', `${apiOrigin}/swagger-ui.html`],
    ['OpenAPI JSON', `${apiOrigin}/v3/api-docs`],
    ['Health', `${apiOrigin}/health`],
    ['Actuator', `${apiOrigin}/actuator`],
    ['Prometheus metrics', `${apiOrigin}/actuator/prometheus`],
  ];
  const hostLinks = [
    ['API direct', 'http://localhost:8080', ''],
    ['Frontend (nginx)', 'http://localhost:3000', ''],
    ['Kafka UI', 'http://localhost:8082', ''],
    ['MinIO console', 'http://localhost:9001', 'minioadmin / minioadmin'],
    ['H2 web console', 'http://localhost:8083', 'JDBC: jdbc:h2:tcp://h2:1521//opt/h2-data/report;MODE=Oracle · user sa · empty password'],
  ];

  const sameOriginHtml = sameOriginLinks
    .map(([label, href]) => `<li><a href="${escapeHtml(href)}" target="_blank" rel="noopener">${escapeHtml(label)}</a></li>`)
    .join('');
  const hostHtml = hostLinks
    .map(([label, href, hint]) => `
      <li>
        <a href="${escapeHtml(href)}" target="_blank" rel="noopener">${escapeHtml(label)}</a>
        <span class="muted"> (compose default ports)</span>
        ${hint ? `<div class="link-hint">${escapeHtml(hint)}</div>` : ''}
      </li>
    `)
    .join('');

  document.getElementById('system-links').innerHTML = sameOriginHtml + hostHtml;
}

// ---- Init ----

async function init() {
  try {
    await loadTenantsAndReportTypes();
    clearError();
  } catch (err) {
    showError(err.message);
  }
  renderSystemLinks();
  refreshJobs();
  refreshStats();
  refreshDocuments();
  setInterval(refreshJobs, POLL_MS);
  setInterval(() => {
    refreshStats();
    refreshDocuments();
  }, STATS_POLL_MS);
}

init();
