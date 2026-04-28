/* ═══════════════════════════════════════════════════════════════
   Arthayantra — Frontend Logic
   Full integration with the Java backend API endpoints
   ═══════════════════════════════════════════════════════════════ */

const API = window.location.origin;
let eventSource = null;
let prevPrices = {};
let simRunning = false;

// ═══ INITIALIZATION ═══════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
    checkServerStatus();
    loadPortfolio();
    setupFormPreview();

    // Auto-refresh portfolio every 10s
    setInterval(loadPortfolio, 10000);
    setInterval(checkServerStatus, 15000);
});

// ═══ TAB NAVIGATION ══════════════════════════════════════════════

function switchTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    document.getElementById('tab-' + tabName).classList.add('active');
    document.querySelector(`.nav-tab[data-tab="${tabName}"]`).classList.add('active');

    // Load tab-specific data
    if (tabName === 'risk') loadRiskData();
    if (tabName === 'strategies') loadSignals();
}

// ═══ SERVER STATUS ═══════════════════════════════════════════════

async function checkServerStatus() {
    try {
        const res = await fetch(`${API}/status`);
        const data = await res.json();

        const dot = document.querySelector('.status-dot');
        const text = document.querySelector('.status-text');
        dot.classList.add('online');
        dot.classList.remove('offline');
        text.textContent = 'Online';

        const dbDot = document.querySelector('.db-dot');
        const dbText = document.querySelector('.db-text');
        if (data.database === 'connected') {
            dbDot.classList.add('online');
            dbDot.classList.remove('offline');
            dbText.textContent = 'DB: Connected';
        } else {
            dbDot.classList.add('offline');
            dbDot.classList.remove('online');
            dbText.textContent = 'DB: File Mode';
        }

        simRunning = data.simulationRunning || false;
        updateSimButton();
    } catch (e) {
        const dot = document.querySelector('.status-dot');
        const text = document.querySelector('.status-text');
        dot.classList.add('offline');
        dot.classList.remove('online');
        text.textContent = 'Offline';
    }
}

// ═══ PORTFOLIO MANAGEMENT ════════════════════════════════════════

async function loadPortfolio() {
    try {
        const res = await fetch(`${API}/portfolio`);
        const data = await res.json();

        const summary = data.summary || {};
        document.getElementById('statTrades').textContent = summary.tradeCount || 0;
        document.getElementById('statInvested').textContent = '₹' + fmt(summary.totalInvested || 0);
        document.getElementById('statRevenue').textContent = '₹' + fmt(summary.totalRevenue || 0);

        const pl = summary.profitLoss || 0;
        const plEl = document.getElementById('statPL');
        plEl.textContent = (pl >= 0 ? '+₹' : '-₹') + fmt(Math.abs(pl));
        plEl.className = 'stat-value ' + (pl >= 0 ? 'profit' : 'loss');
        document.getElementById('plIcon').textContent = pl >= 0 ? '📈' : '📉';

        renderTrades(data.trades || []);
    } catch (e) {
        console.error('Portfolio load failed:', e);
    }
}

function renderTrades(trades) {
    const body = document.getElementById('tradesBody');
    if (!trades.length) {
        body.innerHTML = '<tr class="empty-row"><td colspan="10"><div class="empty-state"><span class="empty-icon">📭</span><p>No trades yet. Execute your first trade!</p></div></td></tr>';
        return;
    }

    body.innerHTML = trades.map(t => `
        <tr>
            <td>#${t.id}</td>
            <td><span class="badge badge-${t.type === 'BUY' ? 'buy' : 'sell'}">${t.type}</span></td>
            <td>${t.instrument}</td>
            <td>${fmt(t.quantity)}</td>
            <td>${fmt(t.price)}</td>
            <td>₹${fmt(t.quantity * t.price)}</td>
            <td>${t.stopLoss || '—'}</td>
            <td>${t.takeProfit || '—'}</td>
            <td class="text-muted">${formatTime(t.timestamp)}</td>
            <td>
                <div class="actions-cell">
                    <button class="btn btn-sm btn-edit" onclick="openUpdateModal(${t.id}, ${t.quantity}, ${t.price})">✏️</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteTrade(${t.id})">🗑️</button>
                </div>
            </td>
        </tr>
    `).join('');
}

