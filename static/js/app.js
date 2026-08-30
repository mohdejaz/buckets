/* ============================================================
   Buckets – Budget Manager  |  Frontend Application
   ============================================================ */

'use strict';

// ---- Utilities ----
const $ = id => document.getElementById(id);
const fmt = (n, forceSign = false) => {
  const v = parseFloat(n) || 0;
  const s = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Math.abs(v));
  if (forceSign) return (v < 0 ? '-' : v > 0 ? '+' : '') + s;
  return (v < 0 ? '-' : '') + s;
};
const amtClass = v => v > 0 ? 'amount-pos' : v < 0 ? 'amount-neg' : 'amount-zero';
const balClass = v => v > 0 ? 'balance-pos' : v < 0 ? 'balance-neg' : '';
const today = () => new Date().toISOString().slice(0, 10);

async function api(method, url, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const r = await fetch(url, opts);
  if (r.status === 401) {
    window.location.href = '/login';
    throw new Error('Session expired');
  }
  if (!r.ok) {
    const err = await r.json().catch(() => ({ error: 'Request failed' }));
    throw new Error(err.error || 'Request failed');
  }
  return r.json();
}

function toast(msg, type = 'success') {
  const el = $('appToast');
  el.className = `toast align-items-center text-bg-${type} border-0`;
  $('toastMessage').textContent = msg;
  new bootstrap.Toast(el, { delay: 2800 }).show();
}

function showError(msg) { toast(msg, 'danger'); }

// ---- Pagination builder ----
function buildPagination(containerId, currentPage, totalPages, onPage) {
  const c = $(containerId);
  if (!c) return;
  if (totalPages <= 1) { c.innerHTML = ''; return; }
  const max = 7;
  let pages = [];
  if (totalPages <= max) {
    pages = Array.from({ length: totalPages }, (_, i) => i + 1);
  } else {
    pages = [1];
    const start = Math.max(2, currentPage - 2);
    const end = Math.min(totalPages - 1, currentPage + 2);
    if (start > 2) pages.push('…');
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 1) pages.push('…');
    pages.push(totalPages);
  }

  const ul = document.createElement('ul');
  ul.className = 'pagination mb-0';

  const mkLi = (label, page, disabled, active) => {
    const li = document.createElement('li');
    li.className = 'page-item' + (disabled ? ' disabled' : '') + (active ? ' active' : '');
    const a = document.createElement('a');
    a.className = 'page-link';
    a.href = '#';
    a.innerHTML = label;
    if (!disabled && !active) a.addEventListener('click', e => { e.preventDefault(); onPage(page); });
    li.appendChild(a);
    ul.appendChild(li);
  };

  mkLi('<i class="bi bi-chevron-left"></i>', currentPage - 1, currentPage === 1, false);
  pages.forEach(p => {
    if (p === '…') mkLi('…', null, true, false);
    else mkLi(p, p, false, p === currentPage);
  });
  mkLi('<i class="bi bi-chevron-right"></i>', currentPage + 1, currentPage === totalPages, false);

  c.innerHTML = '';
  c.appendChild(ul);
}

// ---- Confirm dialog ----
function confirm(msg, label = 'Delete', danger = true) {
  return new Promise(resolve => {
    $('confirmMessage').textContent = msg;
    const btn = $('btnConfirmOk');
    btn.textContent = label;
    btn.className = `btn btn-sm ${danger ? 'btn-danger' : 'btn-warning'}`;
    const modal = new bootstrap.Modal($('confirmModal'));
    const onOk = () => { cleanup(); modal.hide(); resolve(true); };
    const onDismiss = () => { cleanup(); resolve(false); };
    const cleanup = () => {
      btn.removeEventListener('click', onOk);
      $('confirmModal').removeEventListener('hidden.bs.modal', onDismiss);
    };
    btn.addEventListener('click', onOk);
    $('confirmModal').addEventListener('hidden.bs.modal', onDismiss, { once: true });
    modal.show();
  });
}

// ============================================================
// App State
// ============================================================
const State = {
  currentSection: 'dashboard',
  accountId: null,
  accounts: [],
  buckets: [],
  archivedBuckets: [],
  lastTxDate: null,
  accountPage: 1,
  accountsPerPage: 10,
  currentBucketId: null,
  settlementBucketId: null,
};

