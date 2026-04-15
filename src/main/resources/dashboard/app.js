const tokenInput = document.getElementById('tokenInput');
const connectBtn = document.getElementById('connectBtn');
const authStatus = document.getElementById('authStatus');
const refreshBtn = document.getElementById('refreshBtn');

const plotTableBody = document.querySelector('#plotTable tbody');
const plotDetails = document.getElementById('plotDetails');
const backupTableBody = document.querySelector('#backupTable tbody');
const eventTableBody = document.querySelector('#eventTable tbody');
const permissionResult = document.getElementById('permissionResult');

const searchInput = document.getElementById('searchInput');
const ownerInput = document.getElementById('ownerInput');
const areaTypeFilter = document.getElementById('areaTypeFilter');
const marketFilter = document.getElementById('marketFilter');

const eventSearchInput = document.getElementById('eventSearchInput');
const eventActionInput = document.getElementById('eventActionInput');
const eventActorInput = document.getElementById('eventActorInput');

let authToken = localStorage.getItem('streuland.dashboard.token') || '';
let selectedPlotId = null;
let selectedSnapshotId = null;
let socket = null;

tokenInput.value = authToken;

function headers() {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

function plotQuery() {
  const params = new URLSearchParams();
  if (searchInput.value.trim()) params.set('search', searchInput.value.trim());
  if (ownerInput.value.trim()) params.set('owner', ownerInput.value.trim());
  if (areaTypeFilter.value) params.set('areaType', areaTypeFilter.value);
  if (marketFilter.value) params.set('marketStatus', marketFilter.value);
  return params.toString();
}

function eventQuery() {
  const params = new URLSearchParams();
  params.set('limit', '100');
  if (selectedPlotId) params.set('plotId', selectedPlotId);
  if (eventSearchInput.value.trim()) params.set('search', eventSearchInput.value.trim());
  if (eventActionInput.value.trim()) params.set('action', eventActionInput.value.trim());
  if (eventActorInput.value.trim()) params.set('actor', eventActorInput.value.trim());
  return params.toString();
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...headers(),
      ...(options.headers || {})
    }
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function loadPlots() {
  const query = plotQuery();
  const payload = await fetchJson(`/api/dashboard/ops/plots${query ? `?${query}` : ''}`);
  renderPlots(payload.plots || []);
}

function renderPlots(plots) {
  plotTableBody.innerHTML = '';
  plots.forEach((plot) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${plot.plotId}</td>
      <td>${plot.status || plot.areaType}</td>
      <td>${plot.owner || '-'}</td>
      <td>${(plot.trustedPlayers || []).length}</td>
      <td>${plot.marketStatus}${plot.marketPrice ? ` (${plot.marketPrice})` : ''}</td>
    `;
    tr.addEventListener('click', () => selectPlot(plot.plotId));
    plotTableBody.appendChild(tr);
  });
}

async function selectPlot(plotId) {
  selectedPlotId = plotId;
  selectedSnapshotId = null;
  await Promise.all([loadPlotDetails(plotId), loadBackups(plotId), loadEvents()]);
}

async function loadPlotDetails(plotId) {
  const details = await fetchJson(`/api/dashboard/ops/plots/${encodeURIComponent(plotId)}`);
  plotDetails.textContent = JSON.stringify(details, null, 2);
}

async function loadBackups(plotId) {
  if (!plotId) {
    backupTableBody.innerHTML = '';
    return;
  }
  const payload = await fetchJson(`/api/dashboard/ops/backups?plotId=${encodeURIComponent(plotId)}`);
  renderBackups(payload.backups || []);
}

function renderBackups(backups) {
  backupTableBody.innerHTML = '';
  backups.forEach((entry) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${entry.snapshotId}</td>
      <td>${entry.createdAt || '-'}</td>
      <td>${entry.note || '-'}</td>
    `;
    tr.addEventListener('click', () => {
      selectedSnapshotId = entry.snapshotId;
      [...backupTableBody.querySelectorAll('tr')].forEach((row) => row.classList.remove('selected'));
      tr.classList.add('selected');
    });
    backupTableBody.appendChild(tr);
  });
}

async function loadEvents() {
  const payload = await fetchJson(`/api/dashboard/ops/audit?${eventQuery()}`);
  renderEvents(payload.events || []);
}

