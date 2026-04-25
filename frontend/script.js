/**
 * Forex Portfolio Manager — Frontend Logic
 * Connects to Java backend via fetch API.
 */

const API_BASE = 'http://localhost:8080';

// ─── Initialize on Page Load ─────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    checkServerStatus();
    loadPortfolio();
    setupTradePreview();
});

// ─── Server Status Check ─────────────────────────────────────────
function checkServerStatus() {
    fetch(`${API_BASE}/status`)
        .then(res => res.json())
        .then(data => {
            const el = document.getElementById('serverStatus');
            el.querySelector('.status-dot').classList.add('online');
            el.querySelector('.status-text').textContent = 'Server Online';

            const dbEl = document.getElementById('dbStatus');
            if (data.database === 'connected') {
                dbEl.querySelector('.db-dot').classList.add('online');
                dbEl.querySelector('.db-text').textContent = 'DB: Connected';
            } else {
                dbEl.querySelector('.db-dot').classList.add('offline');
                dbEl.querySelector('.db-text').textContent = 'DB: File Mode';
            }
        })
        .catch(() => {
            const el = document.getElementById('serverStatus');
            el.querySelector('.status-dot').classList.add('offline');
            el.querySelector('.status-text').textContent = 'Server Offline';
        });
}

// ─── Trade Type Selector ─────────────────────────────────────────
function selectType(type) {
    document.getElementById('tradeType').value = type;
    const buyBtn = document.getElementById('btnBuy');
    const sellBtn = document.getElementById('btnSell');
    buyBtn.classList.toggle('active', type === 'BUY');
    sellBtn.classList.toggle('active', type === 'SELL');
}

// ─── Trade Value Preview ─────────────────────────────────────────
function setupTradePreview() {
    const qtyInput = document.getElementById('quantity');
    const priceInput = document.getElementById('price');
    const update = () => {
        const qty = parseFloat(qtyInput.value) || 0;
        const price = parseFloat(priceInput.value) || 0;
        document.getElementById('previewValue').textContent = '₹' + (qty * price).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    };
    qtyInput.addEventListener('input', update);
    priceInput.addEventListener('input', update);
}

// ─── Add Trade ───────────────────────────────────────────────────
function addTrade() {
    const type = document.getElementById('tradeType').value;
    const instrument = document.getElementById('instrument').value;
    const quantity = parseFloat(document.getElementById('quantity').value);
    const price = parseFloat(document.getElementById('price').value);

    // Client-side validation
    if (!quantity || quantity <= 0) { showToast('Please enter a valid quantity.', 'error'); return; }
    if (!price || price <= 0) { showToast('Please enter a valid price.', 'error'); return; }

    const btn = document.getElementById('btnAddTrade');
    btn.innerHTML = '<span class="spinner"></span> Processing...';
    btn.disabled = true;

    fetch(`${API_BASE}/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, instrument, quantity, price })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast(`${type} ${instrument} — ₹${(quantity * price).toFixed(2)} added!`, 'success');
            document.getElementById('quantity').value = '';
            document.getElementById('price').value = '';
            document.getElementById('previewValue').textContent = '₹0.00';
            loadPortfolio();
        } else {
            showToast(data.error || 'Failed to add trade.', 'error');
        }
    })
    .catch(err => showToast('Server error: ' + err.message, 'error'))
    .finally(() => {
        btn.innerHTML = '<span class="btn-icon">⚡</span> Execute Trade';
        btn.disabled = false;
    });
}

// ─── Load Portfolio ──────────────────────────────────────────────
function loadPortfolio() {
    fetch(`${API_BASE}/portfolio`)
        .then(res => res.json())
        .then(data => {
            renderTrades(data.trades || []);
            updateStats(data.summary || {});
        })
        .catch(() => {
            document.getElementById('tradesBody').innerHTML =
                '<tr class="empty-row"><td colspan="8"><div class="empty-state"><span class="empty-icon">⚠️</span><p>Could not connect to server.</p></div></td></tr>';
        });
}

// ─── Render Trade Table ──────────────────────────────────────────
function renderTrades(trades) {
    const tbody = document.getElementById('tradesBody');
    if (!trades || trades.length === 0) {
        tbody.innerHTML = '<tr class="empty-row"><td colspan="8"><div class="empty-state"><span class="empty-icon">📭</span><p>No trades yet. Add your first trade!</p></div></td></tr>';
        return;
    }

    tbody.innerHTML = trades.map(t => {
        const value = (t.quantity * t.price).toFixed(2);
        const badgeClass = t.type === 'BUY' ? 'badge-buy' : 'badge-sell';
        return `<tr>
            <td class="value-cell">#${t.id}</td>
            <td><span class="badge ${badgeClass}">${t.type}</span></td>
            <td>${t.instrument}</td>
            <td class="value-cell">${t.quantity}</td>
            <td class="value-cell">₹${Number(t.price).toLocaleString('en-IN')}</td>
            <td class="value-cell">₹${Number(value).toLocaleString('en-IN')}</td>
            <td style="font-size:0.75rem;color:var(--text-muted)">${t.timestamp || '—'}</td>
            <td><div class="actions-cell">
                <button class="btn btn-edit btn-sm" onclick="openUpdateModal(${t.id}, ${t.quantity}, ${t.price})">✏️</button>
                <button class="btn btn-danger btn-sm" onclick="deleteTrade(${t.id})">🗑️</button>
            </div></td>
        </tr>`;
    }).join('');
}

// ─── Update Stats Bar ────────────────────────────────────────────
function updateStats(summary) {
    document.getElementById('statTrades').textContent = summary.tradeCount || 0;
    const invested = parseFloat(summary.totalInvested) || 0;
    const revenue = parseFloat(summary.totalRevenue) || 0;
    const pl = parseFloat(summary.profitLoss) || 0;

    document.getElementById('statInvested').textContent = '₹' + invested.toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('statRevenue').textContent = '₹' + revenue.toLocaleString('en-IN', { minimumFractionDigits: 2 });

    const plEl = document.getElementById('statPL');
    plEl.textContent = (pl >= 0 ? '+₹' : '-₹') + Math.abs(pl).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    plEl.className = 'stat-value ' + (pl >= 0 ? 'profit' : 'loss');
    document.getElementById('plIcon').textContent = pl >= 0 ? '📈' : '📉';
}

// ─── Delete Trade ────────────────────────────────────────────────
function deleteTrade(id) {
    if (!confirm(`Delete trade #${id}? This cannot be undone.`)) return;

    fetch(`${API_BASE}/delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast(`Trade #${id} deleted.`, 'success');
            loadPortfolio();
        } else {
            showToast(data.error || 'Delete failed.', 'error');
        }
    })
    .catch(err => showToast('Error: ' + err.message, 'error'));
}