// ═══ ADD TRADE ═══════════════════════════════════════════════════

function selectType(type) {
    document.getElementById('tradeType').value = type;
    document.getElementById('btnBuy').classList.toggle('active', type === 'BUY');
    document.getElementById('btnSell').classList.toggle('active', type === 'SELL');
}

function setupFormPreview() {
    ['quantity', 'price'].forEach(id => {
        document.getElementById(id).addEventListener('input', updatePreview);
    });
}

function updatePreview() {
    const qty = parseFloat(document.getElementById('quantity').value) || 0;
    const price = parseFloat(document.getElementById('price').value) || 0;
    document.getElementById('previewValue').textContent = '₹' + fmt(qty * price);
}

async function addTrade() {
    const type = document.getElementById('tradeType').value;
    const instrument = document.getElementById('instrument').value;
    const quantity = parseFloat(document.getElementById('quantity').value);
    const price = parseFloat(document.getElementById('price').value);
    const stopLoss = parseFloat(document.getElementById('stopLossInput').value) || 0;
    const takeProfit = parseFloat(document.getElementById('takeProfitInput').value) || 0;

    if (!quantity || quantity <= 0 || !price || price <= 0) {
        toast('Please enter valid quantity and price.', 'error');
        return;
    }

    const btn = document.getElementById('btnAddTrade');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Executing...';

    try {
        const res = await fetch(`${API}/add`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type, instrument, quantity, price, stopLoss, takeProfit })
        });
        const data = await res.json();

        if (data.success) {
            toast(`${type} ${instrument} — ${quantity} units @ ₹${price}`, 'success');
            document.getElementById('quantity').value = '';
            document.getElementById('price').value = '';
            document.getElementById('stopLossInput').value = '';
            document.getElementById('takeProfitInput').value = '';
            document.getElementById('previewValue').textContent = '₹0.00';
            loadPortfolio();
        } else {
            toast(data.error || 'Trade failed.', 'error');
        }
    } catch (e) {
        toast('Network error. Is the server running?', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="btn-icon">⚡</span> Execute Trade';
    }
}

// ═══ UPDATE TRADE ════════════════════════════════════════════════

function openUpdateModal(id, qty, price) {
    document.getElementById('updateId').value = id;
    document.getElementById('updateQuantity').value = qty;
    document.getElementById('updatePrice').value = price;
    document.getElementById('updateModal').classList.add('active');
}

function closeUpdateModal() {
    document.getElementById('updateModal').classList.remove('active');
}

async function submitUpdate() {
    const id = parseInt(document.getElementById('updateId').value);
    const quantity = parseFloat(document.getElementById('updateQuantity').value);
    const price = parseFloat(document.getElementById('updatePrice').value);

    if (quantity <= 0 || price <= 0) {
        toast('Values must be positive.', 'error');
        return;
    }

    try {
        const res = await fetch(`${API}/update`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id, quantity, price })
        });
        const data = await res.json();
        if (data.success) {
            toast('Trade #' + id + ' updated.', 'success');
            closeUpdateModal();
            loadPortfolio();
        } else {
            toast(data.error || 'Update failed.', 'error');
        }
    } catch (e) {
        toast('Network error.', 'error');
    }
}

// ═══ DELETE TRADE ════════════════════════════════════════════════