function renderEvents(events) {
  eventTableBody.innerHTML = '';
  events.forEach((event) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${event.timestamp}</td>
      <td>${event.plotId || '-'}</td>
      <td>${event.action}</td>
      <td>${event.actor || '-'}</td>
      <td><code>${JSON.stringify(event.metadata || {})}</code></td>
    `;
    if (event.plotId) {
      tr.addEventListener('click', () => selectPlot(event.plotId));
    }
    eventTableBody.appendChild(tr);
  });
}

async function mutateTrusted(action) {
  if (!selectedPlotId) return;
  const actor = document.getElementById('actorInput').value.trim();
  const target = document.getElementById('targetInput').value.trim();
  await fetchJson(`/api/dashboard/plots/${encodeURIComponent(selectedPlotId)}/trusted`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ actor, target, action })
  });
  await Promise.all([loadPlots(), loadPlotDetails(selectedPlotId), loadEvents()]);
}

async function runPermissionCheck() {
  if (!selectedPlotId) return;
  const player = document.getElementById('permissionPlayerInput').value.trim();
  const permission = document.getElementById('permissionSelect').value;
  const params = new URLSearchParams({ player, permission });
  const payload = await fetchJson(`/api/dashboard/plots/${encodeURIComponent(selectedPlotId)}/permissions?${params.toString()}`);
  permissionResult.textContent = JSON.stringify(payload, null, 2);
}

async function createBackup() {
  if (!selectedPlotId) return;
  await fetchJson(`/api/dashboard/ops/backups?plotId=${encodeURIComponent(selectedPlotId)}&action=create&actor=dashboard-admin`, {
    method: 'POST'
  });
  await Promise.all([loadBackups(selectedPlotId), loadEvents()]);
}

async function restoreBackup() {
  if (!selectedPlotId || !selectedSnapshotId) return;
  await fetchJson(`/api/dashboard/ops/backups?plotId=${encodeURIComponent(selectedPlotId)}&action=restore&snapshotId=${encodeURIComponent(selectedSnapshotId)}&actor=dashboard-admin`, {
    method: 'POST'
  });
  await Promise.all([loadPlotDetails(selectedPlotId), loadEvents()]);
}

function connectRealtime() {
  if (socket) socket.close();
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  const wsPort = new URLSearchParams(location.search).get('wsPort') || '8081';
  socket = new WebSocket(`${protocol}://${location.hostname}:${wsPort}?token=${encodeURIComponent(authToken)}`);

  socket.onopen = () => { authStatus.textContent = 'Connected'; };
  socket.onclose = () => { authStatus.textContent = 'Disconnected'; };
  socket.onmessage = async (event) => {
    const payload = JSON.parse(event.data);
    if (payload.type === 'audit_event' || payload.type === 'backup_updated' || payload.type === 'trust_permissions_updated') {
      await loadEvents();
      if (selectedPlotId) await loadBackups(selectedPlotId);
    }
    if (payload.type?.startsWith('plot_')) {
      await loadPlots();
      if (selectedPlotId && payload.plotId === selectedPlotId) {
        await loadPlotDetails(selectedPlotId);
      }
    }
  };
}

connectBtn.addEventListener('click', async () => {
  authToken = tokenInput.value.trim();
  localStorage.setItem('streuland.dashboard.token', authToken);
  try {
    await Promise.all([loadPlots(), loadEvents()]);
    connectRealtime();
    authStatus.textContent = 'Authenticated';
  } catch (error) {
    authStatus.textContent = `Auth failed (${error.message})`;
  }
});

refreshBtn.addEventListener('click', () => Promise.all([loadPlots(), loadEvents()]));
document.getElementById('eventRefreshBtn').addEventListener('click', () => loadEvents());
document.getElementById('permissionBtn').addEventListener('click', () => runPermissionCheck());
document.getElementById('createBackupBtn').addEventListener('click', () => createBackup());
document.getElementById('restoreBackupBtn').addEventListener('click', () => restoreBackup());

[searchInput, ownerInput, areaTypeFilter, marketFilter].forEach((el) => {
  el.addEventListener('change', () => loadPlots());
});
[eventSearchInput, eventActionInput, eventActorInput].forEach((el) => {
  el.addEventListener('change', () => loadEvents());
});

document.querySelectorAll('.trustedAction').forEach((btn) => {
  btn.addEventListener('click', () => mutateTrusted(btn.dataset.action));
});

Promise.all([loadPlots(), loadEvents()]).then(connectRealtime).catch(() => {
  authStatus.textContent = 'Provide token and connect';
});
