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
// With `opts.date`, the dialog also asks which day to book on and resolves to
// that ISO date instead of `true` — still falsy only when dismissed, so
// `if (!ok) return;` reads the same at every call site.
function confirm(msg, label = 'Delete', danger = true, opts = {}) {
  return new Promise(resolve => {
    $('confirmMessage').textContent = msg;
    const btn = $('btnConfirmOk');
    btn.textContent = label;
    btn.className = `btn btn-sm ${danger ? 'btn-danger' : 'btn-warning'}`;
    const dateWrap = $('confirmDateWrap');
    const dateInput = $('confirmDate');
    dateWrap.classList.toggle('d-none', !opts.date);
    if (opts.date) dateInput.value = today();
    const modal = new bootstrap.Modal($('confirmModal'));
    const onOk = () => {
      const value = opts.date ? (dateInput.value || today()) : true;
      cleanup();
      modal.hide();
      resolve(value);
    };
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

  // Accounts are shown as dashboard tiles; this just keeps State and the
  // sidebar selector current.
  async load() {
    try {
      State.accounts = await api('GET', '/api/accounts');
      this.populateSelector();
    } catch (e) { showError(e.message); }
  },

  populateSelector() {
    const sel = $('accountSelect');
    const current = State.accountId;
    sel.innerHTML = '<option value="">— Select account —</option>' +
      State.accounts.map(a => `<option value="${a.id}" ${a.id === current ? 'selected' : ''}>${esc(a.name)}</option>`).join('');
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
      App.refreshBalances();
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
      let created = null;
      if (id) {
        await api('PUT', `/api/accounts/${id}`, { name });
        toast('Account updated');
      } else {
        created = await api('POST', '/api/accounts', { name });
        toast('Account created');
      }
      this.modal.hide();
      await this.load();
      if (created) {
        Dashboard.openAccount(created.id);
      } else {
        App.refreshBalances();
        // A rename from the Buckets header has to repaint that header.
        if (State.currentSection === 'buckets') Buckets.render();
      }
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
      App.nav('dashboard');
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

    // Header doubles as the account header while an account is open.
    const acct = State.accounts.find(a => a.id === State.accountId);
    $('bucketsAccountName').textContent = acct ? acct.name : 'Buckets';
    $('bucketsAccountMenu').classList.toggle('d-none', !State.accountId);

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

      // Group rows carry their children's totals, so every sum here has to
      // skip them or the same dollar is counted twice.
      const totalVal = State.buckets.filter(b => !b.is_settlement && !b.is_parent)
        .reduce((sum, b) => sum + parseFloat(b.balance), 0);
      const totalEl = $('totalBucketBalance');
      totalEl.textContent = fmt(totalVal);
      totalEl.className = `fw-bold fs-5 font-monospace ${balClass(totalVal)}`;

      const netVal = State.buckets.filter(b => !b.is_parent)
        .reduce((sum, b) => sum + parseFloat(b.balance), 0);
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
    this._rowNo = 0;   // numbers top-level rows only; sub-buckets sit unnumbered
    body.innerHTML = '';

    if (!this._allRows.length) {
      body.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">
        <i class="bi bi-bucket fs-2 d-block mb-2 opacity-25"></i>No buckets yet.</td></tr>`;
      $('bucketsMoreWrap').classList.add('d-none');
      $('bucketsInfo').textContent = '';
      $('bucketsFoot').classList.add('d-none');
      return;
    }

    this._renderTotals();
    this._appendRows();
  },

  // The table pages in 25 rows at a time, but the footer sums every bucket the
  // filter matched — a total that grew as you clicked "More" would be a lie.
  _renderTotals() {
    // Group rows are drawn but never summed — their numbers are the children's.
    const sum = key => this._allRows
      .filter(b => !b.is_parent)
      .reduce((s, b) => s + parseFloat(b[key] || 0), 0);
    const budget = sum('budget');
    const balance = sum('balance');
    const refillMtd = sum('refill_mtd');

    $('bucketsTotalBudget').textContent = fmt(budget);
    const balEl = $('bucketsTotalBalance');
    balEl.textContent = fmt(balance);
    balEl.className = `text-end font-monospace ${balClass(balance)}`;
    $('bucketsTotalRefillMtd').textContent = fmt(refillMtd);
    $('bucketsFoot').classList.remove('d-none');
  },

  _PAGE: 25,
  _allRows: [],
  _shown: 0,
  _target: parseInt(sessionStorage.getItem('bucketsShown')) || 25,

  // Groups a user has collapsed. Session-scoped like _target above — a folded
  // group is a view preference for this sitting, not a saved setting.
  _collapsed: new Set(JSON.parse(sessionStorage.getItem('bucketsCollapsed') || '[]')),

  // Children of a collapsed group are not drawn, and so do not eat into the
  // page size either.
  _visibleRows() {
    return this._allRows.filter(b => !(b.parent_id && this._collapsed.has(b.parent_id)));
  },

  toggleGroup(id) {
    if (this._collapsed.has(id)) this._collapsed.delete(id); else this._collapsed.add(id);
    sessionStorage.setItem('bucketsCollapsed', JSON.stringify([...this._collapsed]));
    this.render();
  },

  _appendRows() {
    const body = $('bucketsBody');
    const rows = this._visibleRows();
    const limit = Math.min(this._target, rows.length);
    const next = rows.slice(this._shown, limit);
    this._shown = limit;

    body.insertAdjacentHTML('beforeend', next.map(b => {
      const overdrawn = b.balance < 0;
      const child = !!b.parent_id;
      const collapsed = this._collapsed.has(b.id);

      // A group names itself and folds; a leaf opens its ledger.
      const nameCell = b.is_parent
        ? `<button type="button" class="bucket-group-toggle" onclick="Buckets.toggleGroup(${b.id})"
                   title="${collapsed ? 'Show' : 'Hide'} sub-buckets">
             <i class="bi bi-chevron-${collapsed ? 'right' : 'down'}"></i>
             <strong>${esc(b.name)}</strong>
             <span class="badge-group">${b.child_count}</span>
           </button>`
        : `<a href="#" class="text-decoration-none" onclick="BucketDetail.open(${b.id}); return false;"><strong>${esc(b.name)}</strong></a>`;

      // The Refill All switch belongs to the group, which answers for
      // everything under it — so a sub-bucket's cell only reports, and the
      // single control sits on the group row.
      const factorText = b.is_parent
        ? '<span class="text-muted">—</span>'
        : `<span class="font-monospace${b.auto_refill ? '' : ' text-decoration-line-through'}">${b.refill_factor}×</span>`;
      const manualBadge = b.auto_refill ? '' : '<span class="badge-manual">Manual</span>';

      const factorCell = child
        ? `<span class="refill-mode-static"
                 title="Follows its group — set Refill All on &quot;${esc(State.buckets.find(x => x.id === b.parent_id)?.name || '')}&quot;">
             ${factorText}${manualBadge}
           </span>`
        : `<button type="button" class="refill-mode" onclick="Buckets.toggleAutoRefill(${b.id})"
                   title="${b.is_parent
                     ? (b.auto_refill
                         ? 'Refill All fills this group — click to exclude it and all its sub-buckets'
                         : 'Refill All skips this group and all its sub-buckets — click to include')
                     : (b.auto_refill
                         ? 'Included in Refill All — click to exclude'
                         : 'Excluded from Refill All — click to include')}">
             ${factorText}${manualBadge}
           </button>`;

      return `
      <tr class="${overdrawn ? 'bucket-overdrawn' : ''}${b.is_parent ? ' bucket-group' : ''}${child ? ' bucket-child' : ''}">
        <td class="text-muted">${child ? '' : ++this._rowNo}</td>
        <td>
          ${child ? '<span class="bucket-child-tick">\u203a</span>' : ''}${nameCell}
          ${overdrawn ? '<span class="badge-overdraft"><i class="bi bi-exclamation-triangle-fill me-1"></i>Overdraft</span>' : ''}
        </td>
        <td class="text-end font-monospace">${fmt(b.budget)}</td>
        <td class="text-end font-monospace ${balClass(b.balance)}">${fmt(b.balance)}</td>
        <td class="text-end font-monospace text-success">${fmt(b.refill_mtd)}</td>
        <td class="text-end">${factorCell}</td>
        <td class="text-center">
          <button class="action-btn action-btn-refill me-1" onclick="Buckets.refill(${b.id})"
                  title="${b.is_parent ? 'Refill every sub-bucket from Settlement' : 'Refill from Settlement'}">
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

    const total = rows.length;
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

  // Money can only land on a leaf, so a group becomes an <optgroup> label:
  // the browser won't let anyone select it, which is the rule enforced for
  // free in every dropdown that lists buckets.
  bucketOptions(list) {
    return list.map(b => {
      if (b.parent_id) return '';        // drawn inside its group below
      if (!b.is_parent) return `<option value="${b.id}">${esc(b.name)}</option>`;
      const kids = list.filter(k => k.parent_id === b.id);
      if (!kids.length) return '';       // a group whose children are filtered out
      return `<optgroup label="${esc(b.name)}">`
        + kids.map(k => `<option value="${k.id}">${esc(k.name)}</option>`).join('')
        + '</optgroup>';
    }).join('');
  },

  populateBucketSelects() {
    const settlement = State.buckets.find(b => b.is_settlement);
    const regular = State.buckets.filter(b => !b.is_settlement);
    const allOpts = this.bucketOptions(State.buckets);
    // For tx bucket: Settlement first (easy deposit entry), then regular buckets
    const txOpts = '<option value="">— Select bucket —</option>' +
      (settlement ? `<option value="${settlement.id}">Settlement</option>` : '') +
      this.bucketOptions(regular);

    const txBucket = $('txBucket');
    if (txBucket) { const prev = txBucket.value; txBucket.innerHTML = txOpts; if (prev) txBucket.value = prev; }

    ['transferFrom', 'transferTo'].forEach(id => {
      const el = $(id);
      if (el) { const prev = el.value; el.innerHTML = allOpts; if (prev) el.value = prev; }
    });
  },

  // Only a top-level bucket can be a parent (one level deep), and a bucket
  // that is itself a group can't be filed under another.
  // A sub-bucket inside an excluded group is excluded, whatever its own switch
  // says. Showing that switch on and enabled would contradict both the table
  // and what Refill All does, so it reads as off and locked, and the stored
  // value is left alone (save omits the field) for when the group comes back.
  // Budget and Refill Factor are a sub-bucket's business. A group owns neither:
  // its budget is the children's total, and with no budget of its own there is
  // nothing for a factor to multiply — so the factor is not offered at all and
  // the budget is shown as the read-only sum it is.
  _syncGroupFields(current) {
    const isGroup = !!current?.is_parent;
    const factorCol = $('bucketFactorCol');
    const budgetCol = $('bucketBudgetCol');
    const budget = $('bucketBudget');
    const help = $('bucketBudgetHelp');
    if (!factorCol || !budgetCol) return;

    factorCol.classList.toggle('d-none', isGroup);
    budgetCol.classList.toggle('col-6', !isGroup);
    budgetCol.classList.toggle('col-12', isGroup);
    if (budget) budget.disabled = isGroup;
    if (help) {
      help.classList.toggle('d-none', !isGroup);
      help.textContent = isGroup
        ? `Total of its ${current.child_count} sub-bucket(s) — set a budget on each one.`
        : '';
    }
  },

  _syncAutoRefill(current) {
    const box = $('bucketAutoRefill');
    const help = $('bucketAutoRefillHelp');
    if (!box) return;
    const group = current?.parent_id &&
      State.buckets.find(x => x.id === current.parent_id);

    box.checked = current?.auto_refill !== false;
    box.disabled = !!group;
    if (help) {
      help.textContent = group
        ? `Set on its group instead — "${group.name}" decides this for every sub-bucket under it.`
        : current?.is_parent
          ? 'Off = Refill All skips this group and every sub-bucket under it. A manual refill still fills them.'
          : 'Off = this bucket is skipped by Refill All and can only be refilled by hand.';
    }
  },

  _fillParentSelect(current) {
    const sel = $('bucketParent');
    // A page served before this field existed (a long-running server still
    // holding the old template, or a cached tab) must not lose the ability to
    // create a bucket — the grouping control is the only thing missing.
    if (!sel) return;
    const options = State.buckets.filter(b =>
      !b.is_settlement && !b.parent_id && b.id !== current?.id);
    sel.innerHTML = '<option value="">— None (top level) —</option>'
      + options.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
    sel.value = current?.parent_id || '';

    // A group has sub-buckets hanging off it; moving it would orphan them.
    const isGroup = !!current?.is_parent;
    sel.disabled = isGroup;
    const help = $('bucketParentHelp');
    if (help) help.textContent = isGroup
      ? `"${current.name}" is a group — move or delete its sub-buckets to file it elsewhere.`
      : "Put this bucket inside a group. The group's budget and balance are its sub-buckets' totals.";
  },

  openNew() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    $('bucketModalTitle').textContent = 'New Bucket';
    $('bucketId').value = '';
    $('bucketName').value = '';
    $('bucketBudget').value = '0';
    $('bucketRefillFactor').value = '1.0';
    this._syncGroupFields(null);
    this._syncAutoRefill(null);
    this._fillParentSelect(null);
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
    this._syncGroupFields(b);
    this._syncAutoRefill(b);
    this._fillParentSelect(b);
    $('bucketForm').classList.remove('was-validated');
    this.modal.show();
  },

  async save() {
    const form = $('bucketForm');
    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const id = $('bucketId').value;
    // 0 is a meaningful factor (budget kept, never auto-refilled), and `|| 1.0`
    // would quietly turn it back into a full refill — only a blank field defaults.
    const factor = parseFloat($('bucketRefillFactor').value);
    // A group is not offered these, so it sends neither: the server keeps what
    // it has rather than taking a reading off a field that was never shown.
    const isGroup = $('bucketFactorCol')?.classList.contains('d-none');
    const payload = {
      name: $('bucketName').value.trim(),
      budget: isGroup ? undefined : parseFloat($('bucketBudget').value) || 0,
      refill_factor: isGroup ? undefined : (Number.isFinite(factor) ? factor : 1.0),
      // Locked means the group decides — omit it so the bucket keeps its own
      // stored preference for when the group is included again.
      auto_refill: $('bucketAutoRefill').disabled
        ? undefined : $('bucketAutoRefill').checked,
      // Omitted entirely when the field is absent, so the server keeps
      // whatever parent the bucket already has rather than clearing it.
      parent_id: $('bucketParent') ? parseInt($('bucketParent').value) || null : undefined,
      acct_id: State.accountId,
    };
    try {
      const res = id
        ? await api('PUT', `/api/buckets/${id}`, payload)
        : await api('POST', '/api/buckets', payload);
      toast(id ? 'Bucket updated' : 'Bucket created');
      // The server moves a would-be group's own money into a "General"
      // sub-bucket. Say so — it is a row the user did not ask for.
      if (res?.converted) {
        toast(`Moved ${res.converted.moved_tx} transaction(s) into "${res.converted.name}"`);
      }
      this.modal.hide();
      await this.load();
      App.refreshBalances();
    } catch (e) { showError(e.message); }
  },

  // Flipping a bucket in or out of "Refill All" is the one bucket setting
  // worth changing without opening the modal, so the Refill Factor cell is the
  // control. The PUT wants the whole bucket, so the unchanged fields ride along.
  async toggleAutoRefill(id) {
    const b = State.buckets.find(x => x.id === id);
    if (!b) return;
    // Sub-buckets follow their group; there is no per-child switch to flip.
    if (b.parent_id) return;
    try {
      await api('PUT', `/api/buckets/${id}`, {
        name: b.name,
        budget: b.budget,
        // null for a group — omitted so the server keeps what it has stored.
        refill_factor: b.refill_factor ?? undefined,
        auto_refill: !b.auto_refill,
      });
      const scope = b.is_parent ? ' and its sub-buckets' : '';
      toast(b.auto_refill
        ? `"${b.name}"${scope} excluded from Refill All — manual refills only`
        : `"${b.name}"${scope} included in Refill All`);
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async refill(id) {
    const b = State.buckets.find(x => x.id === id);
    // refill_amount already sums a group's children, so this one field covers
    // both kinds of row.
    const amount = b?.refill_amount ?? ((b?.budget || 0) * (b?.refill_factor ?? 0));
    if (amount <= 0) {
      showError(b?.is_parent
        ? `"${b.name}" has nothing to refill — none of its sub-buckets has a budget.`
        : `"${b?.name}" has nothing to refill — its refill factor is ${b?.refill_factor ?? 0}.`);
      return;
    }
    const txDate = await confirm(
      b?.is_parent
        ? `Transfer ${fmt(amount)} from Settlement into the ${b.child_count} sub-buckets of "${b.name}"?`
        : `Transfer ${fmt(amount)} from Settlement to "${b?.name}"?`,
      'Refill', false, { date: true });
    if (!txDate) return;
    try {
      const res = await api('POST', `/api/buckets/${id}/refill`, { tx_date: txDate });
      toast(`Refilled ${fmt(res.amount)}`);
      await this.load();
      App.refreshBalances();
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
    const ok = await confirm(
      b?.is_parent
        ? `Archive "${b.name}" and its ${b.child_count} sub-bucket(s)? They will be hidden and frozen — no refills, transfers, or transactions.`
        : `Archive bucket "${b?.name}"? It will be hidden and frozen — no refills, transfers, or transactions.`,
      'Archive', false);
    if (!ok) return;
    try {
      await api('POST', `/api/buckets/${id}/archive`);
      toast('Bucket archived');
      await this.load();
      App.refreshBalances();
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
      App.refreshBalances();
    } catch (e) { showError(e.message); }
  },

  // The archived table is flat, so a sub-bucket names its group to stay
  // distinguishable from a same-named one elsewhere.
  _qualified(b) {
    const parent = b.parent_id && State.archivedBuckets.concat(State.buckets)
      .find(x => x.id === b.parent_id);
    return parent ? `${parent.name} \u203a ${b.name}` : b.name;
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
        <td>${esc(this._qualified(b))}</td>
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
    // auto_refill already carries the group's answer for a sub-bucket.
    const skipped = State.buckets.filter(b =>
      !b.is_settlement && !b.is_parent && !b.auto_refill &&
      b.budget > 0 && b.refill_factor > 0).length;
    const msg = 'Transfer from Settlement into all buckets based on their budget and refill factor?'
      + (skipped ? ` ${skipped} manual-only bucket(s) will be skipped.` : '');
    const txDate = await confirm(msg, 'Refill All', false, { date: true });
    if (!txDate) return;
    try {
      const res = await api('POST', '/api/buckets/refill-all', { acct_id: State.accountId, tx_date: txDate });
      toast(`Refilled ${res.count} bucket(s)`);
      await this.load();
      App.refreshBalances();
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
    $('btnTxPageDeposit').addEventListener('click', () => this.openNew(null, 'deposit'));
    $('btnExportTx').addEventListener('click', () => this.exportCsv());
    $('btnPostAllTx').addEventListener('click', () => this.postAll());
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
      this._pending = 0;
      $('btnPostAllTx').classList.add('d-none');
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
      const html = rows.map((t, i) => {
        const posted = t.posted
          ? `<i class="bi bi-check-circle-fill status-posted" title="Posted" aria-label="Posted" role="img"></i>`
          : `<i class="bi bi-circle status-pending" title="Pending" aria-label="Pending" role="img"></i>`;
        const postBtn = t.posted
          ? `<button class="action-btn action-btn-post" onclick="Transactions.unpost(${t.id})" title="Unpost"><i class="bi bi-x-circle"></i></button>`
          : `<button class="action-btn action-btn-post" onclick="Transactions.post(${t.id})" title="Post"><i class="bi bi-check-circle"></i></button>`;
        // Reordering is only meaningful against another row of the same day,
        // so the spinner appears only where there is one to trade places with.
        const canUp   = i > 0 && rows[i - 1].tx_date === t.tx_date;
        const canDown = i < rows.length - 1 && rows[i + 1].tx_date === t.tx_date;
        const spinner = (canUp || canDown)
          ? `<span class="tx-move">
               <button onclick="Transactions.move(${t.id}, 'up')" title="Move up"${canUp ? '' : ' disabled'}><i class="bi bi-chevron-up"></i></button>
               <button onclick="Transactions.move(${t.id}, 'down')" title="Move down"${canDown ? '' : ' disabled'}><i class="bi bi-chevron-down"></i></button>
             </span>`
          : '';
        return `
        <tr class="${t.posted ? '' : 'table-row-pending'}">
          <td class="text-muted font-monospace tx-date-cell" style="font-size:.82rem">${t.tx_date}${spinner}</td>
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

    // The count comes from the server, not the loaded rows — paging means the
    // page may be showing 20 of 200, and "Post All" posts all 200.
    this._pending = data.pending || 0;
    const postAllBtn = $('btnPostAllTx');
    if (this._pending) {
      postAllBtn.classList.remove('d-none');
      postAllBtn.title = `Post all ${this._pending} pending transaction(s)`;
    } else {
      postAllBtn.classList.add('d-none');
    }
  },

  _pending: 0,

  async postAll() {
    if (!State.accountId) { showError('Please select an account first.'); return; }
    if (!this._pending) { toast('Nothing pending to post'); return; }
    const ok = await confirm(
      `Post all ${this._pending} pending transaction(s) in this account?`, 'Post All', false);
    if (!ok) return;
    try {
      const res = await api('POST', '/api/transactions/post-all', { acct_id: State.accountId });
      toast(`Posted ${res.count} transaction(s)`);
      await this.load();
      await Buckets.load();
      App.refreshBalances();
    } catch (e) { showError(e.message); }
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
      if (State.currentSection === 'buckets' || State.currentSection === 'transactions') await Buckets.load();
      if (State.currentSection === 'bucket-detail') await BucketDetail.load(State.currentBucketId);
      App.refreshBalances();
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
      if (State.currentSection === 'buckets' || State.currentSection === 'transactions') await Buckets.load();
      if (State.currentSection === 'bucket-detail') await BucketDetail.load(State.currentBucketId);
      Trash.refreshBadge();
    } catch (e) { showError(e.message); }
  },

  async move(id, direction) {
    try {
      await api('POST', `/api/transactions/${id}/move`, { direction });
      // Reordering leaves every total untouched, but it does change the
      // running balance each row is shown against, so the list is refetched.
      await this.load();
    } catch (e) { showError(e.message); }
  },

  async post(id) {
    try {
      await api('POST', `/api/transactions/${id}/post`);
      toast('Transaction posted');
      await this.load();
      if (State.currentSection === 'buckets' || State.currentSection === 'transactions') await Buckets.load();
    } catch (e) { showError(e.message); }
  },

  async unpost(id) {
    try {
      await api('POST', `/api/transactions/${id}/unpost`);
      toast('Transaction unposted');
      await this.load();
      if (State.currentSection === 'buckets' || State.currentSection === 'transactions') await Buckets.load();
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
        ? `<i class="bi bi-check-circle-fill status-posted" title="Posted" aria-label="Posted" role="img"></i>`
        : `<i class="bi bi-circle status-pending" title="Pending" aria-label="Pending" role="img"></i>`;
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
  // Every account's balance is the landing view. Accounts.load() also keeps the
  // sidebar selector in sync, so this doubles as the first-paint data load.
  async load() {
    try {
      await Accounts.load();
    } catch (e) { showError(e.message); return; }

    // With a single account there is nothing to choose — make it the active one
    // so Buckets and Transactions work without a detour. The overview still shows.
    if (!State.accountId && State.accounts.length === 1) {
      State.accountId = State.accounts[0].id;
      $('accountSelect').value = State.accountId;
    }
    this.render();
  },

  // One tile per account, plus the total of them all.
  render() {
    const accounts = State.accounts;
    const total = accounts.reduce((sum, a) => sum + (parseFloat(a.balance) || 0), 0);

    $('netWorthCount').textContent = `${accounts.length} account${accounts.length === 1 ? '' : 's'}`;
    const totalEl = $('netWorthValue');
    totalEl.textContent = fmt(total);
    totalEl.className = `net-worth-value ${balClass(total)}`;

    $('netWorthRow').classList.toggle('d-none', accounts.length === 0);
    $('accountsOverviewEmpty').classList.toggle('d-none', accounts.length > 0);

    // The add tile sits in the grid rather than the header — it belongs with
    // the things it creates, and the header stays uncluttered as actions grow.
    const addTile = `
      <div class="account-tile account-tile--add" role="button" tabindex="0"
           data-acct-new="1">
        <span class="account-tile-add-icon"><i class="bi bi-plus-lg"></i></span>
        <span class="account-tile-add-text">New account</span>
      </div>`;

    // Tiles are divs, not buttons: they are drag handles as well as links, and
    // the click that opens one is delegated so a drag can cancel it.
    $('accountTiles').innerHTML = accounts.map(a => `
      <div class="account-tile acct-hue-${this.hue(a.id)}${a.id === State.accountId ? ' active' : ''}"
           role="button" tabindex="0" data-acct-id="${a.id}">
        <span class="account-tile-grip" title="Drag to reorder"><i class="bi bi-grip-vertical"></i></span>
        <span class="account-tile-name">${esc(a.name)}</span>
        <span class="account-tile-balance ${balClass(a.balance)}">${fmt(a.balance)}</span>
        <span class="account-tile-cta">View buckets <i class="bi bi-arrow-right"></i></span>
      </div>`).join('') + (accounts.length ? addTile : '');
  },

  // Colour follows the account, never its position — eight fixed hues keyed by
  // id, so dragging the grid around never repaints a tile the user has learnt.
  // The name is always on the tile, so the hue is decoration, never the label.
  hue(id) { return id % 8; },

  // A tile is a drill-down into that account's buckets — the per-account view.
  openAccount(id) {
    State.accountId = id;
    $('accountSelect').value = id;
    App.nav('buckets');
  },

  // ---- Tile click + drag-to-reorder -------------------------------------
  // Pointer events rather than HTML5 drag-and-drop: the same code then covers
  // mouse and touch, where dragstart never fires.
  _drag: null,
  _draggedAt: 0,

  initTiles() {
    const grid = $('accountTiles');

    grid.addEventListener('click', e => {
      // A drag ends in a click on the tile that was moved; swallow that one
      // rather than opening the account. Time-bounded, so a click that never
      // comes cannot leave the next real one disarmed.
      if (Date.now() - this._draggedAt < 300) { this._draggedAt = 0; return; }
      if (e.target.closest('.account-tile-grip')) return;
      const tile = e.target.closest('.account-tile');
      if (!tile) return;
      if (tile.dataset.acctNew) Accounts.openNew();
      else this.openAccount(parseInt(tile.dataset.acctId));
    });

    grid.addEventListener('keydown', e => {
      if (e.key !== 'Enter' && e.key !== ' ') return;
      const tile = e.target.closest('.account-tile');
      if (!tile) return;
      e.preventDefault();
      if (tile.dataset.acctNew) Accounts.openNew();
      else this.openAccount(parseInt(tile.dataset.acctId));
    });

    grid.addEventListener('pointerdown', e => {
      if (e.button !== 0 || !e.target.closest('.account-tile-grip')) return;
      const tile = e.target.closest('.account-tile[data-acct-id]');
      if (tile) this._dragStart(tile, e);
    });
  },

  _dragStart(tile, e) {
    e.preventDefault();
    const grid = $('accountTiles');
    this._drag = { tile, order: this._tileOrder(), moved: false };
    tile.classList.add('dragging');
    grid.classList.add('sorting');

    // The tile is reordered in place rather than lifted out of the flow, so
    // the grid shows its own result as the pointer crosses each neighbour.
    const onMove = ev => this._dragMove(ev);
    const onEnd = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
      this._dragEnd();
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
  },

  _dragMove(e) {
    const d = this._drag;
    if (!d) return;
    // The whole stack under the cursor, so the tile being dragged — which is
    // still sitting in the grid — does not mask the neighbour beneath it.
    const over = document.elementsFromPoint(e.clientX, e.clientY)
      .map(el => el.closest?.('.account-tile[data-acct-id]'))
      .find(t => t && t !== d.tile);
    if (!over) return;
    const r = over.getBoundingClientRect();
    const after = e.clientX - r.left > r.width / 2;
    const ref = after ? over.nextSibling : over;
    if (ref === d.tile) return;   // already there — not a move
    over.parentNode.insertBefore(d.tile, ref);
    d.moved = true;
  },

  async _dragEnd() {
    const d = this._drag;
    this._drag = null;
    if (!d) return;
    d.tile.classList.remove('dragging');
    $('accountTiles').classList.remove('sorting');
    if (d.moved) this._draggedAt = Date.now();

    const ids = this._tileOrder();
    if (ids.join() === d.order.join()) return;

    // Keep State in the new order so any repaint — the sidebar selector
    // included — agrees with the screen before the save even returns. If a
    // refresh changed the account list mid-drag, the screen is stale: reload
    // rather than save an order built from accounts that have since moved.
    const byId = new Map(State.accounts.map(a => [a.id, a]));
    if (ids.length !== byId.size || !ids.every(id => byId.has(id))) {
      this.load();
      return;
    }
    State.accounts = ids.map(id => byId.get(id));
    Accounts.populateSelector();
    try {
      await api('POST', '/api/accounts/reorder', { ids });
    } catch (e) {
      showError(e.message);
      this.load();
    }
  },

  _tileOrder() {
    return [...$('accountTiles').querySelectorAll('.account-tile[data-acct-id]')]
      .map(el => parseInt(el.dataset.acctId));
  },
};

// ============================================================
// Navigation
// ============================================================
const SECTIONS = ['dashboard', 'buckets', 'transactions', 'bucket-detail', 'iou', 'trash'];
const SECTION_LABELS = {
  dashboard: 'Dashboard',
  buckets: 'Buckets',
  transactions: 'Transactions',
  'bucket-detail': 'Bucket',
  iou: 'IOUs',
  trash: 'Deleted Transactions',
};

const App = {
  // Anything that moves money refreshes whichever balance view is on screen.
  // The Buckets page refreshes through Buckets.load(), which every money-moving
  // path already calls.
  refreshBalances() {
    if (State.currentSection === 'dashboard') Dashboard.load();
  },

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

    // Account selector change. Clearing it leaves every per-account section
    // with nothing to show, so the placeholder goes back to the dashboard.
    $('accountSelect').addEventListener('change', async () => {
      const val = $('accountSelect').value;
      State.accountId = val ? parseInt(val) : null;
      this.nav(val ? State.currentSection : 'dashboard');
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

    Dashboard.initTiles();

    $('btnDashRefresh').addEventListener('click', () => Dashboard.load());

    // Initial data load — Dashboard.load() pulls the accounts it lists.
    this.nav('dashboard');
    Trash.refreshBadge();
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





// ============================================================
// Receipt scanning
// ------------------------------------------------------------
// Upload a receipt, review a proposed breakdown, commit it.
//
// The table shows a row per bucket with its line items nested underneath.
// Each item carries its own description, pre-tax amount, tax share and bucket;
// a bucket's header row is the sum of its items. What gets SAVED is still one
// transaction per bucket — forty warehouse-store rows is not useful ledger
// history — but the items are what you edit to get there.
//
// Tax is allocated across the lines the receipt marked taxable, in integer
// cents by largest remainder, so the shares add up to the tax on the bill
// exactly. Any share can be typed over; a readout says whether the total still
// matches. The toggle turns allocation off and puts tax on its own line.
//
// Nothing reaches the database until "Add transactions" is pressed, and that
// call is all-or-nothing on the server.
// ============================================================
const Receipt = {
  modal: null,
  items: [],          // line items — the editable detail
  parsed: null,       // the server's proposal, kept so toggling is free
  spreadTax: true,
  expanded: {},       // bucket_id -> items shown
  printedTotal: null,

  async init() {
    // The button stays hidden unless the server says AI is configured, so a
    // deployment without an API key shows no trace of the feature.
    let status;
    try {
      status = await api('GET', '/api/ai/status');
    } catch { return; }
    if (!status.enabled) return;

    $('btnScanReceipt').classList.remove('d-none');
    this.modal = new bootstrap.Modal($('receiptModal'));

    $('btnScanReceipt').addEventListener('click', () => this.open());
    $('btnReceiptParse').addEventListener('click', () => this.parse());
    $('btnReceiptCommit').addEventListener('click', () => this.commit());
    $('btnReceiptBack').addEventListener('click', () => this.showUpload());
    $('btnReceiptAddRow').addEventListener('click', () => this.addRow());
    $('btnReceiptRetax').addEventListener('click', () => { this.applyTax(); this.render(); });
    $('receiptFiles').addEventListener('change', () => this.preview());
    $('receiptSpreadTax').addEventListener('change', e => {
      // Rebuilding from the stored proposal keeps this instant and free —
      // flipping the toggle must never re-call the API.
      this.spreadTax = e.target.checked;
      this.applyTax();
      this.render();
    });
    $('receiptMerchant').addEventListener('input', () => {
      if (this.items.length) this.recalc();
    });
  },

  open() {
    if (!State.accountId) { showError('Pick an account first.'); return; }
    if (!State.buckets.filter(b => !b.is_settlement && !b.is_parent).length) {
      showError('Add a spending bucket to this account first.');
      return;
    }
    $('receiptFiles').value = '';
    $('receiptPreview').innerHTML = '';
    this.items = [];
    this.parsed = null;
    this.taxTouched = false;
    this.expanded = {};
    this.printedTotal = null;
    this.showUpload();
    this.modal.show();
  },

  showUpload() {
    $('receiptStepUpload').classList.remove('d-none');
    $('receiptStepReview').classList.add('d-none');
    $('btnReceiptParse').classList.remove('d-none');
    $('btnReceiptCommit').classList.add('d-none');
    $('btnReceiptBack').classList.add('d-none');
    $('receiptUploadError').classList.add('d-none');
  },

  showReview() {
    $('receiptStepUpload').classList.add('d-none');
    $('receiptStepReview').classList.remove('d-none');
    $('btnReceiptParse').classList.add('d-none');
    $('btnReceiptCommit').classList.remove('d-none');
    $('btnReceiptBack').classList.remove('d-none');
  },

  preview() {
    const box = $('receiptPreview');
    box.innerHTML = '';
    [...$('receiptFiles').files].slice(0, 5).forEach(f => {
      const img = document.createElement('img');
      img.src = URL.createObjectURL(f);
      img.className = 'rounded border';
      img.style.cssText = 'height:90px;width:auto;object-fit:cover';
      img.onload = () => URL.revokeObjectURL(img.src);
      box.appendChild(img);
    });
  },

  // ---- money -------------------------------------------------------------

  // Spread tax proportionally over the taxable lines, in integer cents.
  // Proportional shares almost never divide evenly, so take the floor and hand
  // out the leftover pennies by largest fractional remainder — that is what
  // makes the shares add up to the tax printed on the bill exactly, rather
  // than drifting a cent or two from what the card was charged.
  allocateTax(lineCents, taxableFlags, taxCents) {
    const shares = lineCents.map(() => 0);
    if (taxCents <= 0) return shares;

    let flags = taxableFlags;
    let base = lineCents.reduce((s, c, i) => s + (flags[i] && c > 0 ? c : 0), 0);

    // No usable tax markers on the receipt. Dropping the tax would understate
    // every bucket, so fall back to spreading across all positive lines.
    if (base <= 0) {
      flags = lineCents.map(c => c > 0);
      base = lineCents.reduce((s, c) => s + (c > 0 ? c : 0), 0);
    }
    if (base <= 0) return shares;

    const remainders = [];
    lineCents.forEach((c, i) => {
      if (!flags[i] || c <= 0) return;
      const exact = taxCents * c / base;
      shares[i] = Math.floor(exact);
      remainders.push([exact - shares[i], i]);
    });

    const leftover = taxCents - shares.reduce((s, v) => s + v, 0);
    remainders.sort((a, b) => b[0] - a[0]);
    for (let k = 0; k < leftover && remainders.length; k++) {
      shares[remainders[k % remainders.length][1]] += 1;
    }
    return shares;
  },

  // (Re)compute every item's tax share. Called on parse, when the toggle flips,
  // and from "Recalculate" — never silently on an edit, because that would
  // quietly overwrite a share the user had already typed in.
  applyTax() {
    const billTaxCents = Math.round(-(this.parsed && this.parsed.tax || 0) * 100);

    // Drop any previous synthetic tax line before deciding what to do.
    this.items = this.items.filter(l => !l._taxLine);

    if (!this.spreadTax) {
      this.items.forEach(l => { l.tax = 0; });
      if (billTaxCents > 0) {
        const biggest = this.items.find(l => l.bucket_id && l.amount < 0);
        this.items.push({
          description: 'Sales tax',
          amount: this.parsed.tax,
          tax: 0,
          bucket_id: biggest ? biggest.bucket_id : null,
          include: true,
          _taxLine: true,
        });
      }
      return;
    }

    const live = this.items.filter(l => l.include !== false);
    const cents = live.map(l => Math.round(-l.amount * 100));
    const flags = live.map(l => !!l.taxable);
    const anyTaxable = flags.some((f, i) => f && cents[i] > 0);

    // allocateTax falls back to "spread over everything" when nothing is
    // marked, which is right for a receipt that printed no tax codes but wrong
    // once the user has unticked boxes on purpose. Honour the empty selection
    // and let the readout show that the tax no longer matches the bill.
    const shares = (!anyTaxable && this.taxTouched)
      ? cents.map(() => 0)
      : this.allocateTax(cents, flags, billTaxCents);

    this.items.forEach(l => { l.tax = 0; });
    live.forEach((l, i) => { l.tax = -shares[i] / 100; });
  },

  taxTotal() {
    return Math.round(this.items
      .filter(l => l.include !== false)
      .reduce((s, l) => s + (l.tax || 0), 0) * 100) / 100;
  },

  groups() {
    const map = new Map();
    this.items.filter(l => l.include !== false).forEach(l => {
      const key = l.bucket_id || 0;
      if (!map.has(key)) map.set(key, { bucket_id: l.bucket_id || null, items: [], subtotal: 0, tax: 0, total: 0 });
      const g = map.get(key);
      g.items.push(l);
      g.subtotal = Math.round((g.subtotal + l.amount) * 100) / 100;
      g.tax = Math.round((g.tax + (l.tax || 0)) * 100) / 100;
      g.total = Math.round((g.total + l.amount + (l.tax || 0)) * 100) / 100;
    });
    const out = [...map.values()];
    out.sort((a, b) => {
      if (!a.bucket_id) return -1;          // unassigned first — it blocks commit
      if (!b.bucket_id) return 1;
      return Math.abs(b.total) - Math.abs(a.total);
    });
    return out;
  },

  // ---- parsing -----------------------------------------------------------

  async parse() {
    const files = [...$('receiptFiles').files];
    if (!files.length) { showError('Choose a receipt image first.'); return; }

    const btn = $('btnReceiptParse');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Reading…';
    $('receiptUploadError').classList.add('d-none');

    const fd = new FormData();
    fd.append('acct_id', State.accountId);
    files.slice(0, 5).forEach(f => fd.append('images', f));

    try {
      const r = await fetch('/api/ai/parse-receipt', { method: 'POST', body: fd });
      if (r.status === 401) { window.location.href = '/login'; return; }
      const data = await r.json();
      if (!r.ok) throw new Error(data.error || 'Could not read the receipt.');
      if (!data.lines || !data.lines.length) throw new Error('No line items found on that image.');

      this.parsed = data;
      this.printedTotal = data.printed_total;
      this.expanded = {};
      // Nothing on the receipt was marked taxable, so spreading could only
      // guess. Start with tax on its own line and let the user opt in.
      if (!data.has_tax_codes) this.spreadTax = false;
      $('receiptSpreadTax').checked = this.spreadTax;
      $('receiptMerchant').value = data.merchant || '';
      $('receiptDate').value = data.purchase_date || today();

      this.items = data.lines.map(l => ({
        description: l.description,
        amount: l.amount,                 // printed price, negative for a charge
        tax: 0,
        taxable: !!l.taxable,
        tax_code: l.tax_code || '',
        bucket_id: l.bucket_id,
        include: true,
      }));
      this.applyTax();

      const disc = $('receiptDiscrepancy');
      if (data.discrepancy) {
        // The transcription didn't add up to the printed total. Surfaced, not
        // blocked — the running total updates as the table is corrected.
        disc.textContent =
          `The receipt reads ${fmt(data.printed_total)} but the transcribed items and tax `
          + `come to ${fmt(data.computed_total)} — a difference of `
          + `${fmt(Math.abs(data.discrepancy))}. Check for a missed or misread line.`;
        disc.classList.remove('d-none');
      } else {
        disc.classList.add('d-none');
      }

      this.render();
      this.showReview();
    } catch (e) {
      $('receiptUploadError').textContent = e.message;
      $('receiptUploadError').classList.remove('d-none');
    } finally {
      btn.disabled = false;
      btn.innerHTML = original;
    }
  },

  addRow() {
    this.items.push({
      description: '', amount: 0, tax: 0, taxable: false,
      bucket_id: null, include: true, _manual: true,
    });
    this.expanded[0] = true;
    this.render();
    const inputs = $('receiptLines').querySelectorAll('[data-ri="desc"]');
    if (inputs.length) inputs[inputs.length - 1].focus();
  },

  // ---- rendering ---------------------------------------------------------

  render() {
    const opts = Buckets.bucketOptions(
      State.buckets.filter(b => !b.is_settlement && !b.archived));
    const named = id => {
      const b = State.buckets.find(x => x.id === id);
      return b ? b.name : 'Needs a bucket';
    };

    $('receiptLines').innerHTML = this.groups().map(g => {
      const key = g.bucket_id || 0;
      const open = this.expanded[key] !== false;   // expanded by default
      const rows = g.items.map(l => {
        const i = this.items.indexOf(l);
        return `
        <tr class="receipt-item">
          <td class="ps-4">
            <input type="text" class="form-control form-control-sm"
                   placeholder="Description"
                   data-ri="desc" data-i="${i}" value="${esc(l.description)}">
          </td>
          <td>
            <input type="number" step="0.01"
                   class="form-control form-control-sm text-end font-monospace"
                   data-ri="amt" data-i="${i}" value="${Math.abs(l.amount).toFixed(2)}">
          </td>
          <td>
            <div class="d-flex align-items-center gap-1">
              <input type="checkbox" class="form-check-input mt-0" data-ri="taxable" data-i="${i}"
                     ${l.taxable ? 'checked' : ''}
                     title="${l.tax_code ? 'Receipt tax code: ' + esc(l.tax_code) : 'No tax code printed on this line'}">
              <input type="number" step="0.01"
                     class="form-control form-control-sm text-end font-monospace flex-grow-1"
                     style="min-width:4.5rem"
                     data-ri="tax" data-i="${i}" ${l.taxable ? '' : 'disabled'}
                     value="${Math.abs(l.tax || 0).toFixed(2)}">
            </div>
          </td>
          <td>
            <select class="form-select form-select-sm" data-ri="bkt" data-i="${i}">
              <option value="">— pick —</option>${opts}
            </select>
          </td>
          <td class="text-center">
            <button class="btn btn-sm btn-link text-danger p-0" data-ri="del" data-i="${i}"
                    title="Remove this item"><i class="bi bi-x-lg"></i></button>
          </td>
        </tr>`;
      }).join('');

      return `
      <tr class="table-active ${g.bucket_id ? '' : 'table-warning'}">
        <td>
          <button class="btn btn-sm btn-link p-0 text-decoration-none fw-semibold"
                  data-ri="toggle" data-key="${key}">
            <i class="bi bi-chevron-${open ? 'down' : 'right'} me-1"></i>${esc(named(g.bucket_id))}
          </button>
          <span class="text-muted small ms-1">${g.items.length} item${g.items.length > 1 ? 's' : ''}</span>
        </td>
        <td class="text-end font-monospace small text-muted">${fmt(Math.abs(g.subtotal))}</td>
        <td class="text-end font-monospace small text-muted">${fmt(Math.abs(g.tax))}</td>
        <td class="text-end fw-bold font-monospace" colspan="2">${fmt(g.total)}</td>
      </tr>
      ${open ? rows : ''}`;
    }).join('');

    this.items.forEach((l, i) => {
      const sel = $('receiptLines').querySelector(`[data-ri="bkt"][data-i="${i}"]`);
      if (sel) sel.value = l.bucket_id || '';
    });

    $('receiptLines').querySelectorAll('[data-ri]').forEach(el => {
      const ev = el.tagName === 'BUTTON' ? 'click'
               : el.dataset.ri === 'bkt' ? 'change' : 'input';
      el.addEventListener(ev, e => this.onEdit(e));
    });

    this.recalc();
  },

  onEdit(e) {
    const el = e.target.closest('[data-ri]');
    if (!el) return;
    const kind = el.dataset.ri;

    if (kind === 'toggle') {
      const key = parseInt(el.dataset.key);
      this.expanded[key] = this.expanded[key] === false;
      this.render();
      return;
    }

    const i = parseInt(el.dataset.i);
    const l = this.items[i];
    if (!l) return;

    switch (kind) {
      case 'desc':
        l.description = el.value;
        return;                        // no re-render; it would drop focus
      case 'amt': {
        // Shown as a magnitude, stored negative — spending reduces a bucket
        // balance. A row keeps whichever direction it had, so an edit can't
        // silently turn a refund into a charge; type a leading minus to flip.
        const v = Math.abs(parseFloat(el.value) || 0);
        l.amount = el.value.trim().startsWith('-') ? v
                 : (l.amount > 0 ? 1 : -1) * v;
        this.refreshTotals();
        return;
      }
      case 'taxable':
        // Changing which lines are taxable changes the basis of the split, so
        // the tax is redistributed across whatever is still ticked — that is
        // what keeps the shares adding up to the bill. It does overwrite a
        // hand-typed share, which is why an amount edit deliberately doesn't.
        l.taxable = el.checked;
        this.taxTouched = true;
        this.applyTax();
        this.render();
        return;
      case 'tax':
        l.tax = -Math.abs(parseFloat(el.value) || 0);
        this.refreshTotals();
        return;
      case 'bkt':
        l.bucket_id = el.value ? parseInt(el.value) : null;
        this.render();                 // regroup under the new bucket
        return;
      case 'del':
        this.items.splice(i, 1);
        this.render();
        return;
    }
  },

  // Update the bucket subtotals in place. A full re-render on every keystroke
  // would steal focus from the field being typed into.
  refreshTotals() {
    const cells = $('receiptLines').querySelectorAll('tr.table-active');
    const groups = this.groups();
    cells.forEach((tr, idx) => {
      const g = groups[idx];
      if (!g) return;
      const tds = tr.querySelectorAll('td');
      tds[1].textContent = fmt(Math.abs(g.subtotal));
      tds[2].textContent = fmt(Math.abs(g.tax));
      tds[3].textContent = fmt(g.total);
    });
    this.recalc();
  },

  recalc() {
    const groups = this.groups();
    const usable = groups.filter(g => g.bucket_id && g.total !== 0);
    const total = groups.reduce((s, g) => s + g.total, 0);
    const subtotal = groups.reduce((s, g) => s + g.subtotal, 0);
    const taxSum = groups.reduce((s, g) => s + g.tax, 0);
    $('receiptTotal').textContent = fmt(Math.abs(total));
    $('receiptBreakdown').textContent = taxSum
      ? `items ${fmt(Math.abs(subtotal))} + tax ${fmt(Math.abs(taxSum))}`
      : `items ${fmt(Math.abs(subtotal))}`;
    $('receiptCount').textContent =
      `${usable.length} transaction${usable.length === 1 ? '' : 's'} from `
      + `${this.items.filter(l => l.include !== false).length} items`;

    // Does the tax spread across the items still equal the tax on the bill?
    const tn = $('receiptTaxNote');
    const billTax = this.parsed ? Math.abs(this.parsed.tax || 0) : 0;
    $('btnReceiptRetax').classList.toggle('d-none', !this.spreadTax || !billTax);
    if (this.spreadTax && billTax) {
      const applied = Math.abs(this.taxTotal());
      const exact = Math.abs(applied - billTax) < 0.005;
      const ticked = this.items.filter(l => l.include !== false && l.taxable).length;
      tn.textContent = exact
        ? `${fmt(billTax)} tax spread across ${ticked} taxable item`
          + `${ticked === 1 ? '' : 's'} — matches the bill exactly.`
        : `${ticked} taxable item${ticked === 1 ? '' : 's'} carry ${fmt(applied)} `
          + `of tax but the bill says ${fmt(billTax)} `
          + `(off by ${fmt(Math.abs(applied - billTax))}).`;
      tn.className = exact ? 'form-text small mb-0 text-success'
                           : 'form-text small mb-0 text-danger';
    } else if (billTax) {
      tn.textContent = `${fmt(billTax)} tax is on its own line.`;
      tn.className = 'form-text small mb-0 text-muted';
    } else {
      tn.textContent = 'No sales tax was read from this receipt.';
      tn.className = 'form-text small mb-0 text-muted';
    }

    // How the running total compares to the receipt, live as rows are edited.
    const cmp = $('receiptCompare');
    if (this.printedTotal) {
      const diff = Math.round((Math.abs(total) - Math.abs(this.printedTotal)) * 100) / 100;
      cmp.textContent = diff
        ? `receipt ${fmt(this.printedTotal)} · off by ${fmt(Math.abs(diff))}`
        : `matches receipt ${fmt(this.printedTotal)}`;
      cmp.className = diff ? 'small text-danger' : 'small text-success';
    } else {
      cmp.textContent = '';
    }

    // Client-side preview of the server's overdraft rule, so a shortfall shows
    // while it can still be fixed rather than as a rejected commit.
    const short = [];
    groups.forEach(g => {
      if (!g.bucket_id || g.total >= 0) return;
      const b = State.buckets.find(x => x.id === g.bucket_id);
      if (b && parseFloat(b.balance) + g.total < -0.004) {
        short.push(`${b.name} is short ${fmt(Math.abs(parseFloat(b.balance) + g.total))}`);
      }
    });

    const msgs = [];
    const orphan = groups.find(g => !g.bucket_id);
    if (orphan) {
      msgs.push(`${orphan.items.length} item${orphan.items.length > 1 ? 's' : ''} `
                + `still need a bucket.`);
    }
    if (short.length) {
      msgs.push(`Not enough balance: ${short.join('; ')}. Refill or reassign first.`);
    }

    const el = $('receiptShortfall');
    el.innerHTML = msgs.join('<br>');
    el.classList.toggle('d-none', !msgs.length);
    $('btnReceiptCommit').disabled = !usable.length || !!msgs.length;
    $('btnReceiptCommit').innerHTML =
      `<i class="bi bi-check2-circle me-1"></i>Add ${usable.length} `
      + `transaction${usable.length === 1 ? '' : 's'}`;
  },

  // Item names become the transaction note, capped so a forty-item warehouse
  // run doesn't blow out the transaction table's layout.
  describe(items) {
    const MAX = 180;
    const names = items.map(l => (l.description || '').trim()).filter(Boolean);
    const kept = [];
    let len = 0;
    for (const n of names) {
      if (len + n.length + 2 > MAX) break;
      kept.push(n);
      len += n.length + 2;
    }
    if (!kept.length && names.length) kept.push(names[0]);
    const rest = names.length - kept.length;
    return kept.join(', ') + (rest > 0 ? ` +${rest} more` : '');
  },

  async commit() {
    const groups = this.groups().filter(g => g.bucket_id && g.total !== 0);
    if (!groups.length) { showError('Nothing to add.'); return; }

    const merchant = $('receiptMerchant').value.trim();
    const btn = $('btnReceiptCommit');
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Adding…';

    try {
      const res = await api('POST', '/api/transactions/batch', {
        tx_date: $('receiptDate').value || today(),
        lines: groups.map(g => {
          const detail = this.describe(g.items);
          return {
            bucket_id: g.bucket_id,
            amount: g.total,
            note: merchant ? `${merchant} — ${detail}`.trim() : detail,
          };
        }),
      });
      this.modal.hide();
      toast(`Added ${res.count} transaction${res.count > 1 ? 's' : ''}.`);
      Buckets.load();
      Transactions.load();
    } catch (e) {
      showError(e.message);
    } finally {
      btn.disabled = false;
      btn.innerHTML = original;
    }
  },
};

// Boot
document.addEventListener('DOMContentLoaded', () => { App.init(); IdleTimer.init(); Receipt.init(); });
