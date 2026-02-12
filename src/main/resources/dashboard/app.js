const plotStats = document.getElementById('plotStats');
const biomeStats = document.getElementById('biomeStats');
const upgradeStats = document.getElementById('upgradeStats');
const heatmapCanvas = document.getElementById('heatmap');
const ctx = heatmapCanvas.getContext('2d');

async function loadSummary() {
  const [plots, biomes, upgrades] = await Promise.all([
    fetch('/api/plots').then(r => r.json()),
    fetch('/api/biomes').then(r => r.json()),
    fetch('/api/upgrades').then(r => r.json())
  ]);

  plotStats.innerHTML = `<h3>Plots</h3><p>Total: ${plots.total}<br>Claimed: ${plots.claimed}<br>Unclaimed: ${plots.unclaimed}</p>`;
  biomeStats.innerHTML = `<h3>Biomes</h3><p>${biomes.biomes.map(b => `${b.biome}: ${b.plots}`).join('<br>')}</p>`;
  upgradeStats.innerHTML = `<h3>Upgrades</h3><p>Total upgrades: ${upgrades.totalUpgrades}<br>Avg level: ${upgrades.averageUpgradeLevel}</p>`;
}

function renderHeatmap(grid) {
  ctx.clearRect(0, 0, heatmapCanvas.width, heatmapCanvas.height);
  if (!grid.length) return;

  const rows = grid.length;
  const cols = grid[0].length;
  const cellW = heatmapCanvas.width / cols;
  const cellH = heatmapCanvas.height / rows;
  let max = 1;
  grid.forEach(r => r.forEach(v => max = Math.max(max, v)));

  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const intensity = grid[y][x] / max;
      const red = Math.floor(255 * intensity);
      const green = Math.floor(130 * (1 - intensity));
      ctx.fillStyle = `rgba(${red},${green},255,0.8)`;
      ctx.fillRect(x * cellW, y * cellH, cellW - 1, cellH - 1);
    }
  }
}

async function loadHeatmap() {
  const districtId = document.getElementById('districtId').value;
  const data = await fetch(`/api/districts/${districtId}/heatmap`).then(r => r.json());
  renderHeatmap(data.grid || []);
}

document.getElementById('loadHeatmap').addEventListener('click', loadHeatmap);

function connectRealtime() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  const wsPort = Number(new URLSearchParams(location.search).get('wsPort') || 8081);
  const socket = new WebSocket(`${protocol}://${location.hostname}:${wsPort}`);
  socket.onmessage = (event) => {
    const payload = JSON.parse(event.data);
    if (payload.type === 'plot_created' || payload.type === 'plot_upgraded') {
      loadSummary();
      loadHeatmap();
    }
  };
}

loadSummary();
loadHeatmap();
connectRealtime();