async function deleteTrade(id) {
    if (!confirm('Delete trade #' + id + '?')) return;
    try {
        const res = await fetch(`${API}/delete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        });
        const data = await res.json();
        if (data.success) {
            toast('Trade #' + id + ' deleted.', 'success');
            loadPortfolio();
        } else {
            toast(data.error || 'Delete failed.', 'error');
        }
    } catch (e) {
        toast('Network error.', 'error');
    }
}

// ═══ MARKET SIMULATION & SSE ═════════════════════════════════════

function toggleSimulation() {
    if (simRunning) {
        stopSimulation();
    } else {
        startSimulation();
    }
}

async function startSimulation() {
    try {
        const res = await fetch(`${API}/simulation/start`, { method: 'POST' });
        const data = await res.json();
        if (data.success) {
            simRunning = true;
            updateSimButton();
            connectSSE();
            toast('Market simulation started', 'success');
            document.getElementById('marketStatus').textContent = 'Live';
            document.getElementById('marketStatus').className = 'badge badge-buy';
        }
    } catch (e) {
        toast('Failed to start simulation.', 'error');
    }
}

async function stopSimulation() {
    try {
        const res = await fetch(`${API}/simulation/stop`, { method: 'POST' });
        const data = await res.json();
        if (data.success) {
            simRunning = false;
            updateSimButton();
            if (eventSource) { eventSource.close(); eventSource = null; }
            toast('Simulation stopped.', 'info');
            document.getElementById('marketStatus').textContent = 'Stopped';
            document.getElementById('marketStatus').className = 'badge badge-info';
        }
    } catch (e) {
        toast('Failed to stop simulation.', 'error');
    }
}

function updateSimButton() {
    const btn = document.getElementById('btnSimToggle');
    const icon = document.getElementById('simIcon');
    const text = document.getElementById('simText');
    if (simRunning) {
        btn.classList.add('running');
        icon.textContent = '⏹';
        text.textContent = 'Stop Sim';
    } else {
        btn.classList.remove('running');
        icon.textContent = '▶';
        text.textContent = 'Start Sim';
    }
}

function connectSSE() {
    if (eventSource) eventSource.close();
    eventSource = new EventSource(`${API}/stream`);

    eventSource.addEventListener('price', (e) => {
        try {
            const data = JSON.parse(e.data);
            updateMarketPrices(data);
        } catch (err) {
            console.error('SSE price parse error:', err);
        }
    });

    eventSource.addEventListener('signal', (e) => {
        try {
            const signal = JSON.parse(e.data);
            addSignalToFeed(signal);
        } catch (err) {
            console.error('SSE signal parse error:', err);
        }
    });

    eventSource.addEventListener('message', (e) => {
        try {
            const data = JSON.parse(e.data);
            updateMarketPrices(data);
        } catch (err) {}
    });

    eventSource.onerror = () => {
        console.log('SSE connection lost. Retrying...');
    };
}

function updateMarketPrices(data) {
    const instruments = ['USD/INR', 'EUR/USD', 'GBP/USD', 'USD/JPY', 'GOLD', 'NIFTY 50'];

    instruments.forEach(inst => {
        const price = data[inst];
        if (price === undefined) return;

        const card = document.getElementById('market-' + inst);
        if (!card) return;

        const priceEl = card.querySelector('.market-price');
        const changeEl = card.querySelector('.market-change');

        const prev = prevPrices[inst] || price;
        const change = ((price - prev) / prev) * 100;
        const isUp = price >= prev;

        priceEl.textContent = price > 100 ? fmt(price) : price.toFixed(4);
        changeEl.textContent = (isUp ? '▲ ' : '▼ ') + Math.abs(change).toFixed(3) + '%';
        changeEl.className = 'market-change ' + (isUp ? 'up' : 'down');

        // Flash effect
        card.classList.remove('flash-up', 'flash-down');
        void card.offsetWidth; // trigger reflow
        card.classList.add(isUp ? 'flash-up' : 'flash-down');
        setTimeout(() => card.classList.remove('flash-up', 'flash-down'), 600);

        prevPrices[inst] = price;
    });
}

function addSignalToFeed(signal) {
    const feed = document.getElementById('signalFeed');
    const emptyState = feed.querySelector('.empty-state');
    if (emptyState) emptyState.remove();

    const badgeClass = signal.type === 'BUY' ? 'badge-buy' : signal.type === 'SELL' ? 'badge-sell' : 'badge-info';
    const time = signal.timestamp ? signal.timestamp.split(' ')[1] || signal.timestamp : '';

    const item = document.createElement('div');
    item.className = 'signal-item';
    item.innerHTML = `
        <span class="signal-time">${time}</span>
        <span class="badge ${badgeClass}">${signal.type}</span>
        <span>${signal.instrument}</span>
        <span style="font-variant-numeric:tabular-nums;font-weight:600">${parseFloat(signal.price).toFixed(4)}</span>
        <div class="signal-confidence"><div class="signal-confidence-fill" style="width:${(signal.confidence * 100)}%"></div></div>
        <span class="signal-reason">${signal.reason || signal.strategy}</span>
    `;

    feed.insertBefore(item, feed.firstChild);

    // Keep max 50 items
    while (feed.children.length > 50) feed.removeChild(feed.lastChild);
}

// ═══ STRATEGY ════════════════════════════════════════════════════

let selectedStrategy = 'MA';

function selectStrategy(name) {
    selectedStrategy = name;
    document.getElementById('stratOptMA').classList.toggle('selected', name === 'MA');
    document.getElementById('stratOptRSI').classList.toggle('selected', name === 'RSI');
    document.querySelector('#stratOptMA .strategy-badge').textContent = name === 'MA' ? 'Active' : 'Inactive';
    document.querySelector('#stratOptRSI .strategy-badge').textContent = name === 'RSI' ? 'Active' : 'Inactive';
}

async function applyStrategy() {
    const autoTrade = document.getElementById('autoTradeToggle').checked;
    const confidence = parseInt(document.getElementById('confidenceSlider').value) / 100;

    try {
        const res = await fetch(`${API}/strategy/config`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ strategy: selectedStrategy, autoTrade: autoTrade.toString(), confidence: confidence.toString() })
        });
        const data = await res.json();
        if (data.success) {
            toast('Strategy set to ' + data.activeStrategy, 'success');
        } else {
            toast(data.error || 'Failed to apply strategy.', 'error');
        }
    } catch (e) {
        toast('Network error.', 'error');
    }
}

async function runStrategy() {
    const btn = document.getElementById('btnRunStrategy');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Running...';

    try {
        const res = await fetch(`${API}/strategy`);
        const data = await res.json();

        const buyHold = data.buyAndHold || 0;
        const random = data.randomStrategy || 0;
        const momentum = data.momentumStrategy || 0;

        setResultValue('resultBuyHold', buyHold);
        setResultValue('resultRandom', random);
        setResultValue('resultMomentum', momentum);

        // Highlight winner
        document.querySelectorAll('.sim-card').forEach(c => c.classList.remove('winner'));
        if (data.bestStrategy) {
            const best = data.bestStrategy.toLowerCase();
            if (best.includes('buy')) document.getElementById('stratBuyHold').classList.add('winner');
            else if (best.includes('random')) document.getElementById('stratRandom').classList.add('winner');
            else if (best.includes('momentum')) document.getElementById('stratMomentum').classList.add('winner');
        }

        const verdict = document.getElementById('verdictCard');
        verdict.style.display = 'flex';
        document.getElementById('bestStrategyName').textContent = data.bestStrategy || '—';
        const bestProfit = data.bestProfit || 0;
        document.getElementById('bestStrategyProfit').textContent = (bestProfit >= 0 ? '+₹' : '-₹') + fmt(Math.abs(bestProfit));
        document.getElementById('bestStrategyProfit').className = 'verdict-profit ' + (bestProfit >= 0 ? 'text-green' : 'text-red');

        if (data.message) toast(data.message, 'info');
    } catch (e) {
        toast('Simulation failed. Is the server running?', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="btn-icon">🚀</span> Run Simulation';
    }
}

function setResultValue(elementId, value) {
    const el = document.getElementById(elementId);
    el.textContent = (value >= 0 ? '+₹' : '-₹') + fmt(Math.abs(value));
    el.className = 'sim-value ' + (value >= 0 ? 'text-green' : 'text-red');
}

async function loadSignals() {
    try {
        const res = await fetch(`${API}/signals`);
        const data = await res.json();
        if (data.signals && data.signals.length) {
            const feed = document.getElementById('signalFeed');
            feed.innerHTML = '';
            data.signals.forEach(s => addSignalToFeed(s));
        }
    } catch (e) {
        // Signals endpoint may not be available if server isn't running
    }
}

// ═══ BACKTEST ════════════════════════════════════════════════════

async function runBacktest() {
    const instrument = document.getElementById('backtestInstrument').value;
    const dataPoints = parseInt(document.getElementById('dataPointsSlider').value);

    const btn = document.getElementById('btnBacktest');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Running Backtest...';

    try {
        const res = await fetch(`${API}/backtest`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ instrument, dataPoints: dataPoints.toString() })
        });
        const data = await res.json();

        document.getElementById('backtestResults').style.display = 'block';

        const body = document.getElementById('backtestBody');
        body.innerHTML = data.results.map(r => {
            const plClass = r.totalProfitLoss >= 0 ? 'text-green' : 'text-red';
            const isBest = r.strategyName === data.bestStrategy;
            return `
                <tr ${isBest ? 'style="background:rgba(16,185,129,0.08)"' : ''}>
                    <td><strong>${r.strategyName}</strong> ${isBest ? '🏆' : ''}</td>
                    <td>${r.totalSignals}</td>
                    <td class="text-green">${r.buySignals}</td>
                    <td class="text-red">${r.sellSignals}</td>
                    <td class="${plClass}"><strong>${r.totalProfitLoss >= 0 ? '+' : ''}₹${fmt(r.totalProfitLoss)}</strong></td>
                    <td>${r.winRate.toFixed(1)}%</td>
                    <td class="text-red">${r.maxDrawdown.toFixed(2)}%</td>
                    <td>${r.sharpeRatio.toFixed(2)}</td>
                </tr>
            `;
        }).join('');

        const verdict = document.getElementById('backtestVerdict');
        verdict.style.display = 'flex';
        document.getElementById('backtestBestName').textContent = data.bestStrategy;
        const bestPL = data.bestProfitLoss || 0;
        document.getElementById('backtestBestPL').textContent = (bestPL >= 0 ? '+₹' : '-₹') + fmt(Math.abs(bestPL));
        document.getElementById('backtestBestPL').className = 'verdict-profit ' + (bestPL >= 0 ? 'text-green' : 'text-red');

        toast('Backtest complete for ' + instrument, 'success');
    } catch (e) {
        toast('Backtest failed. Check server.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="btn-icon">⏪</span> Run Backtest';
    }
}

// ═══ RISK MANAGEMENT ═════════════════════════════════════════════

async function loadRiskData() {
    try {
        const res = await fetch(`${API}/risk`);
        const data = await res.json();

        document.getElementById('riskSL').value = data.stopLossPercent || 5;
        document.getElementById('riskTP').value = data.takeProfitPercent || 10;
        document.getElementById('riskMD').value = data.maxDrawdownPercent || 20;
        document.getElementById('riskRPT').value = data.riskPerTradePercent || 2;

        const dd = parseFloat(data.currentDrawdown) || 0;
        document.getElementById('riskDrawdown').textContent = dd.toFixed(2) + '%';
        document.getElementById('drawdownFill').style.width = Math.min(100, (dd / (data.maxDrawdownPercent || 20)) * 100) + '%';
        document.getElementById('riskPeak').textContent = '₹' + fmt(data.peakPortfolioValue || 0);
        document.getElementById('riskSLCount').textContent = data.stopLossTriggered || 0;
        document.getElementById('riskTPCount').textContent = data.takeProfitTriggered || 0;
    } catch (e) {
        console.error('Risk data load failed:', e);
    }
}

async function updateRiskConfig() {
    const stopLoss = document.getElementById('riskSL').value;
    const takeProfit = document.getElementById('riskTP').value;
    const maxDrawdown = document.getElementById('riskMD').value;
    const riskPerTrade = document.getElementById('riskRPT').value;

    try {
        const res = await fetch(`${API}/risk/config`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stopLoss, takeProfit, maxDrawdown, riskPerTrade })
        });
        const data = await res.json();
        if (data.success) {
            toast('Risk parameters updated.', 'success');
            loadRiskData();
        } else {
            toast(data.error || 'Update failed.', 'error');
        }
    } catch (e) {
        toast('Network error.', 'error');
    }
}

// ═══ UPDATE MODAL ════════════════════════════════════════════════

document.getElementById('updateModal').addEventListener('click', (e) => {
    if (e.target === e.currentTarget) closeUpdateModal();
});

// ═══ UTILITIES ═══════════════════════════════════════════════════

function fmt(n) {
    return parseFloat(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatTime(timestamp) {
    if (!timestamp) return '—';
    const parts = timestamp.split(' ');
    return parts.length > 1 ? parts[1] : timestamp;
}

function toast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    const icons = { success: '✅', error: '❌', info: 'ℹ️' };
    toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'toastOut 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}