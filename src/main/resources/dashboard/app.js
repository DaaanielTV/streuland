const tokenInput = document.getElementById('tokenInput');
const connectBtn = document.getElementById('connectBtn');
const authStatus = document.getElementById('authStatus');
const plotTableBody = document.querySelector('#plotTable tbody');
const plotDetails = document.getElementById('plotDetails');
const refreshBtn = document.getElementById('refreshBtn');
const searchInput = document.getElementById('searchInput');
const ownerInput = document.getElementById('ownerInput');
const areaTypeFilter = document.getElementById('areaTypeFilter');
const marketFilter = document.getElementById('marketFilter');

let authToken = localStorage.getItem('streuland.dashboard.token') || '';
let selectedPlotId = null;
let socket = null;

tokenInput.value = authToken;

function headers() {
  return authToken ? { 'Authorization': `Bearer ${authToken}` } : {};
}

function buildQuery() {
  const params = new URLSearchParams();
  if (searchInput.value.trim()) params.set('search', searchInput.value.trim());
  if (ownerInput.value.trim()) params.set('owner', ownerInput.value.trim());
  if (areaTypeFilter.value) params.set('areaType', areaTypeFilter.value);
  if (marketFilter.value) params.set('marketStatus', marketFilter.value);
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
  const query = buildQuery();
  const payload = await fetchJson(`/api/dashboard/plots${query ? `?${query}` : ''}`);
  renderPlots(payload.plots || []);
}

function renderPlots(plots) {
  plotTableBody.innerHTML = '';
  plots.forEach((plot) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${plot.plotId}</td>
      <td>${plot.owner || '-'}</td>
      <td>${(plot.trustedPlayers || []).join(', ') || '-'}</td>
      <td>${plot.areaType}</td>
      <td>${(plot.upgrades || []).join(', ') || '-'}</td>
      <td>${plot.marketStatus}${plot.marketPrice ? ` (${plot.marketPrice})` : ''}</td>
    `;
    tr.addEventListener('click', () => loadPlotDetails(plot.plotId));
    plotTableBody.appendChild(tr);
  });
}

async function loadPlotDetails(plotId) {
  selectedPlotId = plotId;
  const details = await fetchJson(`/api/dashboard/plots/${encodeURIComponent(plotId)}`);
  plotDetails.textContent = JSON.stringify(details, null, 2);
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
  await Promise.all([loadPlots(), loadPlotDetails(selectedPlotId)]);
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
    await loadPlots();
    connectRealtime();
    authStatus.textContent = 'Authenticated';
  } catch (error) {
    authStatus.textContent = `Auth failed (${error.message})`;
  }
});

refreshBtn.addEventListener('click', () => loadPlots());
[searchInput, ownerInput, areaTypeFilter, marketFilter].forEach((el) => {
  el.addEventListener('change', () => loadPlots());
});

document.querySelectorAll('.trustedAction').forEach((btn) => {
  btn.addEventListener('click', () => mutateTrusted(btn.dataset.action));
});

loadPlots().then(connectRealtime).catch(() => {
  authStatus.textContent = 'Provide token and connect';
});
