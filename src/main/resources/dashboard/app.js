const tokenInput = document.getElementById('tokenInput');
const connectBtn = document.getElementById('connectBtn');
const authStatus = document.getElementById('authStatus');
const refreshBtn = document.getElementById('refreshBtn');

const plotTableBody = document.querySelector('#plotTable tbody');
const plotDetails = document.getElementById('plotDetails');
const backupTableBody = document.querySelector('#backupTable tbody');
const eventTableBody = document.querySelector('#eventTable tbody');
const permissionResult = document.getElementById('permissionResult');

// Invitations panel (MVP)
const inviteTableBody = document.querySelector('#inviteTable tbody');
const inviteCodeInput = document.getElementById('inviteCodeInput');
const inviteExpiresInput = document.getElementById('inviteExpiresInput');
const inviteMaxUsesInput = document.getElementById('inviteMaxUsesInput');
const inviteRolesInput = document.getElementById('inviteRolesInput');
const inviteServerInput = document.getElementById('inviteServerInput');
const inviteCreateBtn = document.getElementById('inviteCreateBtn');
const inviteDefaultsRolesInput = document.getElementById('inviteDefaultsRolesInput');
const inviteDefaultsExpiresDaysInput = document.getElementById('inviteDefaultsExpiresDaysInput');
const inviteDefaultsMaxUsesInput = document.getElementById('inviteDefaultsMaxUsesInput');
const inviteDefaultsSaveBtn = document.getElementById('inviteDefaultsSaveBtn');
let inviteDefaults = {
  roles: ['player'],
  expiresDays: 30,
  maxUses: null
};

function loadInviteDefaultsFromStorage() {
  try {
    const s = localStorage.getItem('streuland.dashboard.inviteDefaults');
    if (!s) return false;
    const parsed = JSON.parse(s);
    inviteDefaults = Object.assign({}, inviteDefaults, parsed);
    return true;
  } catch {
    return false;
  }
}

function saveInviteDefaultsToStorage() {
  localStorage.setItem('streuland.dashboard.inviteDefaults', JSON.stringify(inviteDefaults));
}

function getInviteDefault(key) {
  if (key === 'roles') return inviteDefaults.roles;
  if (key === 'expiresAt') {
    // compute absolute expiry from days
    const dt = new Date();
    dt.setDate(dt.getDate() + inviteDefaults.expiresDays);
    return dt.toISOString();
  }
  if (key === 'maxUses') return inviteDefaults.maxUses;
  return null;
}

function updateDefaultsUI() {
  if (inviteDefaultsRolesInput) inviteDefaultsRolesInput.value = (inviteDefaults.roles || []).join(', ');
  if (inviteDefaultsExpiresDaysInput) inviteDefaultsExpiresDaysInput.value = inviteDefaults.expiresDays;
  if (inviteDefaultsMaxUsesInput) inviteDefaultsMaxUsesInput.value = inviteDefaults.maxUses == null ? '' : inviteDefaults.maxUses;
  const info = `Default roles: ${inviteDefaults.roles.join(', ')} • expiry days: ${inviteDefaults.expiresDays} • max uses: ${inviteDefaults.maxUses == null ? 'unlimited' : inviteDefaults.maxUses}`;
  const infoEl = document.getElementById('inviteDefaultsInfo');
  if (infoEl) infoEl.textContent = info;
}

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

async function loadInvites() {
  try {
    const payload = await fetchJson('/api/dashboard/ops/invitations');
    const invites = payload.invitations || [];
    renderInvites(invites);
  } catch (e) {
    console.error('Failed to load invites', e);
  }
}

function renderInvites(invites) {
  inviteTableBody.innerHTML = '';
  invites.forEach((inv) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${inv.code || ''}</td>
      <td>${inv.expiresAt || '-'}</td>
      <td>${inv.uses != null ? inv.uses : 0}</td>
      <td>${inv.maxUses == null ? '∞' : inv.maxUses}</td>
      <td>${inv.isRevoked ? 'Yes' : 'No'}</td>
      <td>${(inv.allowedRoles || []).join(', ')}</td>
      <td>${inv.serverId || ''}</td>
      <td><button class="inviteCopyBtn" data-code="${inv.code}">Copy URL</button></td>
      <td><button class="inviteRevokeBtn" data-id="${inv.id}">Revoke</button></td>
    `;
    tr.querySelector('.inviteRevokeBtn').addEventListener('click', async () => {
      await revokeInvite(inv.id);
      await loadInvites();
    });
    tr.querySelector('.inviteCopyBtn').addEventListener('click', () => {
      const url = window.location.origin + '/auth/signup-with-code?code=' + encodeURIComponent(inv.code);
      navigator.clipboard.writeText(url).catch(() => {
        // fallback
        window.prompt('Invite URL', url);
      });
    });
    inviteTableBody.appendChild(tr);
  });
}

async function revokeInvite(id) {
  const url = `/api/dashboard/ops/invitations/${id}/revoke`;
  try {
    await fetch(url, { method: 'POST' });
  } catch (e) {
    console.error('Failed to revoke invite', e);
  }
}

inviteCreateBtn.addEventListener('click', async () => {
  const code = inviteCodeInput.value.trim() || null;
  const expiresAt = inviteExpiresInput.value || null;
  const maxUses = inviteMaxUsesInput.value ? parseInt(inviteMaxUsesInput.value, 10) : null;
  const rolesRaw = inviteRolesInput.value || '';
  const allowedRoles = rolesRaw ? rolesRaw.split(',').map(s => s.trim()).filter(s => s) : null;
  const serverId = inviteServerInput.value || null;
  // Merge defaults if any field is missing
  const mergedExpiresAt = expiresAt || getInviteDefault('expiresAt');
  const mergedRoles = allowedRoles || getInviteDefault('roles');
  const payload = {
    code,
    issuerUserId: 'dashboard-admin',
    expiresAt: mergedExpiresAt,
    maxUses,
    allowedRoles: mergedRoles,
    serverId,
    targetServer: null
  };
  try {
    // simple POST helper
    const res = await fetch('/api/dashboard/ops/invitations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    await loadInvites();
    // Clear form
    inviteCodeInput.value = '';
    inviteExpiresInput.value = '';
    inviteMaxUsesInput.value = '';
    inviteRolesInput.value = '';
    inviteServerInput.value = '';
  } catch (e) {
    console.error('Failed to create invite', e);
  }
});

// Initial load
loadInviteDefaultsFromStorage();
updateDefaultsUI();
Promise.all([loadPlots(), loadEvents(), loadInvites()]).then(connectRealtime).catch(() => {
  authStatus.textContent = 'Provide token and connect';
});

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
// Invite defaults save handler
inviteDefaultsSaveBtn.addEventListener('click', () => {
  const rolesVal = (inviteDefaultsRolesInput.value || '').trim();
  inviteDefaults.roles = rolesVal ? rolesVal.split(',').map(s => s.trim()).filter(s => s) : ['player'];
  const daysVal = parseInt(inviteDefaultsExpiresDaysInput.value, 10);
  inviteDefaults.expiresDays = Number.isFinite(daysVal) ? daysVal : inviteDefaults.expiresDays;
  const maxUsesVal = inviteDefaultsMaxUsesInput.value;
  inviteDefaults.maxUses = maxUsesVal === '' ? null : Number(maxUsesVal);
  saveInviteDefaultsToStorage();
  updateDefaultsUI();
});
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