// ============================================================
// ACCOUNTS
// ============================================================
const Accounts = {
  modal: null,
  transferModal: null,

  init() {
    this.modal = new bootstrap.Modal($('accountModal'));
    this.transferModal = new bootstrap.Modal($('accountTransferModal'));
    $('btnNewAccount').addEventListener('click', () => this.openNew());
    $('btnSaveAccount').addEventListener('click', () => this.save());
    $('btnAccountTransfer').addEventListener('click', () => this.openTransfer());
    $('btnSaveAccountTransfer').addEventListener('click', () => this.saveTransfer());
  },

  async load() {
    try {
      State.accounts = await api('GET', '/api/accounts');
      this.render();
      this.populateSelector();
    } catch (e) { showError(e.message); }
  },

  render() {
    const body = $('accountsBody');
    const data = State.accounts;
    const page = State.accountPage;
    const per = State.accountsPerPage;
    const slice = data.slice((page - 1) * per, page * per);

    if (!slice.length) {
      body.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-4">
        <i class="bi bi-bank2 fs-2 d-block mb-2 opacity-25"></i>No accounts yet.</td></tr>`;
    } else {
      body.innerHTML = slice.map((a, i) => `
        <tr>
          <td class="text-muted">${(page - 1) * per + i + 1}</td>
          <td><strong>${esc(a.name)}</strong></td>
          <td class="text-end font-monospace ${balClass(a.balance)}">${fmt(a.balance)}</td>
          <td class="text-center">
            <button class="action-btn action-btn-select me-1" onclick="Accounts.select(${a.id})">
              <i class="bi bi-check2-circle me-1"></i>Select
            </button>
            <button class="action-btn action-btn-edit me-1" onclick="Accounts.openEdit(${a.id})">
              <i class="bi bi-pencil me-1"></i>Edit
            </button>
            <button class="action-btn action-btn-delete" onclick="Accounts.delete(${a.id})">
              <i class="bi bi-trash me-1"></i>Delete
            </button>
          </td>
        </tr>`).join('');
    }

    buildPagination('accountsPagination', page, Math.max(1, Math.ceil(data.length / per)), p => {
      State.accountPage = p;
      this.render();
    });
  },

  populateSelector() {
    const sel = $('accountSelect');
    const current = State.accountId;
    sel.innerHTML = '<option value="">— Select account —</option>' +
      State.accounts.map(a => `<option value="${a.id}" ${a.id === current ? 'selected' : ''}>${esc(a.name)}</option>`).join('');
  },

  select(id) {
    State.accountId = id;
    const a = State.accounts.find(x => x.id === id);
    $('accountSelect').value = id;
    toast(`Account "${a?.name}" selected`);
    App.nav('dashboard');
  },

  openTransfer() {
    if (State.accounts.length < 2) {
      showError('You need at least two accounts to transfer between them.');
      return;
    }
    const opts = State.accounts.map(a =>
      `<option value="${a.id}">${esc(a.name)}</option>`).join('');
    $('acctTransferFrom').innerHTML = opts;
    $('acctTransferTo').innerHTML = opts;
    // Default: current account as source, a different one as destination.
    if (State.accountId) $('acctTransferFrom').value = State.accountId;
    const to = State.accounts.find(a => a.id !== parseInt($('acctTransferFrom').value));
    if (to) $('acctTransferTo').value = to.id;
    $('acctTransferAmount').value = '';
    $('acctTransferNote').value = '';
    $('acctTransferDate').value = new Date().toLocaleDateString('en-CA');
    $('accountTransferForm').classList.remove('was-validated');
    this.transferModal.show();
  },

  async saveTransfer() {
    const form = $('accountTransferForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;
    const payload = {
      from_acct_id: parseInt($('acctTransferFrom').value),
      to_acct_id: parseInt($('acctTransferTo').value),
      amount: parseFloat($('acctTransferAmount').value),
      tx_date: $('acctTransferDate').value,
      note: $('acctTransferNote').value.trim(),
    };
    if (payload.from_acct_id === payload.to_acct_id) {
      showError('Choose two different accounts.');
      return;
    }
    try {
      await api('POST', '/api/account-transfers', payload);
      toast('Transfer complete');
      this.transferModal.hide();
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
      if (State.currentSection === 'buckets') Buckets.load();
    } catch (e) { showError(e.message); }
  },

  openNew() {
    $('accountModalTitle').textContent = 'New Account';
    $('accountId').value = '';
    $('accountName').value = '';
    $('accountForm').classList.remove('was-validated');
    this.modal.show();
  },

  openEdit(id) {
    const a = State.accounts.find(x => x.id === id);
    if (!a) return;
    $('accountModalTitle').textContent = 'Edit Account';
    $('accountId').value = id;
    $('accountName').value = a.name;
    $('accountForm').classList.remove('was-validated');
    this.modal.show();
  },

  async save() {
    const form = $('accountForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const id = $('accountId').value;
    const name = $('accountName').value.trim();
    try {
      if (id) {
        await api('PUT', `/api/accounts/${id}`, { name });
        toast('Account updated');
      } else {
        await api('POST', '/api/accounts', { name });
        toast('Account created');
      }
      this.modal.hide();
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async delete(id) {
    const a = State.accounts.find(x => x.id === id);
    const ok = await confirm(`Delete account "${a?.name}"? This cannot be undone.`);
    if (!ok) return;
    try {
      await api('DELETE', `/api/accounts/${id}`);
      if (State.accountId === id) { State.accountId = null; $('accountSelect').value = ''; }
      toast('Account deleted');
      await this.load();
    } catch (e) { showError(e.message); }
  },
};

// ============================================================
// BUCKETS
// ============================================================
const Buckets = {
  modal: null,
  transferModal: null,

  init() {
    this.modal = new bootstrap.Modal($('bucketModal'));
    this.transferModal = new bootstrap.Modal($('transferModal'));
    $('btnNewBucket').addEventListener('click', () => this.openNew());
    $('btnSaveBucket').addEventListener('click', () => this.save());
    $('btnTransfer').addEventListener('click', () => this.openTransfer());
    $('btnSaveTransfer').addEventListener('click', () => this.saveTransfer());
    $('btnRefillAll').addEventListener('click', () => this.refillAll());
    $('btnResetBuckets').addEventListener('click', () => this.resetAll());
    $('btnDepositSettlement').addEventListener('click', () => {
      if (State.settlementBucketId) Transactions.openNew(State.settlementBucketId);
    });
    $('toggleArchived').addEventListener('change', () => this.renderArchived());
  },

  async load() {
    if (!State.accountId) {
      State.buckets = [];
      State.archivedBuckets = [];
      this.render();
      this.renderArchived();
      return;
    }
    try {
      const all = await api('GET', `/api/buckets?acct_id=${State.accountId}&include_archived=1`);
      State.buckets = all.filter(b => !b.archived);
      State.archivedBuckets = all.filter(b => b.archived);
      const settlement = State.buckets.find(b => b.is_settlement);
      State.settlementBucketId = settlement ? settlement.id : null;
      window._settlementBucketId = State.settlementBucketId;
      this.render();
      this.renderArchived();
      this.populateBucketSelects();
    } catch (e) { showError(e.message); }
  },

  render() {
    const noAcct = $('bucketsNoAccount');
    const card = $('bucketsCard');
    const body = $('bucketsBody');
    const banner = $('settlementBanner');

    if (!State.accountId) {
      noAcct.classList.remove('d-none');
      card.classList.add('d-none');
      banner.classList.add('d-none');
      const txNetElEmpty = $('txNetBalance');
      if (txNetElEmpty) txNetElEmpty.textContent = fmt(0);
      return;
    }
    noAcct.classList.add('d-none');
    card.classList.remove('d-none');

    // Update settlement banner
    const settlement = State.buckets.find(b => b.is_settlement);
    if (settlement) {
      banner.classList.remove('d-none');
      const balEl = $('settlementBalance');
      balEl.textContent = fmt(settlement.balance);
      balEl.className = `fw-bold fs-5 font-monospace ${balClass(settlement.balance)}`;

      const totalVal = State.buckets.filter(b => !b.is_settlement)
        .reduce((sum, b) => sum + parseFloat(b.balance), 0);
      const totalEl = $('totalBucketBalance');
      totalEl.textContent = fmt(totalVal);
      totalEl.className = `fw-bold fs-5 font-monospace ${balClass(totalVal)}`;

      const netVal = State.buckets.reduce((sum, b) => sum + parseFloat(b.balance), 0);
      const netEl = $('netBalance');
      netEl.textContent = fmt(netVal);
      netEl.className = `fw-bold fs-5 font-monospace ${balClass(netVal)}`;

      const txNetEl = $('txNetBalance');
      if (txNetEl) {
        txNetEl.textContent = fmt(netVal);
        txNetEl.className = `fw-bold fs-5 font-monospace ${balClass(netVal)}`;
      }
    } else {
      banner.classList.add('d-none');
      const txNetEl = $('txNetBalance');
      if (txNetEl) txNetEl.textContent = fmt(0);
    }

    // Regular (non-settlement) buckets in the table
    this._allRows = State.buckets.filter(b => !b.is_settlement);
    this._shown = 0;
    body.innerHTML = '';

    if (!this._allRows.length) {
      body.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">
        <i class="bi bi-bucket fs-2 d-block mb-2 opacity-25"></i>No buckets yet.</td></tr>`;
      $('bucketsMoreWrap').classList.add('d-none');
      $('bucketsInfo').textContent = '';
      return;
    }

    this._appendRows();
  },

  _PAGE: 25,
  _allRows: [],
  _shown: 0,
  _target: parseInt(sessionStorage.getItem('bucketsShown')) || 25,

  _appendRows() {
    const body = $('bucketsBody');
    const limit = Math.min(this._target, this._allRows.length);
    const next = this._allRows.slice(this._shown, limit);
    const startIdx = this._shown;
    this._shown = limit;

    body.insertAdjacentHTML('beforeend', next.map((b, i) => {
      const overdrawn = b.balance < 0;
      return `
      <tr class="${overdrawn ? 'bucket-overdrawn' : ''}">
        <td class="text-muted">${startIdx + i + 1}</td>
        <td>
          <a href="#" class="text-decoration-none" onclick="BucketDetail.open(${b.id}); return false;"><strong>${esc(b.name)}</strong></a>
          ${overdrawn ? '<span class="badge-overdraft"><i class="bi bi-exclamation-triangle-fill me-1"></i>Overdraft</span>' : ''}
        </td>
        <td class="text-end font-monospace">${fmt(b.budget)}</td>
        <td class="text-end font-monospace ${balClass(b.balance)}">${fmt(b.balance)}</td>
        <td class="text-end font-monospace text-success">${fmt(b.refill_mtd)}</td>
        <td class="text-end">${b.refill_factor}×</td>
        <td class="text-center">
          <button class="action-btn action-btn-refill me-1" onclick="Buckets.refill(${b.id})" title="Refill from Settlement">
            <i class="bi bi-plus-circle"></i>
          </button>
          <button class="action-btn action-btn-edit me-1" onclick="Buckets.openEdit(${b.id})" title="Edit">
            <i class="bi bi-pencil"></i>
          </button>
          <button class="action-btn action-btn-edit me-1" onclick="Buckets.archive(${b.id})" title="Archive">
            <i class="bi bi-archive"></i>
          </button>
          <button class="action-btn action-btn-delete" onclick="Buckets.delete(${b.id})" title="Delete">
            <i class="bi bi-trash"></i>
          </button>
        </td>
      </tr>`;
    }).join(''));

    const total = this._allRows.length;
    $('bucketsInfo').textContent = `Showing ${this._shown} of ${total}`;
    const moreWrap = $('bucketsMoreWrap');
    if (this._shown < total) {
      moreWrap.classList.remove('d-none');
    } else {
      moreWrap.classList.add('d-none');
    }
  },

  showMore() {
    this._target += this._PAGE;
    sessionStorage.setItem('bucketsShown', this._target);
    this._appendRows();
  },

  populateBucketSelects() {
    const settlement = State.buckets.find(b => b.is_settlement);
    const regular = State.buckets.filter(b => !b.is_settlement);
    const allOpts = State.buckets.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
    // For tx bucket: Settlement first (easy deposit entry), then regular buckets
    const txOpts = '<option value="">— Select bucket —</option>' +
      (settlement ? `<option value="${settlement.id}">Settlement</option>` : '') +
      regular.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');

    const txBucket = $('txBucket');
    if (txBucket) { const prev = txBucket.value; txBucket.innerHTML = txOpts; if (prev) txBucket.value = prev; }

    ['transferFrom', 'transferTo'].forEach(id => {
      const el = $(id);
      if (el) { const prev = el.value; el.innerHTML = allOpts; if (prev) el.value = prev; }
    });
  },

  openNew() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    $('bucketModalTitle').textContent = 'New Bucket';
    $('bucketId').value = '';
    $('bucketName').value = '';
    $('bucketBudget').value = '0';
    $('bucketRefillFactor').value = '1.0';
    $('bucketForm').classList.remove('was-validated');
    this.modal.show();
  },

  openEdit(id) {
    const b = State.buckets.find(x => x.id === id);
    if (!b) return;
    $('bucketModalTitle').textContent = 'Edit Bucket';
    $('bucketId').value = id;
    $('bucketName').value = b.name;
    $('bucketBudget').value = b.budget;
    $('bucketRefillFactor').value = b.refill_factor;
    $('bucketForm').classList.remove('was-validated');
    this.modal.show();
  },

  async save() {
    const form = $('bucketForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const id = $('bucketId').value;
    const payload = {
      name: $('bucketName').value.trim(),
      budget: parseFloat($('bucketBudget').value) || 0,
      refill_factor: parseFloat($('bucketRefillFactor').value) || 1.0,
      acct_id: State.accountId,
    };
    try {
      if (id) {
        await api('PUT', `/api/buckets/${id}`, payload);
        toast('Bucket updated');
      } else {
        await api('POST', '/api/buckets', payload);
        toast('Bucket created');
      }
      this.modal.hide();
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async refill(id) {
    const b = State.buckets.find(x => x.id === id);
    const ok = await confirm(`Transfer ${fmt(b?.budget * b?.refill_factor)} from Settlement to "${b?.name}"?`, 'Refill', false);
    if (!ok) return;
    try {
      const res = await api('POST', `/api/buckets/${id}/refill`);
      toast(`Refilled ${fmt(res.amount)}`);
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async delete(id) {
    const b = State.buckets.find(x => x.id === id);
    const ok = await confirm(`Delete bucket "${b?.name}"?`);
    if (!ok) return;
    try {
      await api('DELETE', `/api/buckets/${id}`);
      toast('Bucket deleted');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async archive(id) {
    const b = State.buckets.find(x => x.id === id);
    const ok = await confirm(`Archive bucket "${b?.name}"? It will be hidden and frozen — no refills, transfers, or transactions.`, 'Archive', false);
    if (!ok) return;
    try {
      await api('POST', `/api/buckets/${id}/archive`);
      toast('Bucket archived');
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async unarchive(id) {
    const b = State.archivedBuckets.find(x => x.id === id);
    const ok = await confirm(`Unarchive bucket "${b?.name}"?`, 'Unarchive', false);
    if (!ok) return;
    try {
      await api('POST', `/api/buckets/${id}/unarchive`);
      toast('Bucket restored');
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  renderArchived() {
    const card = $('archivedBucketsCard');
    const body = $('archivedBucketsBody');
    const show = $('toggleArchived').checked;
    const rows = (State.archivedBuckets || []).filter(b => !b.is_settlement);
    if (!show || !rows.length) {
      card.classList.add('d-none');
      body.innerHTML = '';
      return;
    }
    card.classList.remove('d-none');
    body.innerHTML = rows.map(b => `
      <tr class="text-muted">
        <td>${esc(b.name)}</td>
        <td class="text-end font-monospace">${fmt(b.budget)}</td>
        <td class="text-end font-monospace ${balClass(b.balance)}">${fmt(b.balance)}</td>
        <td class="text-center">
          <button class="action-btn action-btn-edit" onclick="Buckets.unarchive(${b.id})" title="Unarchive">
            <i class="bi bi-arrow-counterclockwise"></i>
          </button>
        </td>
      </tr>`).join('');
  },

  openTransfer(fromId = null, toId = null) {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    this.populateBucketSelects();
    $('transferAmount').value = '';
    $('transferNote').value = '';
    $('transferDate').value = new Date().toLocaleDateString('en-CA');
    $('transferForm').classList.remove('was-validated');
    if (fromId) {
      $('transferFrom').value = fromId;
      $('transferFrom').disabled = true;
    } else {
      $('transferFrom').disabled = false;
    }
    if (toId) $('transferTo').value = toId;
    this.transferModal.show();
  },

  async saveTransfer() {
    const form = $('transferForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;
    const payload = {
      from_bucket_id: parseInt($('transferFrom').value),
      to_bucket_id: parseInt($('transferTo').value),
      amount: parseFloat($('transferAmount').value),
      tx_date: $('transferDate').value,
      note: $('transferNote').value.trim(),
    };
    const fromBucket = State.buckets.find(b => b.id === payload.from_bucket_id);
    if (fromBucket && payload.amount > fromBucket.balance) {
      const ok = await confirm(
        `This transfer of ${fmt(payload.amount)} exceeds "${fromBucket.name}" balance of ${fmt(fromBucket.balance)}. Proceed anyway?`,
        'Proceed', false
      );
      if (!ok) return;
    }
    try {
      await api('POST', '/api/transfers', payload);
      toast('Transfer complete');
      this.transferModal.hide();
      await this.load();
      if (State.currentSection === 'bucket-detail') await BucketDetail.load(State.currentBucketId);
      if (State.currentSection === 'transactions') await Transactions.load();
    } catch (e) { showError(e.message); }
  },

  async refillAll() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    const ok = await confirm('Transfer from Settlement into all buckets based on their budget and refill factor?', 'Refill All', false);
    if (!ok) return;
    try {
      const res = await api('POST', '/api/buckets/refill-all', { acct_id: State.accountId });
      toast(`Refilled ${res.count} bucket(s)`);
      await this.load();
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async resetAll() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    const ok = await confirm('Sweep all bucket balances back into Settlement? This zeroes every bucket and deposits the total into Settlement.', 'Reset', false);
    if (!ok) return;
    try {
      await api('POST', '/api/buckets/reset', { acct_id: State.accountId });
      toast('Balances swept into Settlement');
      await this.load();
    } catch (e) { showError(e.message); }
  },
};

// ============================================================
// TRANSACTIONS
// ============================================================
const Transactions = {
  modal: null,

  _mode: null,

  _applyAmountConstraint(bucketId, isTransfer = false) {
    const inp = $('txAmount');
    const hint = $('txAmountHint');
    const isSettlement = State.buckets.find(b => b.id === bucketId)?.is_settlement;
    inp.removeAttribute('min');
    inp.removeAttribute('max');
    if (this._mode === 'deposit') {
      inp.setAttribute('min', '0.01');
      if (hint) hint.textContent = 'Deposit only — must be positive (e.g. 25.00)';
    } else if (!isSettlement && !isTransfer) {
      inp.setAttribute('max', '-0.01');
      if (hint) hint.textContent = 'Expenses only — must be negative (e.g. -25.00)';
    } else {
      if (hint) hint.textContent = 'Use negative for expenses (e.g. -25.00)';
    }
  },

  init() {
    this.modal = new bootstrap.Modal($('txModal'));
    $('btnSaveTx').addEventListener('click', () => this.save());
    $('btnTxPageNewTx').addEventListener('click', () => this.openNew());
    $('btnExportTx').addEventListener('click', () => this.exportCsv());
    $('txBucket').addEventListener('change', () => {
      const bid = parseInt($('txBucket').value);
      this._applyAmountConstraint(bid);
    });
  },

  _PAGE: 20,
  _target: parseInt(sessionStorage.getItem('txShown')) || 20,

  async load() {
    const noAcct = $('txNoAccount');
    const card = $('txCard');
    if (!State.accountId) {
      noAcct.classList.remove('d-none');
      card.classList.add('d-none');
      return;
    }
    noAcct.classList.add('d-none');
    card.classList.remove('d-none');

    const params = new URLSearchParams({
      acct_id: State.accountId,
      page: 1,
      per_page: this._target,
    });
    try {
      const data = await api('GET', `/api/transactions?${params}`);
      this.render(data);
    } catch (e) { showError(e.message); }
  },

  render(data) {
    const body = $('txBody');
    const rows = data.transactions || [];

    if (!rows.length) {
      body.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-4">
        <i class="bi bi-receipt fs-2 d-block mb-2 opacity-25"></i>No transactions found.</td></tr>`;
    } else {
      const html = rows.map(t => {
        const posted = t.posted
          ? `<span class="badge-posted"><i class="bi bi-check-circle-fill me-1"></i>Posted</span>`
          : `<span class="badge-unposted"><i class="bi bi-circle me-1"></i>Pending</span>`;
        const postBtn = t.posted
          ? `<button class="action-btn action-btn-post" onclick="Transactions.unpost(${t.id})" title="Unpost"><i class="bi bi-x-circle"></i></button>`
          : `<button class="action-btn action-btn-post" onclick="Transactions.post(${t.id})" title="Post"><i class="bi bi-check-circle"></i></button>`;
        return `
        <tr class="${t.posted ? '' : 'table-row-pending'}">
          <td class="text-muted font-monospace" style="font-size:.82rem">${t.tx_date}</td>
          <td style="white-space:nowrap">
            <span class="badge bg-light text-secondary border" style="font-size:.75rem">${esc(t.bucket)}</span>${t.linked_tx_id ? ' <span class="badge bg-secondary" style="font-size:.7rem">Transfer</span>' : ''}
          </td>
          <td class="text-muted small text-truncate" style="max-width:240px" title="${esc(t.note)}">${esc(t.note)}</td>
          <td class="text-end font-monospace ${amtClass(t.amount)}">${fmt(t.amount, true)}</td>
          <td class="text-end font-monospace ${balClass(t.balance)}">${fmt(t.balance)}</td>
          <td class="text-center">${posted}</td>
          <td class="text-center" style="white-space:nowrap">
            ${postBtn}
            <button class="action-btn action-btn-edit mx-1" onclick="Transactions.openEdit(${t.id})" title="Edit"><i class="bi bi-pencil"></i></button>
            <button class="action-btn action-btn-delete" onclick="Transactions.delete(${t.id}, ${t.linked_tx_id ?? 'null'})" title="Delete"><i class="bi bi-trash"></i></button>
          </td>
        </tr>`;
      }).join('');
      body.innerHTML = html;
    }

    const shown = Math.min(this._target, data.total);
    $('txInfo').textContent = data.total ? `Showing ${shown} of ${data.total}` : 'No transactions';

    const moreWrap = $('txMoreWrap');
    if (shown < data.total) {
      moreWrap.classList.remove('d-none');
    } else {
      moreWrap.classList.add('d-none');
    }
  },

  loadMore() {
    this._target += this._PAGE;
    sessionStorage.setItem('txShown', this._target);
    this.load();
  },

  openNew(bucketId = null, mode = null) {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    this._mode = mode;
    const isSettlement = State.buckets.find(b => b.id === bucketId)?.is_settlement;
    $('txModalTitle').textContent = mode === 'deposit'
      ? 'Deposit'
      : (isSettlement ? 'New Transaction' : 'Withdraw');
    $('txId').value = '';
    $('txDate').value = State.lastTxDate || today();
    $('txBucket').value = '';
    $('txAmount').value = '';
    $('txNote').value = '';
    $('txForm').classList.remove('was-validated');
    Buckets.populateBucketSelects();
    if (bucketId) {
      $('txBucket').value = bucketId;
      $('txBucket').disabled = true;
    } else {
      $('txBucket').disabled = false;
    }
    this._applyAmountConstraint(bucketId ? parseInt(bucketId) : null);
    this.modal.show();
  },

  openEdit(id) {
    fetch(`/api/transactions?acct_id=${State.accountId}&page=1&per_page=9999`)
      .then(r => r.json())
      .then(data => {
        const t = data.transactions.find(x => x.id === id);
        if (!t) return;
        this._mode = (t.amount > 0 && !t.linked_tx_id) ? 'deposit' : null;
        $('txModalTitle').textContent = 'Edit Transaction';
        $('txId').value = t.id;
        $('txDate').value = t.tx_date;
        $('txBucket').value = t.bucket_id;
        $('txAmount').value = t.amount;
        $('txNote').value = t.note;
        $('txForm').classList.remove('was-validated');
        Buckets.populateBucketSelects();
        $('txBucket').value = t.bucket_id;
        $('txBucket').disabled = false;
        this._applyAmountConstraint(t.bucket_id, !!t.linked_tx_id);
        this.modal.show();
      });
  },

  async save() {
    const form = $('txForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const id = $('txId').value;
    const payload = {
      tx_date: $('txDate').value,
      bucket_id: parseInt($('txBucket').value),
      amount: parseFloat($('txAmount').value),
      note: $('txNote').value.trim(),
    };
    try {
      if (id) {
        await api('PUT', `/api/transactions/${id}`, payload);
        toast('Transaction updated');
      } else {
        await api('POST', '/api/transactions', payload);
        State.lastTxDate = payload.tx_date;
        toast('Transaction added');
      }
      this.modal.hide();
      await this.load();
      if (State.currentSection === 'buckets') await Buckets.load();
      if (State.currentSection === 'bucket-detail') await BucketDetail.load(State.currentBucketId);
      if (State.currentSection === 'dashboard') Dashboard.load();
    } catch (e) { showError(e.message); }
  },

  async delete(id, linkedId = null) {
    const msg = linkedId
      ? 'This is a transfer. Both legs will be deleted. Continue?'
      : 'Delete this transaction?';
    const ok = await confirm(msg);
    if (!ok) return;
    try {
      const res = await api('DELETE', `/api/transactions/${id}`);
      toast(res.deleted_linked ? 'Transfer deleted (both sides)' : 'Transaction deleted');
      await this.load();
      if (State.currentSection === 'buckets') await Buckets.load();
      if (State.currentSection === 'bucket-detail') await BucketDetail.load(State.currentBucketId);
      Trash.refreshBadge();
    } catch (e) { showError(e.message); }
  },

  async post(id) {
    try {
      await api('POST', `/api/transactions/${id}/post`);
      toast('Transaction posted');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async unpost(id) {
    try {
      await api('POST', `/api/transactions/${id}/unpost`);
      toast('Transaction unposted');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  exportCsv() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    window.location.href = `/api/transactions/export?acct_id=${State.accountId}`;
  },
};

// ============================================================
// BUCKET DETAIL
// ============================================================
const BucketDetail = {
  _allRows: [],
  _shown: 0,
  _PAGE: 20,
  init() {
    $('btnBucketDetailNewTx').addEventListener('click', () => Transactions.openNew(State.currentBucketId));
    $('btnBucketDetailDeposit').addEventListener('click', () => Transactions.openNew(State.currentBucketId, 'deposit'));
    $('btnBucketDetailTransfer').addEventListener('click', () => {
      Buckets.openTransfer(State.currentBucketId, null);
    });
    UpcomingExpenses.init();
  },

  open(bucketId) {
    State.currentBucketId = bucketId;
    App.nav('bucket-detail');
  },

  async load(bucketId) {
    State.currentBucketId = bucketId;
    try {
      const [data] = await Promise.all([
        api('GET', `/api/buckets/${bucketId}/transactions`),
        UpcomingExpenses.load(bucketId),
      ]);
      this.render(data);
    } catch (e) { showError(e.message); }
  },

  render(data) {
    const b = data.bucket;
    $('bucketDetailTitle').innerHTML = `<i class="bi bi-bucket-fill me-2"></i>${esc(b.name)}`;
    $('topbarTitle').textContent = b.name;

    $('btnBucketDetailTransfer').classList.remove('d-none');
    $('btnBucketDetailNewTx').innerHTML = b.is_settlement
      ? '<i class="bi bi-plus-circle me-1"></i>New Transaction'
      : '<i class="bi bi-dash-circle me-1"></i>Withdraw';
    // Deposit is a dedicated positive-amount entry for regular buckets;
    // the Settlement bucket's "New Transaction" already allows inflows.
    $('btnBucketDetailDeposit').classList.toggle('d-none', b.is_settlement);

    const deposits = data.transactions
      .reduce((sum, t) => t.amount > 0 ? sum + t.amount : sum, 0);
    const withdrawals = data.transactions
      .reduce((sum, t) => t.amount < 0 ? sum - t.amount : sum, 0);

    $('bucketDetailStats').innerHTML = `
      <div class="col-sm-6 col-xl-3">
        <div class="stat-card stat-card--blue">
          <div class="stat-icon"><i class="bi bi-wallet2"></i></div>
          <div class="stat-body">
            <div class="stat-label">Balance</div>
            <div class="stat-value ${balClass(b.balance)}">${fmt(b.balance)}</div>
          </div>
        </div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="stat-card stat-card--purple">
          <div class="stat-icon"><i class="bi bi-cash-stack"></i></div>
          <div class="stat-body">
            <div class="stat-label">Budget</div>
            <div class="stat-value">${fmt(b.budget)}</div>
          </div>
        </div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="stat-card stat-card--green">
          <div class="stat-icon"><i class="bi bi-arrow-down-circle"></i></div>
          <div class="stat-body">
            <div class="stat-label">Total Deposits</div>
            <div class="stat-value">${fmt(deposits)}</div>
          </div>
        </div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="stat-card stat-card--red">
          <div class="stat-icon"><i class="bi bi-arrow-up-circle"></i></div>
          <div class="stat-body">
            <div class="stat-label">Total Withdrawals</div>
            <div class="stat-value">${fmt(withdrawals)}</div>
          </div>
        </div>
      </div>`;

    this._allRows = [...data.transactions].reverse();
    this._shown = 0;
    $('bucketDetailBody').innerHTML = '';

    if (!this._allRows.length) {
      $('bucketDetailBody').innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">
        <i class="bi bi-receipt fs-2 d-block mb-2 opacity-25"></i>No transactions yet.</td></tr>`;
      $('bucketDetailMoreWrap').classList.add('d-none');
      $('bucketDetailInfo').textContent = '';
      return;
    }

    this._appendRows();
  },

  _appendRows() {
    const body = $('bucketDetailBody');
    const next = this._allRows.slice(this._shown, this._shown + this._PAGE);
    this._shown += next.length;

    body.insertAdjacentHTML('beforeend', next.map(t => {
      const posted = t.posted
        ? `<span class="badge-posted"><i class="bi bi-check-circle-fill me-1"></i>Posted</span>`
        : `<span class="badge-unposted"><i class="bi bi-circle me-1"></i>Pending</span>`;
      const postBtn = t.posted
        ? `<button class="action-btn action-btn-post" onclick="BucketDetail.unpost(${t.id})" title="Unpost"><i class="bi bi-x-circle"></i></button>`
        : `<button class="action-btn action-btn-post" onclick="BucketDetail.post(${t.id})" title="Post"><i class="bi bi-check-circle"></i></button>`;
      return `
      <tr class="${t.posted ? '' : 'table-row-pending'}">
        <td class="text-muted font-monospace" style="font-size:.82rem">${t.tx_date}</td>
        <td class="text-muted" style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(t.note)}${t.linked_tx_id ? ' <span class="badge bg-secondary" style="font-size:.7rem">Transfer</span>' : ''}</td>
        <td class="text-end font-monospace ${amtClass(t.amount)}">${fmt(t.amount, true)}</td>
        <td class="text-end font-monospace ${balClass(t.running)}">${fmt(t.running)}</td>
        <td class="text-center">${posted}</td>
        <td class="text-center" style="white-space:nowrap">
          ${postBtn}
          <button class="action-btn action-btn-edit mx-1" onclick="Transactions.openEdit(${t.id})" title="Edit"><i class="bi bi-pencil"></i></button>
          <button class="action-btn action-btn-delete" onclick="BucketDetail.delete(${t.id}, ${t.linked_tx_id ?? 'null'})" title="Delete"><i class="bi bi-trash"></i></button>
        </td>
      </tr>`;
    }).join(''));

    const total = this._allRows.length;
    $('bucketDetailInfo').textContent = `Showing ${this._shown} of ${total}`;
    const moreWrap = $('bucketDetailMoreWrap');
    if (this._shown < total) {
      moreWrap.classList.remove('d-none');
    } else {
      moreWrap.classList.add('d-none');
    }
  },

  showMore() {
    this._appendRows();
  },

  async post(id) {
    try {
      await api('POST', `/api/transactions/${id}/post`);
      await this.load(State.currentBucketId);
    } catch (e) { showError(e.message); }
  },

  async unpost(id) {
    try {
      await api('POST', `/api/transactions/${id}/unpost`);
      await this.load(State.currentBucketId);
    } catch (e) { showError(e.message); }
  },

  async delete(id, linkedId = null) {
    const msg = linkedId
      ? 'This is a transfer. Both legs will be deleted. Continue?'
      : 'Delete this transaction?';
    const ok = await confirm(msg);
    if (!ok) return;
    try {
      const res = await api('DELETE', `/api/transactions/${id}`);
      toast(res.deleted_linked ? 'Transfer deleted (both sides)' : 'Transaction deleted');
      await this.load(State.currentBucketId);
      await Buckets.load();
      Trash.refreshBadge();
    } catch (e) { showError(e.message); }
  },
};

// ============================================================
// UPCOMING EXPENSES
// ============================================================
const UpcomingExpenses = {
  modal: null,
  items: [],

  init() {
    this.modal = new bootstrap.Modal($('upcomingModal'));
    $('btnNewUpcoming').addEventListener('click', () => this.openNew());
    $('btnSaveUpcoming').addEventListener('click', () => this.save());
  },

  async load(bucketId) {
    try {
      this.items = await api('GET', `/api/buckets/${bucketId}/upcoming`);
      this.render();
    } catch (e) { showError(e.message); }
  },

  render() {
    const body = $('upcomingBody');
    const totalEl = $('upcomingTotal');

    if (!this.items.length) {
      body.innerHTML = `<div class="text-center py-2 opacity-50" style="font-size:.76rem;color:var(--sidebar-text)">
        <i class="bi bi-clock-history d-block mb-1"></i>No upcoming expenses.</div>`;
      totalEl.textContent = fmt(0);
      return;
    }

    const total = this.items.reduce((s, x) => s + x.amount, 0);
    totalEl.textContent = fmt(total);

    body.innerHTML = this.items.map(x => `
      <div class="sidebar-upcoming-item">
        <div class="up-desc">${esc(x.description)}</div>
        <div class="up-meta">
          <span class="font-monospace amount-neg">${fmt(x.amount)}</span>
          <span>${x.due_date || '—'}</span>
          <span style="white-space:nowrap">
            <button class="action-btn action-btn-edit" onclick="UpcomingExpenses.openEdit(${x.id})" title="Edit"><i class="bi bi-pencil"></i></button>
            <button class="action-btn action-btn-delete" onclick="UpcomingExpenses.delete(${x.id})" title="Delete"><i class="bi bi-trash"></i></button>
          </span>
        </div>
      </div>`).join('');
  },

  openNew() {
    $('upcomingModalTitle').textContent = 'New Upcoming Expense';
    $('upcomingId').value = '';
    $('upcomingDesc').value = '';
    $('upcomingAmount').value = '0';
    $('upcomingDueDate').value = '';
    $('upcomingNotes').value = '';
    $('upcomingForm').classList.remove('was-validated');
    this.modal.show();
  },

  openEdit(id) {
    const x = this.items.find(i => i.id === id);
    if (!x) return;
    $('upcomingModalTitle').textContent = 'Edit Upcoming Expense';
    $('upcomingId').value = x.id;
    $('upcomingDesc').value = x.description;
    $('upcomingAmount').value = x.amount;
    $('upcomingDueDate').value = x.due_date || '';
    $('upcomingNotes').value = x.notes || '';
    $('upcomingForm').classList.remove('was-validated');
    this.modal.show();
  },

  async save() {
    const form = $('upcomingForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const id = $('upcomingId').value;
    const payload = {
      description: $('upcomingDesc').value.trim(),
      amount:      parseFloat($('upcomingAmount').value) || 0,
      due_date:    $('upcomingDueDate').value || '',
      notes:       $('upcomingNotes').value.trim(),
    };
    try {
      if (id) {
        await api('PUT', `/api/upcoming/${id}`, payload);
        toast('Expense updated');
      } else {
        await api('POST', `/api/buckets/${State.currentBucketId}/upcoming`, payload);
        toast('Expense added');
      }
      this.modal.hide();
      await this.load(State.currentBucketId);
    } catch (e) { showError(e.message); }
  },

  async delete(id) {
    const ok = await confirm('Delete this upcoming expense?');
    if (!ok) return;
    try {
      await api('DELETE', `/api/upcoming/${id}`);
      toast('Expense deleted');
      await this.load(State.currentBucketId);
    } catch (e) { showError(e.message); }
  },
};

// ============================================================
// IOU / PAYMENT REQUESTS
// ============================================================
const Iou = {
  modal: null,
  linkModal: null,
  detailModal: null,
  items: [],
  activeTab: 'sent',
  linkPage: 1,
  linkPerPage: 20,
  linkQuery: '',
  pendingLinkIouId: null,
  _linkSearchTimer: null,

  init() {
    this.modal      = new bootstrap.Modal($('iouModal'));
    this.linkModal  = new bootstrap.Modal($('iouLinkModal'));
    this.detailModal = new bootstrap.Modal($('iouDetailModal'));

    $('btnNewIou').addEventListener('click', () => this.openNew());
    $('btnSaveIou').addEventListener('click', () => this.save());
    $('btnLookupPayee').addEventListener('click', () => this.lookupPayee());
    $('iouPayeeEmail').addEventListener('keydown', e => {
      if (e.key === 'Enter') { e.preventDefault(); this.lookupPayee(); }
    });

    document.querySelectorAll('[data-iou-tab]').forEach(btn => {
      btn.addEventListener('click', () => this.switchTab(btn.dataset.iouTab));
    });

    $('iouLinkSearch').addEventListener('input', () => {
      clearTimeout(this._linkSearchTimer);
      this._linkSearchTimer = setTimeout(() => {
        this.linkQuery = $('iouLinkSearch').value.trim();
        this.loadLinkTx(1);
      }, 300);
    });
  },

  async load() {
    try {
      this.items = await api('GET', '/api/iou');
      this.render();
      this.updateSidebarBadge();
    } catch (e) { showError(e.message); }
  },

  render() {
    const sent     = this.items.filter(x => x.role === 'requester');
    const received = this.items.filter(x => x.role === 'payee');
    const activeSent     = sent.filter(x => x.status === 'pending' || x.status === 'linked');
    const activeReceived = received.filter(x => x.status === 'pending' || x.status === 'linked');
    $('iouBadgeSent').textContent     = activeSent.length     || '';
    $('iouBadgeReceived').textContent = activeReceived.length || '';
    this.renderSent(sent);
    this.renderReceived(received);
  },

  _PAGE: 20,
  _paging: {
    sent:     { rows: [], shown: 0, target: parseInt(sessionStorage.getItem('iouSentShown')) || 20 },
    received: { rows: [], shown: 0, target: parseInt(sessionStorage.getItem('iouReceivedShown')) || 20 },
  },

  // Both tabs share one paging engine; they differ only in element ids,
  // the empty-state message and the row builder.
  _renderTable(kind, rows) {
    const cap = kind === 'sent' ? 'Sent' : 'Received';
    const p = this._paging[kind];
    p.rows = rows;
    p.shown = 0;

    const body = $(`iou${cap}Body`);
    body.innerHTML = '';

    if (!rows.length) {
      body.innerHTML = kind === 'sent'
        ? `<tr><td colspan="6" class="text-center text-muted py-4">
            <i class="bi bi-send fs-2 d-block mb-2 opacity-25"></i>No payment requests sent yet.</td></tr>`
        : `<tr><td colspan="6" class="text-center text-muted py-4">
            <i class="bi bi-inbox fs-2 d-block mb-2 opacity-25"></i>No payment requests received.</td></tr>`;
      $(`iou${cap}MoreWrap`).classList.add('d-none');
      $(`iou${cap}Info`).textContent = '';
      return;
    }

    this._appendRows(kind);
  },

  _appendRows(kind) {
    const cap = kind === 'sent' ? 'Sent' : 'Received';
    const p = this._paging[kind];
    const limit = Math.min(p.target, p.rows.length);
    const next = p.rows.slice(p.shown, limit);
    p.shown = limit;

    const row = kind === 'sent' ? x => this._rowSent(x) : x => this._rowReceived(x);
    $(`iou${cap}Body`).insertAdjacentHTML('beforeend', next.map(row).join(''));

    $(`iou${cap}Info`).textContent = `Showing ${p.shown} of ${p.rows.length}`;
    $(`iou${cap}MoreWrap`).classList.toggle('d-none', p.shown >= p.rows.length);
  },

  showMore(kind) {
    const p = this._paging[kind];
    p.target += this._PAGE;
    sessionStorage.setItem(kind === 'sent' ? 'iouSentShown' : 'iouReceivedShown', p.target);
    this._appendRows(kind);
  },

  renderSent(rows) {
    this._renderTable('sent', rows);
  },

  _rowSent(x) {
    const canAct = x.status === 'pending' || x.status === 'linked';
    const actions = canAct ? `
      <button class="action-btn action-btn-post me-1" onclick="Iou.settle(${x.id})" title="Mark settled">
        <i class="bi bi-check-circle"></i>
      </button>
      <button class="action-btn action-btn-delete" onclick="Iou.cancel(${x.id})" title="Cancel">
        <i class="bi bi-x-circle"></i>
      </button>` : '';
    return `
    <tr>
      <td><strong>${esc(x.other_user_name)}</strong><br><small class="text-muted">${esc(x.other_user_email)}</small></td>
      <td class="text-end font-monospace text-success">${fmt(x.amount)}</td>
      <td style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(x.description)}</td>
      <td class="d-none d-md-table-cell text-muted font-monospace" style="font-size:.82rem">${x.due_date || '—'}</td>
      <td class="text-center">${this.statusBadge(x.status)}</td>
      <td class="text-center" style="white-space:nowrap">
        <button class="action-btn action-btn-edit me-1" onclick="Iou.openDetail(${x.id})" title="View details">
          <i class="bi bi-info-circle"></i>
        </button>
        ${actions}
      </td>
    </tr>`;
  },

  renderReceived(rows) {
    this._renderTable('received', rows);
  },

  _rowReceived(x) {
    let actions = '';
    if (x.status === 'pending') {
      actions = `<button class="action-btn action-btn-refill" onclick="Iou.openLink(${x.id})" title="Link a transaction">
        <i class="bi bi-link-45deg"></i>
      </button>`;
    } else if (x.status === 'linked') {
      actions = `
      <button class="action-btn action-btn-refill me-1" onclick="Iou.openLink(${x.id})" title="Re-link transaction">
        <i class="bi bi-link-45deg"></i>
      </button>
      <button class="action-btn action-btn-delete" onclick="Iou.unlink(${x.id})" title="Unlink">
        <i class="bi bi-link"></i>
      </button>`;
    }
    return `
    <tr>
      <td><strong>${esc(x.other_user_name)}</strong><br><small class="text-muted">${esc(x.other_user_email)}</small></td>
      <td class="text-end font-monospace amount-neg">${fmt(x.amount)}</td>
      <td style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(x.description)}</td>
      <td class="d-none d-md-table-cell text-muted font-monospace" style="font-size:.82rem">${x.due_date || '—'}</td>
      <td class="text-center">${this.statusBadge(x.status)}</td>
      <td class="text-center" style="white-space:nowrap">
        <button class="action-btn action-btn-edit me-1" onclick="Iou.openDetail(${x.id})" title="View details">
          <i class="bi bi-info-circle"></i>
        </button>
        ${actions}
      </td>
    </tr>`;
  },

  openNew() {
    $('iouForm').classList.remove('was-validated');
    $('iouPayeeEmail').value = '';
    $('iouPayeeId').value = '';
    $('iouPayeeName').textContent = '';
    $('iouPayeeName').className = 'form-text';
    $('iouAmount').value = '';
    $('iouDesc').value = '';
    $('iouDueDate').value = '';
    $('iouNotes').value = '';
    this.modal.show();
  },

  async lookupPayee() {
    const email = $('iouPayeeEmail').value.trim();
    if (!email) return;
    const nameEl = $('iouPayeeName');
    nameEl.textContent = 'Looking up…';
    nameEl.className = 'form-text text-muted';
    $('iouPayeeId').value = '';
    try {
      const res = await api('GET', `/api/users/lookup?email=${encodeURIComponent(email)}`);
      $('iouPayeeId').value = res.id;
      nameEl.textContent = `✓ Found: ${res.name}`;
      nameEl.className = 'form-text text-success fw-semibold';
    } catch (e) {
      nameEl.textContent = e.message;
      nameEl.className = 'form-text text-danger';
    }
  },

  async save() {
    const form = $('iouForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;
    if (!$('iouPayeeId').value) {
      await this.lookupPayee();
      if (!$('iouPayeeId').value) return;
    }
    const payload = {
      payee_email:  $('iouPayeeEmail').value.trim(),
      amount:       parseFloat($('iouAmount').value) || 0,
      description:  $('iouDesc').value.trim(),
      due_date:     $('iouDueDate').value || '',
      notes:        $('iouNotes').value.trim(),
    };
    try {
      await api('POST', '/api/iou', payload);
      toast('Payment request sent');
      this.modal.hide();
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async cancel(id) {
    const ok = await confirm('Cancel this payment request?', 'Cancel Request', false);
    if (!ok) return;
    try {
      await api('POST', `/api/iou/${id}/cancel`);
      toast('Request cancelled');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async settle(id) {
    const ok = await confirm('Mark this IOU as settled?', 'Mark Settled', false);
    if (!ok) return;
    try {
      await api('POST', `/api/iou/${id}/settle`);
      toast('Marked as settled');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  openLink(iouId) {
    this.pendingLinkIouId = iouId;
    this.linkQuery = '';
    this.linkPage  = 1;
    $('iouLinkSearch').value = '';
    this.loadLinkTx(1);
    this.linkModal.show();
  },

  async loadLinkTx(page) {
    this.linkPage = page;
    const params = new URLSearchParams({ page, per_page: this.linkPerPage });
    if (this.linkQuery) params.append('q', this.linkQuery);
    try {
      const data = await api('GET', `/api/iou/transactions?${params}`);
      this.renderLinkTx(data);
    } catch (e) { showError(e.message); }
  },

  renderLinkTx(data) {
    const body = $('iouLinkTxBody');
    if (!data.transactions.length) {
      body.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-3">No transactions found.</td></tr>`;
      $('iouLinkPagination').innerHTML = '';
      return;
    }
    body.innerHTML = data.transactions.map(t => `
      <tr>
        <td class="font-monospace" style="font-size:.82rem">${t.tx_date}</td>
        <td><span class="badge bg-light text-secondary border" style="font-size:.72rem">${esc(t.bucket)}</span></td>
        <td class="text-end font-monospace ${amtClass(t.amount)}">${fmt(t.amount, true)}</td>
        <td class="text-muted" style="max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(t.note)}</td>
        <td class="text-center">
          <button class="btn btn-sm btn-outline-primary py-0" onclick="Iou.selectTx(${t.id})">
            <i class="bi bi-link-45deg me-1"></i>Link
          </button>
        </td>
      </tr>`).join('');
    buildPagination('iouLinkPagination', data.page, data.pages, p => this.loadLinkTx(p));
  },

  async selectTx(txId) {
    try {
      await api('POST', `/api/iou/${this.pendingLinkIouId}/link`, { tx_id: txId });
      toast('Transaction linked');
      this.linkModal.hide();
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async unlink(id) {
    const ok = await confirm('Remove the transaction link? The IOU will return to Pending.', 'Unlink', false);
    if (!ok) return;
    try {
      await api('POST', `/api/iou/${id}/unlink`);
      toast('Transaction unlinked');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  openDetail(id) {
    const x = this.items.find(i => i.id === id);
    if (!x) return;
    const otherLabel = x.role === 'requester' ? 'To' : 'From';
    let linkedHtml = '';
    if (x.linked_tx_id) {
      linkedHtml = `
      <div class="mt-3 p-2 bg-light rounded border">
        <div class="small fw-semibold text-info mb-1"><i class="bi bi-link-45deg me-1"></i>Linked Transaction</div>
        <div class="small font-monospace">${x.linked_tx_date} &nbsp; ${fmt(x.linked_tx_amount, true)} &nbsp; ${esc(x.linked_tx_note || '')}</div>
      </div>`;
    }
    $('iouDetailBody').innerHTML = `
      <dl class="row mb-0">
        <dt class="col-4 text-muted">${otherLabel}</dt>
        <dd class="col-8">${esc(x.other_user_name)} <small class="text-muted">(${esc(x.other_user_email)})</small></dd>
        <dt class="col-4 text-muted">Amount</dt>
        <dd class="col-8 fw-semibold">${fmt(x.amount)}</dd>
        <dt class="col-4 text-muted">Description</dt>
        <dd class="col-8">${esc(x.description)}</dd>
        <dt class="col-4 text-muted">Due Date</dt>
        <dd class="col-8">${x.due_date || '—'}</dd>
        <dt class="col-4 text-muted">Status</dt>
        <dd class="col-8">${this.statusBadge(x.status)}</dd>
        <dt class="col-4 text-muted">Created</dt>
        <dd class="col-8 text-muted small">${x.created_at}</dd>
        ${x.notes ? `<dt class="col-4 text-muted">Notes</dt><dd class="col-8">${esc(x.notes)}</dd>` : ''}
      </dl>
      ${linkedHtml}`;
    this.detailModal.show();
  },

  statusBadge(s) {
    const cls = { pending: 'warning text-dark', linked: 'info', settled: 'success', cancelled: 'secondary' };
    const label = s.charAt(0).toUpperCase() + s.slice(1);
    return `<span class="badge bg-${cls[s] || 'secondary'}">${label}</span>`;
  },

  switchTab(tab) {
    this.activeTab = tab;
    $('iouSentPanel').classList.toggle('d-none', tab !== 'sent');
    $('iouReceivedPanel').classList.toggle('d-none', tab !== 'received');
    document.querySelectorAll('[data-iou-tab]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.iouTab === tab);
    });
  },

  updateSidebarBadge() {
    const badge = $('iouSidebarBadge');
    const count = this.items.filter(x => x.role === 'payee' && x.status === 'pending').length;
    badge.textContent = count;
    badge.classList.toggle('d-none', count === 0);
  },
};

// ============================================================
// TRASH (deleted transactions)
// ============================================================
const Trash = {
  _selected: new Set(),

  init() {
    $('btnPurgeAll').addEventListener('click', () => this.purgeAll());
    $('btnPurgeSelected').addEventListener('click', () => this.purgeSelected());
  },

  async load() {
    const noAcct = $('trashNoAccount');
    const card   = $('trashCard');
    if (!State.accountId) {
      noAcct.classList.remove('d-none');
      card.classList.add('d-none');
      return;
    }
    noAcct.classList.add('d-none');
    card.classList.remove('d-none');
    try {
      const rows = await api('GET', `/api/transactions/deleted?acct_id=${State.accountId}`);
      this.render(rows);
      this._setBadge(rows.length);
    } catch (e) { showError(e.message); }
  },

  render(rows) {
    const body = $('trashBody');
    this._selected.clear();
    this._updatePurgeSelectedBtn();
    const sa = $('trashSelectAll');
    sa.checked = false;
    sa.indeterminate = false;

    if (!rows.length) {
      body.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">
        <i class="bi bi-trash3 fs-2 d-block mb-2 opacity-25"></i>No deleted transactions.</td></tr>`;
      return;
    }
    body.innerHTML = rows.map(t => `
      <tr class="text-muted">
        <td class="text-center">
          <input type="checkbox" class="form-check-input trash-check" data-id="${t.id}" onchange="Trash._onCheck(this)"/>
        </td>
        <td class="font-monospace" style="font-size:.82rem">${t.tx_date}</td>
        <td>
          <span class="badge bg-light text-secondary border" style="font-size:.75rem">${esc(t.bucket)}</span>
          ${t.linked_tx_id ? ' <span class="badge bg-secondary" style="font-size:.7rem">Transfer</span>' : ''}
        </td>
        <td class="text-end font-monospace ${amtClass(t.amount)}">${fmt(t.amount, true)}</td>
        <td style="max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(t.note)}</td>
        <td class="text-center" style="white-space:nowrap">
          <button class="action-btn action-btn-post me-1" onclick="Trash.restore(${t.id}, ${t.linked_tx_id ?? 'null'})" title="Restore">
            <i class="bi bi-arrow-counterclockwise"></i>
          </button>
          <button class="action-btn action-btn-delete" onclick="Trash.purgeOne(${t.id}, ${t.linked_tx_id ?? 'null'})" title="Permanently delete">
            <i class="bi bi-trash3-fill"></i>
          </button>
        </td>
      </tr>`).join('');
  },

  _onCheck(checkbox) {
    const id = parseInt(checkbox.dataset.id);
    if (checkbox.checked) this._selected.add(id);
    else this._selected.delete(id);
    this._syncSelectAll();
    this._updatePurgeSelectedBtn();
  },

  _toggleAll(checkbox) {
    this._selected.clear();
    document.querySelectorAll('.trash-check').forEach(c => {
      c.checked = checkbox.checked;
      if (checkbox.checked) this._selected.add(parseInt(c.dataset.id));
    });
    checkbox.indeterminate = false;
    this._updatePurgeSelectedBtn();
  },

  _syncSelectAll() {
    const checks = [...document.querySelectorAll('.trash-check')];
    const all  = checks.length > 0 && checks.every(c => c.checked);
    const some = checks.some(c => c.checked);
    const sa = $('trashSelectAll');
    sa.checked = all;
    sa.indeterminate = some && !all;
  },

  _updatePurgeSelectedBtn() {
    const btn = $('btnPurgeSelected');
    const n = this._selected.size;
    if (n > 0) {
      btn.classList.remove('d-none');
      btn.innerHTML = `<i class="bi bi-trash3-fill me-1"></i>Purge Selected (${n})`;
    } else {
      btn.classList.add('d-none');
    }
  },

  async restore(id, linkedId = null) {
    const msg = linkedId
      ? 'Restore this transfer? Both legs will be restored.'
      : 'Restore this transaction?';
    const ok = await confirm(msg, 'Restore', false);
    if (!ok) return;
    try {
      const res = await api('POST', `/api/transactions/${id}/restore`);
      toast(res.restored_linked ? 'Transfer restored (both sides)' : 'Transaction restored');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async purgeOne(id, linkedId = null) {
    const msg = linkedId
      ? 'Permanently delete this transfer? Both legs will be removed. This cannot be undone.'
      : 'Permanently delete this transaction? This cannot be undone.';
    const ok = await confirm(msg);
    if (!ok) return;
    try {
      await api('DELETE', `/api/transactions/${id}/purge`);
      toast('Permanently deleted');
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async purgeSelected() {
    if (!this._selected.size) return;
    const n = this._selected.size;
    const ok = await confirm(`Permanently delete ${n} selected transaction(s)? This cannot be undone.`);
    if (!ok) return;
    try {
      const res = await api('POST', '/api/transactions/purge-batch', { ids: [...this._selected] });
      toast(`Purged ${res.purged} transaction(s)`);
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async purgeAll() {
    if (!State.accountId) return;
    const ok = await confirm('Permanently delete all transactions in the trash? This cannot be undone.');
    if (!ok) return;
    try {
      const res = await api('DELETE', `/api/transactions/deleted?acct_id=${State.accountId}`);
      toast(`Purged ${res.purged} transaction(s)`);
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async refreshBadge() {
    if (!State.accountId) return;
    try {
      const rows = await api('GET', `/api/transactions/deleted?acct_id=${State.accountId}`);
      this._setBadge(rows.length);
    } catch (e) {}
  },

  _setBadge(count) {
    const badge = $('trashSidebarBadge');
    badge.textContent = count || '';
    badge.classList.toggle('d-none', !count);
  },
};

// ============================================================
// DASHBOARD
// ============================================================
const Dashboard = {
  async load() {
    if (!State.accountId) {
      $('dashboardNoAccount').classList.remove('d-none');
      $('dashboardContent').classList.add('d-none');
      return;
    }
    $('dashboardNoAccount').classList.add('d-none');
    $('dashboardContent').classList.remove('d-none');

    try {
      const [summary, buckets] = await Promise.all([
        api('GET', `/api/summary?acct_id=${State.accountId}`),
        api('GET', `/api/buckets?acct_id=${State.accountId}`),
      ]);

      $('statBalance').textContent = fmt(summary.total_balance);
      $('statBuckets').textContent = summary.bucket_count;
      $('statTxCount').textContent = summary.tx_count.toLocaleString();
      $('statSpending').textContent = fmt(summary.month_spending);

      const body = $('dashBucketsBody');
      if (!buckets.length) {
        body.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-3">No buckets yet.</td></tr>`;
      } else {
        body.innerHTML = buckets.slice(0, 8).map(b => {
          const pct = b.budget > 0 ? Math.min(200, Math.round((b.balance / b.budget) * 100)) : 0;
          const barClass = pct >= 100 ? 'usage-ok' : pct >= 50 ? 'usage-warn' : 'usage-over';
          return `
          <tr>
            <td><strong>${esc(b.name)}</strong></td>
            <td class="text-end font-monospace">${fmt(b.budget)}</td>
            <td class="text-end font-monospace ${balClass(b.balance)}">${fmt(b.balance)}</td>
            <td>
              <div class="usage-bar-wrap">
                <div class="usage-bar-bg">
                  <div class="usage-bar-fill ${barClass}" style="width:${Math.min(100, pct)}%"></div>
                </div>
                <div class="usage-pct">${pct}%</div>
              </div>
            </td>
          </tr>`;
        }).join('');
      }
    } catch (e) { showError(e.message); }
  },
};

// ============================================================
// Navigation
// ============================================================
const SECTIONS = ['dashboard', 'accounts', 'buckets', 'transactions', 'bucket-detail', 'iou', 'trash'];
const SECTION_LABELS = {
  dashboard: 'Dashboard',
  accounts: 'Accounts',
  buckets: 'Buckets',
  transactions: 'Transactions',
  'bucket-detail': 'Bucket',
  iou: 'IOUs',
  trash: 'Deleted Transactions',
};

const App = {
  nav(section) {
    State.currentSection = section;
    SECTIONS.forEach(s => {
      const el = $(`section-${s}`);
      el.classList.toggle('d-none', s !== section);
    });
    const sidebarSection = section === 'bucket-detail' ? 'buckets' : section;
    document.querySelectorAll('.sidebar-link').forEach(a => {
      a.classList.toggle('active', a.dataset.section === sidebarSection);
    });
    $('topbarTitle').textContent = SECTION_LABELS[section] || section;

    $('sidebarUpcoming').classList.toggle('d-none', section !== 'bucket-detail');

    if (section === 'dashboard')     Dashboard.load();
    if (section === 'accounts')      Accounts.load();
    if (section === 'buckets')       Buckets.load();
    if (section === 'transactions')  { Buckets.load(); Transactions.load(); }
    if (section === 'bucket-detail') BucketDetail.load(State.currentBucketId);
    if (section === 'iou')           Iou.load();
    if (section === 'trash')         Trash.load();
  },

  init() {
    // Sidebar nav
    document.querySelectorAll('.sidebar-link').forEach(a => {
      a.addEventListener('click', e => {
        e.preventDefault();
        this.nav(a.dataset.section);
      });
    });

    // Sidebar toggle — overlay on mobile, collapse on desktop
    const closeMobileSidebar = () => {
      $('sidebar').classList.remove('mobile-open');
      $('sidebarBackdrop').classList.remove('show');
    };
    $('sidebarToggle').addEventListener('click', () => {
      if (window.innerWidth <= 768) {
        $('sidebar').classList.toggle('mobile-open');
        $('sidebarBackdrop').classList.toggle('show');
      } else {
        $('sidebar').classList.toggle('collapsed');
      }
    });
    $('sidebarBackdrop').addEventListener('click', closeMobileSidebar);
    document.querySelectorAll('.sidebar-link').forEach(a => {
      a.addEventListener('click', () => { if (window.innerWidth <= 768) closeMobileSidebar(); });
    });

    // Account selector change
    $('accountSelect').addEventListener('change', async () => {
      const val = $('accountSelect').value;
      State.accountId = val ? parseInt(val) : null;
      if (State.currentSection !== 'accounts') {
        this.nav(State.currentSection);
      }
    });

    // Modules init
    Accounts.init();
    Buckets.init();
    Transactions.init();
    BucketDetail.init();
    Iou.init();
    Trash.init();
    ChangePassword.init();
    Intro.init();

    // Initial data load
    Accounts.load().then(() => {
      this.nav('dashboard');
      Trash.refreshBadge();
    });
  },
};

// ============================================================
// INTRO WALKTHROUGH
// ============================================================
const Intro = {
  modal: null,
  video: null,

  init() {
    this.modal = new bootstrap.Modal($('introModal'));
    this.video = $('introVideo');

    [$('btnWatchIntro'), $('btnWatchIntroSidebar')].forEach(btn => {
      if (btn) btn.addEventListener('click', () => this.open());
    });

    // Stop playback (and the download) when the modal is dismissed.
    $('introModal').addEventListener('hidden.bs.modal', () => {
      this.video.pause();
    });

    // Watching is not "activity" as far as the idle timer is concerned, so
    // keep it alive while the video is actually playing.
    this.video.addEventListener('timeupdate', () => IdleTimer.reset());

    // First login: open it unprompted, and remember that we did.
    if (document.body.dataset.showIntro === '1') {
      this.open();
      this.markSeen();
    }
  },

  open() {
    // Assigning src lazily keeps the video off the wire until it's wanted.
    if (!this.video.src) this.video.src = '/media/intro.mp4';
    this.modal.show();
  },

  async markSeen() {
    try {
      await api('POST', '/api/intro-seen');
    } catch (e) {
      // Not worth interrupting the user — worst case the tour opens again.
      console.warn('Could not record intro as seen:', e.message);
    }
  },
};

// ============================================================
// CHANGE PASSWORD
// ============================================================
const ChangePassword = {
  modal: null,

  init() {
    this.modal = new bootstrap.Modal($('changePasswordModal'));
    [$('btnChangePassword'), $('btnChangePasswordSidebar')].forEach(btn => {
      btn.addEventListener('click', () => this.open());
    });
    $('btnSavePassword').addEventListener('click', () => this.save());
    // Clear form when modal closes
    $('changePasswordModal').addEventListener('hidden.bs.modal', () => this.reset());
  },

  open() {
    this.reset();
    this.modal.show();
  },

  reset() {
    $('cpCurrent').value = '';
    $('cpNew').value = '';
    $('cpConfirm').value = '';
    $('cpMismatch').classList.add('d-none');
    $('changePasswordForm').classList.remove('was-validated');
  },

  async save() {
    const form = $('changePasswordForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const newPwd = $('cpNew').value;
    const confirm = $('cpConfirm').value;
    if (newPwd !== confirm) {
      $('cpMismatch').classList.remove('d-none');
      return;
    }
    $('cpMismatch').classList.add('d-none');

    try {
      await api('POST', '/api/change-password', {
        current_password:  $('cpCurrent').value,
        new_password:      newPwd,
        confirm_password:  confirm,
      });
      toast('Password updated successfully');
      this.modal.hide();
    } catch (e) {
      showError(e.message);
    }
  },
};

// helper called from oninput on the confirm field
function cpCheckMatch() {
  const mismatch = $('cpNew').value && $('cpConfirm').value &&
                   $('cpNew').value !== $('cpConfirm').value;
  $('cpMismatch').classList.toggle('d-none', !mismatch);
}

// ---- XSS-safe escaping ----
function esc(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ============================================================
// IDLE SESSION TIMEOUT
// ============================================================
const IdleTimer = {
  IDLE_MS:  5 * 60 * 1000,   // 5 minutes of inactivity → logout
  WARN_MS:  4 * 60 * 1000,   // warn at 4 minutes
  _idleTimer: null,
  _warnTimer: null,
  _warned: false,

  reset() {
    clearTimeout(this._idleTimer);
    clearTimeout(this._warnTimer);
    this._warned = false;

    this._warnTimer = setTimeout(() => {
      if (!this._warned) {
        this._warned = true;
        toast('You have been inactive for 4 minutes. You will be signed out in 1 minute.', 'warning');
      }
    }, this.WARN_MS);

    this._idleTimer = setTimeout(() => {
      window.location.href = '/logout';
    }, this.IDLE_MS);
  },

  init() {
    ['mousemove', 'keydown', 'mousedown', 'touchstart', 'scroll'].forEach(evt => {
      document.addEventListener(evt, () => this.reset(), { passive: true });
    });
    this.reset();
  },
};

// Boot
document.addEventListener('DOMContentLoaded', () => { App.init(); IdleTimer.init(); });
