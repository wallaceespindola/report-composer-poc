'use strict';

const POLL_MS = 3000;
const API = '/api/v1';

const state = {
  tenants: [],
  reportTypes: new Map(), // code -> description
  expandedJobId: null,
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

async function loadTenantsAndReportTypes() {
  const [tenantsRes, reportTypesRes] = await Promise.all([
    api('/tenants'),
    api('/report-types'),
  ]);

  state.reportTypes = new Map(reportTypesRes.reportTypes.map((rt) => [rt.code, rt.description]));
  state.tenants = tenantsRes.tenants.filter((t) => t.enabled);

  el.tenantSelect.innerHTML = state.tenants
    .map((t) => `<option value="${escapeHtml(t.tenantId)}">${escapeHtml(t.countryCode)} — ${escapeHtml(t.tenantId)}</option>`)
    .join('');

  if (state.tenants.length > 0) {
    populateReportTypesFor(state.tenants[0].tenantId);
  }
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

// ---- Init ----

async function init() {
  try {
    await loadTenantsAndReportTypes();
    clearError();
  } catch (err) {
    showError(err.message);
  }
  refreshJobs();
  setInterval(refreshJobs, POLL_MS);
}

init();
