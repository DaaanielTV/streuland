const roleBanner = document.getElementById('roleBanner');
const listingSelect = document.getElementById('listingSelect');
const neighborhoodCanvas = document.getElementById('neighborhoodGraph');
const neighborhoodCtx = neighborhoodCanvas.getContext('2d');

const params = new URLSearchParams(location.search);
const role = (params.get('role') || 'viewer').toLowerCase();
roleBanner.textContent = `Role: ${role}${role === 'admin' ? ' (full control)' : ' (read-only)'}`;

const map = L.map('map').setView([0, 0], 2);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);

let plotsLayer = null;
let heatLayer = null;
let marketChart = null;
let biomeChart = null;

function ownerColor(owner) {
  let hash = 0;
  for (let i = 0; i < owner.length; i++) hash = ((hash << 5) - hash) + owner.charCodeAt(i);
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue}, 70%, 50%)`;
}

async function loadPlots() {
  const geo = await fetch('/api/map/plots').then(r => r.json());
  if (plotsLayer) map.removeLayer(plotsLayer);
  plotsLayer = L.geoJSON(geo, {
    style: f => ({ color: ownerColor(f.properties.owner), weight: 2, fillOpacity: 0.25 }),
    onEachFeature: (f, layer) => {
      layer.bindPopup(`Plot ${f.properties.plotId}<br>Biome: ${f.properties.biome}<br>Level: ${f.properties.level}`);
    }
  }).addTo(map);
  const bounds = plotsLayer.getBounds();
  if (bounds.isValid()) map.fitBounds(bounds.pad(0.15));
}

async function loadHeatmap() {
  const heat = await fetch('/api/map/heatmap').then(r => r.json());
  if (heatLayer) map.removeLayer(heatLayer);
  const circles = heat.points.map(p => L.circle([p.z, p.x], {
    radius: 20 + (p.intensity * 5),
    color: '#ef4444',
    fillColor: '#f97316',
    fillOpacity: Math.min(0.85, p.intensity / 8)
  }).bindTooltip(`${p.plotId}: ${p.intensity} recent edits`));
  heatLayer = L.layerGroup(circles).addTo(map);
}

function drawNeighborhoodGraph(payload) {
  neighborhoodCtx.clearRect(0, 0, neighborhoodCanvas.width, neighborhoodCanvas.height);
  const nodes = payload.nodes || [];
  const edges = payload.edges || [];
  if (!nodes.length) {
    neighborhoodCtx.fillStyle = '#ddd';
    neighborhoodCtx.fillText('No neighborhood data', 20, 24);
    return;
  }
  const minX = Math.min(...nodes.map(n => n.x));
  const maxX = Math.max(...nodes.map(n => n.x));
  const minZ = Math.min(...nodes.map(n => n.z));
  const maxZ = Math.max(...nodes.map(n => n.z));
  const pos = {};
  nodes.forEach((n) => {
    const x = 30 + ((n.x - minX) / Math.max(1, maxX - minX)) * (neighborhoodCanvas.width - 60);
    const y = 30 + ((n.z - minZ) / Math.max(1, maxZ - minZ)) * (neighborhoodCanvas.height - 60);
    pos[n.plotId] = {x, y};
  });
  edges.forEach((e) => {
    const a = pos[e.from], b = pos[e.to];
    if (!a || !b) return;
    neighborhoodCtx.strokeStyle = `rgba(59,130,246,${Math.min(1, e.resourceFlow / 8)})`;
    neighborhoodCtx.lineWidth = Math.max(1, e.resourceFlow / 2);
    neighborhoodCtx.beginPath(); neighborhoodCtx.moveTo(a.x, a.y); neighborhoodCtx.lineTo(b.x, b.y); neighborhoodCtx.stroke();
  });
  nodes.forEach((n) => {
    const p = pos[n.plotId];
    neighborhoodCtx.fillStyle = n.plotId === payload.centerPlotId ? '#f59e0b' : '#34d399';
    neighborhoodCtx.beginPath(); neighborhoodCtx.arc(p.x, p.y, 8, 0, Math.PI * 2); neighborhoodCtx.fill();
    neighborhoodCtx.fillStyle = '#fff';
    neighborhoodCtx.fillText(n.plotId, p.x + 10, p.y + 4);
  });
}

async function loadNeighborhood() {
  const plotId = document.getElementById('plotIdInput').value.trim();
  if (!plotId) return;
  const payload = await fetch(`/api/neighborhoods/${plotId}`).then(r => r.json());
  drawNeighborhoodGraph(payload);
}

async function loadMarket() {
  const data = await fetch('/api/market/listings').then(r => r.json());
  listingSelect.innerHTML = '';
  data.listings.forEach((l, idx) => {
    const option = document.createElement('option');
    option.value = idx;
    option.textContent = `${l.plotId} - ${l.price}$`;
    listingSelect.appendChild(option);
  });
  const renderSelected = () => {
    const selected = data.listings[Number(listingSelect.value) || 0];
    if (!selected) return;
    const labels = selected.priceHistory.map(p => new Date(p.timestamp).toLocaleDateString());
    const prices = selected.priceHistory.map(p => p.price);
    if (marketChart) marketChart.destroy();
    marketChart = new Chart(document.getElementById('marketChart'), {
      type: 'line',
      data: { labels, datasets: [{ label: `${selected.plotId} price history`, data: prices, borderColor: '#60a5fa' }] },
      options: { responsive: true, plugins: { legend: { labels: { color: '#fff' } } }, scales: { x: { ticks: { color: '#fff' } }, y: { ticks: { color: '#fff' } } } }
    });
  };
  listingSelect.onchange = renderSelected;
  renderSelected();
}

async function loadBiomeStats() {
  const data = await fetch('/api/biomes/stats').then(r => r.json());
  if (biomeChart) biomeChart.destroy();
  biomeChart = new Chart(document.getElementById('biomeChart'), {
    type: 'bar',
    data: {
      labels: data.biomes.map(b => b.biome),
      datasets: [
        { label: 'Total Area', data: data.biomes.map(b => b.totalArea), backgroundColor: '#22c55e' },
        { label: 'Player Count', data: data.biomes.map(b => b.playerCount), backgroundColor: '#a78bfa' }
      ]
    },
    options: { responsive: true, plugins: { legend: { labels: { color: '#fff' } } }, scales: { x: { ticks: { color: '#fff' } }, y: { ticks: { color: '#fff' } } } }
  });
}

function connectRealtime() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  const wsPort = Number(params.get('wsPort') || 8081);
  const socket = new WebSocket(`${protocol}://${location.hostname}:${wsPort}`);
  socket.onmessage = async (event) => {
    const payload = JSON.parse(event.data);
    if (payload.type === 'plot_created' || payload.type === 'plot_upgraded') {
      await Promise.all([loadPlots(), loadHeatmap(), loadBiomeStats(), loadMarket()]);
    }
  };
}

document.getElementById('loadNeighborhood').addEventListener('click', loadNeighborhood);

Promise.all([loadPlots(), loadHeatmap(), loadMarket(), loadBiomeStats()]);
connectRealtime();