// ─── Update Modal ────────────────────────────────────────────────
function openUpdateModal(id, quantity, price) {
    document.getElementById('updateId').value = id;
    document.getElementById('updateQuantity').value = quantity;
    document.getElementById('updatePrice').value = price;
    document.getElementById('updateModal').classList.add('active');
}

function closeUpdateModal() {
    document.getElementById('updateModal').classList.remove('active');
}

function submitUpdate() {
    const id = parseInt(document.getElementById('updateId').value);
    const quantity = parseFloat(document.getElementById('updateQuantity').value);
    const price = parseFloat(document.getElementById('updatePrice').value);

    if (!quantity || quantity <= 0 || !price || price <= 0) {
        showToast('Enter valid quantity and price.', 'error');
        return;
    }

    fetch(`${API_BASE}/update`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, quantity, price })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showToast(`Trade #${id} updated.`, 'success');
            closeUpdateModal();
            loadPortfolio();
        } else {
            showToast(data.error || 'Update failed.', 'error');
        }
    })
    .catch(err => showToast('Error: ' + err.message, 'error'));
}

// ─── Run Strategy Simulation ─────────────────────────────────────
function runStrategy() {
    const btn = document.getElementById('btnRunStrategy');
    btn.innerHTML = '<span class="spinner"></span> Simulating...';
    btn.disabled = true;

    fetch(`${API_BASE}/strategy`)
        .then(res => res.json())
        .then(data => {
            // Update result values
            setResult('resultBuyHold', data.buyAndHold);
            setResult('resultRandom', data.randomStrategy);
            setResult('resultMomentum', data.momentumStrategy);

            // Highlight winner
            document.querySelectorAll('.strategy-card').forEach(c => c.classList.remove('winner'));
            if (data.bestStrategy === 'Buy & Hold') document.getElementById('stratBuyHold').classList.add('winner');
            else if (data.bestStrategy === 'Random Strategy') document.getElementById('stratRandom').classList.add('winner');
            else if (data.bestStrategy === 'Momentum Strategy') document.getElementById('stratMomentum').classList.add('winner');

            // Show verdict
            document.getElementById('bestStrategyName').textContent = data.bestStrategy || 'N/A';
            document.getElementById('bestStrategyProfit').textContent = '₹' + Number(data.bestProfit || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
            document.getElementById('verdictCard').style.display = 'block';

            if (data.message) showToast(data.message, 'info');
        })
        .catch(err => showToast('Strategy error: ' + err.message, 'error'))
        .finally(() => {
            btn.innerHTML = '<span class="btn-icon">🚀</span> Run Simulation';
            btn.disabled = false;
        });
}

function setResult(elementId, value) {
    const el = document.getElementById(elementId);
    const num = parseFloat(value) || 0;
    el.textContent = (num >= 0 ? '+₹' : '-₹') + Math.abs(num).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    el.className = 'result-value ' + (num >= 0 ? 'positive' : 'negative');
}

// ─── Toast Notifications ─────────────────────────────────────────
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const icons = { success: '✅', error: '❌', info: 'ℹ️' };
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.animation = 'toastOut 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}