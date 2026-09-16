/* HealthConnect Pharmacist Module — Next-Gen App Controller */

const API_BASE = '/api';
let authToken = localStorage.getItem('hc_pharmacist_token');
let currentUser = null;
let currentTab = 'dashboard';
let currentAlertFilter = 'all';
let currentPrescStatus = 'Pending';
let allMedicinesCache = [];

// Initialize App & Theme Engine
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  setupEventListeners();

  if (authToken) {
    verifySession();
  } else {
    showAuthScreen();
  }
});

/* Monochrome glass theme engine */
function initTheme() {
  const savedTheme = localStorage.getItem('hc_theme') || 'light';
  applyTheme(savedTheme);
}

function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
  applyTheme(newTheme);
  localStorage.setItem('hc_theme', newTheme);
}

function applyTheme(theme) {
  if (theme === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark');
    const toggleBtn = document.getElementById('btnThemeToggle');
    if (toggleBtn) {
      toggleBtn.textContent = 'Light';
      toggleBtn.setAttribute('aria-label', 'Switch to light mode');
      toggleBtn.setAttribute('aria-pressed', 'true');
    }
  } else {
    document.documentElement.removeAttribute('data-theme');
    const toggleBtn = document.getElementById('btnThemeToggle');
    if (toggleBtn) {
      toggleBtn.textContent = 'Dark';
      toggleBtn.setAttribute('aria-label', 'Switch to dark mode');
      toggleBtn.setAttribute('aria-pressed', 'false');
    }
  }
}

/* ==========================================================================
   AUTH & SESSION MANAGEMENT & VENGEANCE-UI SPOTLIGHT NAVBAR
   ========================================================================== */

function setupEventListeners() {
  // Login form submit
  const loginForm = document.getElementById('loginForm');
  if (loginForm) loginForm.addEventListener('submit', handleLogin);

  const quickFill = document.getElementById('btnQuickFillDemo');
  if (quickFill) {
    quickFill.addEventListener('click', (e) => {
      e.preventDefault();
      const emailEl = document.getElementById('loginEmail');
      const passEl = document.getElementById('loginPassword');
      if (emailEl) emailEl.value = 'pharmacist@healthconnect.com';
      if (passEl) passEl.value = 'password123';
    });
  }

  // Logout buttons (nav & modal)
  ['btnLogout', 'btnLogoutModal', 'btnLogoutNav'].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.addEventListener('click', handleLogout);
  });

  // Tab navigation buttons
  document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTab = btn.getAttribute('data-tab');
      switchTab(targetTab);
    });
  });

  // VENGEANCE-UI SPOTLIGHT NAVBAR CURSOR TRACKING
  const navLinks = document.querySelector('.nav-links');
  if (navLinks) {
    navLinks.addEventListener('mousemove', (e) => {
      const rect = navLinks.getBoundingClientRect();
      const x = e.clientX - rect.left;
      navLinks.style.setProperty('--spotlight-x', `${x}px`);
      navLinks.style.setProperty('--spotlight-opacity', '1');
    });
    navLinks.addEventListener('mouseleave', () => {
      navLinks.style.setProperty('--spotlight-opacity', '0');
    });
  }

  // Medicine live search input enter press & input event
  const searchInput = document.getElementById('medSearchInput');
  if (searchInput) {
    searchInput.addEventListener('keyup', (e) => {
      if (e.key === 'Enter') performMedicineSearch();
    });
    searchInput.addEventListener('input', () => {
      if (searchInput.value.trim() === '') {
        clearMedicineSearch();
      }
    });
  }

  // Add Medicine Form submit
  const addMedForm = document.getElementById('addMedicineForm');
  if (addMedForm) addMedForm.addEventListener('submit', handleAddMedicineSubmit);

  // Restock Form submit
  const restockForm = document.getElementById('restockForm');
  if (restockForm) restockForm.addEventListener('submit', handleRestockSubmit);

  // Change Password Form submit
  const changePassForm = document.getElementById('changePasswordForm') || document.getElementById('panelChangePasswordForm');
  if (changePassForm) changePassForm.addEventListener('submit', handlePanelChangePasswordSubmit);

  // Update Profile Form submit
  const profileForm = document.getElementById('panelUpdateProfileForm');
  if (profileForm) profileForm.addEventListener('submit', handlePanelUpdateProfileSubmit);

  // Close profile dropdown menu when clicking outside
  document.addEventListener('click', (e) => {
    const dropdown = document.getElementById('profileDropdownMenu');
    const trigger = document.getElementById('btnProfileDropdown');
    if (dropdown && trigger && !trigger.contains(e.target) && !dropdown.contains(e.target)) {
      closeProfileDropdown();
    }
  });
}

async function handleLogin(e) {
  e.preventDefault();
  const email = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;

  try {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    const data = await res.json();

    if (!res.ok || !data.success) {
      showToast(data.message || 'Login failed', 'error');
      return;
    }

    authToken = data.token;
    localStorage.setItem('hc_pharmacist_token', authToken);
    currentUser = data.user;

    showToast(`Welcome back, ${currentUser.name}!`, 'success');
    showAppScreen();
    switchTab('dashboard');
  } catch (err) {
    showToast('Network error during authentication. Please check server status.', 'error');
  }
}

async function verifySession() {
  try {
    const res = await fetch(`${API_BASE}/auth/me`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    const data = await res.json();
    if (res.ok && data.success) {
      currentUser = data.user;
      showAppScreen();
      switchTab('dashboard');
    } else {
      handleLogout();
    }
  } catch (err) {
    handleLogout();
  }
}

function handleLogout() {
  authToken = null;
  currentUser = null;
  localStorage.removeItem('hc_pharmacist_token');
  showAuthScreen();
  showToast('Logged out successfully.', 'info');
}

function showAuthScreen() {
  document.getElementById('authSection').classList.remove('hidden');
  document.getElementById('appHeader').classList.add('hidden');
  document.getElementById('mainAppContent').classList.add('hidden');
}

function showAppScreen() {
  document.getElementById('authSection').classList.add('hidden');
  document.getElementById('appHeader').classList.remove('hidden');
  document.getElementById('mainAppContent').classList.remove('hidden');
  if (currentUser) {
    const nameEl = document.getElementById('displayUserName');
    if (nameEl) nameEl.textContent = currentUser.name;
    const initials = currentUser.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
    const avatar = document.getElementById('userAvatarNav');
    if (avatar) avatar.textContent = initials || 'PH';

    const navName = document.getElementById('userNameNav');
    if (navName) navName.textContent = currentUser.name.split(' ')[0] || 'Pharmacist';

    const menuName = document.getElementById('menuUserName');
    if (menuName) menuName.textContent = currentUser.name;

    const menuEmail = document.getElementById('menuUserEmail');
    if (menuEmail) menuEmail.textContent = currentUser.email;
  }
}

function toggleProfileDropdown(e) {
  if (e) e.stopPropagation();
  const dropdown = document.getElementById('profileDropdownMenu');
  if (dropdown) {
    dropdown.classList.toggle('hidden');
  }
}

function closeProfileDropdown() {
  const dropdown = document.getElementById('profileDropdownMenu');
  if (dropdown) {
    dropdown.classList.add('hidden');
  }
}

/* ==========================================================================
   TAB ROUTING & VIEW CONTROLLERS
   ========================================================================== */

function switchTab(tabName) {
  currentTab = tabName;

  // Update Nav Buttons active state
  document.querySelectorAll('.nav-btn').forEach(btn => {
    if (btn.getAttribute('data-tab') === tabName) {
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
  });

  // Hide all tab pages, then reveal the selected workspace with a fresh motion.
  document.querySelectorAll('.tab-page').forEach(page => page.classList.add('hidden'));

  const tabIds = {
    dashboard: 'tabDashboard',
    stock: 'tabStock',
    alerts: 'tabAlerts',
    prescriptions: 'tabPrescriptions'
  };
  const targetPage = document.getElementById(tabIds[tabName]);
  if (targetPage) {
    targetPage.classList.remove('hidden', 'tab-enter');
    // Force a new animation for every tab change, including a return visit.
    void targetPage.offsetWidth;
    targetPage.classList.add('tab-enter');
    window.setTimeout(() => targetPage.classList.remove('tab-enter'), 520);
  }

  // Show target tab page & fetch data
  if (tabName === 'dashboard') {
    loadDashboardStats();
  } else if (tabName === 'stock') {
    loadAllMedicinesInventory();
  } else if (tabName === 'alerts') {
    loadLowStockList();
  } else if (tabName === 'prescriptions') {
    loadPrescriptionsQueue();
  }
}

/* ==========================================================================
   1. DASHBOARD CONTROLLER (PB07 Low-Stock Alerts & FAQ Accordion)
   ========================================================================== */

async function loadDashboardStats() {
  try {
    const res = await fetch(`${API_BASE}/dashboard/stats`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success) {
      showToast(data.message, 'error');
      return;
    }

    const { stats, alerts, recentTransactions } = data;

    // Render Stats with VengeanceUI Animated Counter Roll
    animateNumber('statTotalMedicines', stats.totalMedicines);
    animateNumber('statLowStock', stats.lowStockCount);
    animateNumber('statOutOfStock', stats.outOfStockCount);
    animateNumber('statPendingPrescriptions', stats.pendingPrescriptionsCount);

    // Render Alerts (PB07)
    const alertsContainer = document.getElementById('alertsContainer');
    if (alerts.length === 0) {
      alertsContainer.innerHTML = `
        <div style="background: rgba(255, 255, 255, 0.08); padding: 1.25rem; border-radius: 14px; color: var(--success); font-weight: 700; text-align: center; border: 1px solid var(--success-border); backdrop-filter: blur(20px);">
          ✅ All medicine stock levels are currently above configured minimum thresholds. No active low-stock alerts.
        </div>
      `;
    } else {
      alertsContainer.innerHTML = alerts.map(med => {
        const ratio = med.min_threshold > 0 ? Math.min(100, Math.round((med.stock_qty / med.min_threshold) * 100)) : 0;
        const progressColor = med.stock_qty === 0 ? '#e11d48' : '#d97706';

        return `
          <div class="alert-item-card ${med.severity}">
            <div class="alert-item-info">
              <div class="alert-med-title-row">
                <span class="alert-med-name">${escapeHtml(med.name)}</span>
                <span class="alert-med-code">${escapeHtml(med.code)}</span>
              </div>
              <div style="font-size: 0.85rem; color: var(--text-muted); margin-top: 0.35rem; font-weight: 600;">
                Current Stock: <strong style="color: var(--text-dark);">${med.stock_qty} ${escapeHtml(med.unit)}</strong> | 
                Min Threshold: <strong>${med.min_threshold} ${escapeHtml(med.unit)}</strong> 
                (Deficit: <strong class="text-deficit-danger">${med.deficit} ${escapeHtml(med.unit)}</strong>)
              </div>
              <div class="stock-progress-bg">
                <div class="stock-progress-fill" style="width: ${ratio}%; background: ${progressColor};"></div>
              </div>
            </div>
            <div class="alert-item-actions">
              <span class="alert-badge ${med.severity}">${med.status.toUpperCase()}</span>
              <button class="btn btn-primary" style="font-size: 0.825rem; padding: 0.45rem 0.9rem;" onclick="openRestockModal(${med.id}, '${escapeHtml(med.name)}', ${med.stock_qty}, ${med.min_threshold})">
                Receive Stock
              </button>
            </div>
          </div>
        `;
      }).join('');
    }

    // Render Recent Transactions
    const txBody = document.getElementById('dashboardTransactionsBody');
    if (recentTransactions.length === 0) {
      txBody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No stock addition transactions recorded yet.</td></tr>`;
    } else {
      txBody.innerHTML = recentTransactions.map(tx => `
        <tr>
          <td>${formatDate(tx.created_at)}</td>
          <td><strong>${escapeHtml(tx.medicine_name)}</strong></td>
          <td><span class="badge badge-success">+${tx.qty_received} ${escapeHtml(tx.unit)}</span></td>
          <td><code>${escapeHtml(tx.batch_ref || 'N/A')}</code></td>
          <td>${escapeHtml(tx.pharmacist_name || 'System')}</td>
        </tr>
      `).join('');
    }
  } catch (err) {
    showToast('Failed to load dashboard stats.', 'error');
  }
}

/* VENGEANCE-UI FAQ ACCORDION CONTROLLER */
function toggleFaq(buttonEl) {
  const item = buttonEl.closest('.faq-item');
  const isActive = item.classList.contains('active');

  // Close all other open FAQ items
  document.querySelectorAll('.faq-item').forEach(i => i.classList.remove('active'));

  // Toggle current item
  if (!isActive) {
    item.classList.add('active');
  }
}

/* VENGEANCE-UI ANIMATED NUMBER COUNTER (Smooth Slot Roll & Bounce) */
function animateNumber(elementId, targetValue, duration = 600) {
  const el = document.getElementById(elementId);
  if (!el) return;

  const startValue = parseInt(el.textContent, 10) || 0;
  if (startValue === targetValue) {
    el.textContent = targetValue;
    return;
  }

  el.classList.add('rolling');
  setTimeout(() => el.classList.remove('rolling'), 400);

  const startTime = performance.now();

  function update(currentTime) {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const easeOut = 1 - Math.pow(1 - progress, 3);
    const currentValue = Math.floor(startValue + (targetValue - startValue) * easeOut);

    el.textContent = currentValue;

    if (progress < 1) {
      requestAnimationFrame(update);
    } else {
      el.textContent = targetValue;
    }
  }

  requestAnimationFrame(update);
}

/* ==========================================================================
   2. MEDICINE STOCK MANAGEMENT CONTROLLER (PB06)
   ========================================================================== */

async function loadAllMedicinesInventory() {
  try {
    const res = await fetch(`${API_BASE}/medicines`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success) return;

    allMedicinesCache = data.medicines;
    renderAllMedicinesTable(allMedicinesCache);
  } catch (err) {
    showToast('Error loading inventory list.', 'error');
  }
}

function renderAllMedicinesTable(medicines) {
  const tbody = document.getElementById('allMedicinesTableBody');
  if (medicines.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 2.5rem;">No medicines found in database. Click "Add New Medicine Record" to create one.</td></tr>`;
    return;
  }

  tbody.innerHTML = medicines.map(med => {
    let badgeClass = 'badge-success';
    if (med.status === 'Low Stock') badgeClass = 'badge-warning';
    if (med.status === 'Out of Stock') badgeClass = 'badge-danger';

    return `
      <tr>
        <td><code>${escapeHtml(med.code)}</code></td>
        <td><span class="link-hover link-hover--slide"><strong>${escapeHtml(med.name)}</strong></span></td>
        <td>${escapeHtml(med.unit)}</td>
        <td><strong style="font-size: 1.05rem; color: ${med.stock_qty <= med.min_threshold ? 'var(--danger)' : 'var(--secondary)'}">${med.stock_qty}</strong></td>
        <td>${med.min_threshold}</td>
        <td><span class="badge ${badgeClass}">${med.status}</span></td>
        <td>
          <button class="btn btn-secondary" style="font-size: 0.825rem; padding: 0.4rem 0.75rem;" onclick="openRestockModal(${med.id}, '${escapeHtml(med.name)}', ${med.stock_qty}, ${med.min_threshold})">
            📥 Add Stock
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

async function performMedicineSearch() {
  const rawValue = document.getElementById('medSearchInput').value;
  const query = rawValue.trim();
  const card = document.getElementById('medSearchResultCard');
  const clearBtn = document.getElementById('btnClearSearch');

  if (clearBtn) {
    if (rawValue.length > 0) clearBtn.classList.remove('hidden');
    else clearBtn.classList.add('hidden');
  }

  if (!query) {
    card.classList.add('hidden');
    renderAllMedicinesTable(allMedicinesCache);
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/medicines?query=${encodeURIComponent(query)}`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success) return;

    const results = data.medicines;
    renderAllMedicinesTable(results);

    // If exact match or single result, highlight search card
    if (results.length > 0) {
      const med = results[0];
      let badgeClass = med.status === 'Normal' ? 'badge-success' : (med.status === 'Low Stock' ? 'badge-warning' : 'badge-danger');

      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.25rem;">
          <div>
            <h3 style="color: var(--secondary); font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 0.65rem;">
              ${escapeHtml(med.name)}
              <span class="badge ${badgeClass}">${med.status}</span>
            </h3>
            <div style="font-size: 0.85rem; color: var(--text-muted); margin-top: 0.35rem;">
              Item Code: <code>${escapeHtml(med.code)}</code> | Unit: ${escapeHtml(med.unit)}
            </div>
          </div>
          <button class="btn btn-primary" style="font-size: 0.825rem; padding: 0.45rem 1rem;" onclick="openRestockModal(${med.id}, '${escapeHtml(med.name)}', ${med.stock_qty}, ${med.min_threshold})">
            Receive Delivery Stock
          </button>
        </div>

        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; background: rgba(255, 255, 255, 0.08); padding: 1.25rem; border-radius: 14px; border: 1px solid var(--border); backdrop-filter: blur(20px);">
          <div>
            <div style="font-size: 0.725rem; color: var(--text-muted); text-transform: uppercase; font-weight: 700;">Current Stock Level</div>
            <div style="font-size: 1.5rem; font-weight: 700; color: ${med.stock_qty <= med.min_threshold ? 'var(--danger)' : 'var(--secondary)'}">${med.stock_qty} ${escapeHtml(med.unit)}</div>
          </div>
          <div>
            <div style="font-size: 0.725rem; color: var(--text-muted); text-transform: uppercase; font-weight: 700;">Configured Min Threshold</div>
            <div style="font-size: 1.5rem; font-weight: 700; color: var(--secondary);">${med.min_threshold} ${escapeHtml(med.unit)}</div>
          </div>
          <div>
            <div style="font-size: 0.725rem; color: var(--text-muted); text-transform: uppercase; font-weight: 700;">Alert Status</div>
            <div style="font-size: 1.1rem; font-weight: 700; margin-top: 0.25rem; color: ${med.stock_qty <= med.min_threshold ? 'var(--danger)' : 'var(--success)'}">
              ${med.stock_qty <= med.min_threshold ? 'Below Minimum Threshold' : 'Normal Inventory Level'}
            </div>
          </div>
        </div>
      `;
      card.classList.remove('hidden');
    } else {
      // Medicine search missed -> Prompt to add a new record (PB06 edge case requirement)
      card.innerHTML = `
        <div style="text-align: center; padding: 2rem 1rem;">
          <h3 style="color: var(--secondary); margin-bottom: 0.5rem; font-weight: 700;">No Medicine Found Matching "${escapeHtml(query)}"</h3>
          <p style="color: var(--text-muted); margin-bottom: 1.5rem; font-size: 0.9rem; max-width: 500px; margin-left: auto; margin-right: auto;">
            This medicine is not yet registered in the inventory database. Would you like to create a new medicine record for it now?
          </p>
          <button class="btn btn-primary" onclick="openAddMedicineModal('${escapeHtml(query)}')">
            Add "${escapeHtml(query)}" as New Medicine Record
          </button>
        </div>
      `;
      card.classList.remove('hidden');
    }
  } catch (err) {
    showToast('Search failed.', 'error');
  }
}

function clearMedicineSearch() {
  const searchInput = document.getElementById('medSearchInput');
  const clearBtn = document.getElementById('btnClearSearch');
  if (searchInput) searchInput.value = '';
  if (clearBtn) clearBtn.classList.add('hidden');
  document.getElementById('medSearchResultCard').classList.add('hidden');
  renderAllMedicinesTable(allMedicinesCache);
}

/* Modal Openers & Submissions */

function openAddMedicineModal(suggestedName = '') {
  if (suggestedName) {
    document.getElementById('newMedName').value = suggestedName;
    document.getElementById('newMedCode').value = 'MED-' + Math.floor(1000 + Math.random() * 9000);
  } else {
    document.getElementById('addMedicineForm').reset();
  }
  document.getElementById('modalAddMedicine').classList.remove('hidden');
}

function closeAddMedicineModal() {
  document.getElementById('modalAddMedicine').classList.add('hidden');
}

async function handleAddMedicineSubmit(e) {
  e.preventDefault();
  const code = document.getElementById('newMedCode').value.trim();
  const name = document.getElementById('newMedName').value.trim();
  const unit = document.getElementById('newMedUnit').value.trim();
  const min_threshold = parseInt(document.getElementById('newMedMin').value, 10);
  const starting_stock = parseInt(document.getElementById('newMedStarting').value, 10);

  if (isNaN(min_threshold) || min_threshold < 0 || isNaN(starting_stock) || starting_stock < 0) {
    showToast('Validation Error: Threshold and starting stock must be positive numbers.', 'error');
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/medicines`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({ code, name, unit, min_threshold, starting_stock })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      showToast(data.message || 'Error adding medicine.', 'error');
      return;
    }

    closeAddMedicineModal();
    showToast(data.message, 'success');

    // If starting stock triggers low stock alert (PB07)
    if (data.lowStockAlert) {
      showToast(data.alertMessage, 'warning');
    }

    loadAllMedicinesInventory();
    if (currentTab === 'dashboard') loadDashboardStats();
  } catch (err) {
    showToast('Server error creating medicine record.', 'error');
  }
}

function openRestockModal(id, name, currentQty, minThreshold) {
  document.getElementById('restockMedId').value = id;
  document.getElementById('restockMedName').textContent = name;
  document.getElementById('restockCurrentQty').textContent = currentQty;
  document.getElementById('restockMinThreshold').textContent = minThreshold;
  document.getElementById('restockQty').value = '';
  document.getElementById('restockBatchRef').value = '';
  document.getElementById('modalRestock').classList.remove('hidden');
}

function closeRestockModal() {
  document.getElementById('modalRestock').classList.add('hidden');
}

async function handleRestockSubmit(e) {
  e.preventDefault();
  const medId = document.getElementById('restockMedId').value;
  const qtyInput = document.getElementById('restockQty').value;
  const batchRef = document.getElementById('restockBatchRef').value.trim();

  // EDGE CASE VALIDATION: reject negative or non-numeric values
  const qty_received = Number(qtyInput);
  if (isNaN(qty_received) || !Number.isInteger(qty_received) || qty_received <= 0) {
    showToast('Validation Error: Received quantity must be a positive integer greater than zero.', 'error');
    return;
  }

  if (!batchRef) {
    showToast('Validation Error: Batch or Supplier reference code is required.', 'error');
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/medicines/${medId}/stock`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({ qty_received, batch_ref: batchRef })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      showToast(data.message || 'Error updating stock.', 'error');
      return;
    }

    closeRestockModal();
    showToast(data.message, 'success');

    // PB07 Check: If after update medicine is STILL below threshold
    if (data.lowStockAlert) {
      showToast(data.alertMessage, 'warning');
    }

    // Refresh active views
    loadAllMedicinesInventory();
    if (currentTab === 'dashboard') loadDashboardStats();
    if (currentTab === 'alerts') loadLowStockList();
  } catch (err) {
    showToast('Server error updating stock.', 'error');
  }
}

/* ==========================================================================
   3. LOW / OUT-OF-STOCK LIST CONTROLLER (PB12 & PB07)
   ========================================================================== */

function setAlertFilter(filter) {
  currentAlertFilter = filter;
  document.querySelectorAll('.filter-btn').forEach(btn => {
    if (btn.getAttribute('data-alert-filter') === filter) btn.classList.add('active');
    else btn.classList.remove('active');
  });
  loadLowStockList();
}

async function loadLowStockList() {
  const sortBy = document.getElementById('alertSortSelect').value;
  try {
    const res = await fetch(`${API_BASE}/medicines/alerts?filter=${currentAlertFilter}&sortBy=${sortBy}`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success) return;

    const tbody = document.getElementById('lowStockTableBody');
    if (data.medicines.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 2.5rem;">🎉 No medicines match the selected alert filter criteria. All inventory stock is sufficient!</td></tr>`;
      return;
    }

    tbody.innerHTML = data.medicines.map(med => {
      const isOut = med.stock_qty <= 0;
      const badgeClass = isOut ? 'badge-danger' : 'badge-warning';

      return `
        <tr>
          <td><code>${escapeHtml(med.code)}</code></td>
          <td><strong>${escapeHtml(med.name)}</strong></td>
          <td><strong style="color: ${isOut ? 'var(--danger)' : 'var(--warning)'}; font-size: 1.05rem;">${med.stock_qty} ${escapeHtml(med.unit)}</strong></td>
          <td>${med.min_threshold} ${escapeHtml(med.unit)}</td>
          <td><strong style="color: var(--danger); font-size: 1.05rem;">-${med.deficit} ${escapeHtml(med.unit)}</strong></td>
          <td><span class="badge ${badgeClass}">${med.status}</span></td>
          <td>
            <button class="btn btn-primary" style="font-size: 0.825rem; padding: 0.4rem 0.85rem;" onclick="openRestockModal(${med.id}, '${escapeHtml(med.name)}', ${med.stock_qty}, ${med.min_threshold})">
              📥 Receive Stock
            </button>
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    showToast('Failed to load low stock list.', 'error');
  }
}

/* ==========================================================================
   4. PRESCRIPTION DISPENSING QUEUE CONTROLLER (PB13)
   ========================================================================== */

function setPrescriptionStatusFilter(status) {
  currentPrescStatus = status;
  document.querySelectorAll('.presc-filter-btn').forEach(btn => {
    if (btn.getAttribute('data-presc-status') === status) btn.classList.add('active');
    else btn.classList.remove('active');
  });
  loadPrescriptionsQueue();
}

async function loadPrescriptionsQueue() {
  try {
    const res = await fetch(`${API_BASE}/prescriptions?status=${currentPrescStatus}`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success) return;

    const container = document.getElementById('prescriptionsQueueContainer');
    if (data.prescriptions.length === 0) {
      container.innerHTML = `
        <div class="content-card" style="text-align: center; padding: 3.5rem;">
          <div style="font-size: 3rem; margin-bottom: 0.5rem;">📋</div>
          <h3 style="color: var(--text-muted); font-weight: 700;">No prescriptions found in queue matching status "${currentPrescStatus}".</h3>
        </div>
      `;
      return;
    }

    container.innerHTML = data.prescriptions.map(p => {
      const isFullyDispensed = p.status === 'Dispensed';
      const statusBadge = isFullyDispensed ? '<span class="badge badge-success">Dispensed</span>' : '<span class="badge badge-warning">Pending</span>';

      return `
        <div class="prescription-card">
          <div class="prescription-card-header">
            <div class="patient-info">
              <h4>Patient: ${escapeHtml(p.patient_name)}</h4>
              <div class="doctor-info">Prescribed by <strong>${escapeHtml(p.doctor_name)}</strong> on ${formatDate(p.created_at)}</div>
            </div>
            <div style="display: flex; align-items: center; gap: 0.85rem;">
              ${statusBadge}
              ${!isFullyDispensed ? `
                <button class="btn btn-primary" style="font-size: 0.825rem; padding: 0.45rem 1.1rem;" onclick="dispenseEntirePrescription(${p.id})">
                  Dispense All Items
                </button>
              ` : ''}
            </div>
          </div>

          <div style="padding: 1.25rem;">
            <div class="table-responsive">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Prescribed Medicine</th>
                    <th>Dosage</th>
                    <th>Frequency</th>
                    <th>Duration</th>
                    <th>Qty Required</th>
                    <th>Inventory Stock</th>
                    <th>Item Status</th>
                    <th>Dispensing Action</th>
                  </tr>
                </thead>
                <tbody>
                  ${p.items.map(item => {
                    let stockBadge = '<span class="badge badge-success">In Stock</span>';
                    if (item.stockCheck === 'Out of Stock') stockBadge = '<span class="badge badge-danger">Out of Stock</span>';
                    else if (item.stockCheck === 'Insufficient Stock') stockBadge = '<span class="badge badge-danger">Insufficient Stock</span>';
                    else if (item.stockCheck === 'Low Stock (Available)') stockBadge = '<span class="badge badge-warning">Low Stock</span>';

                    return `
                      <tr>
                        <td><strong>${escapeHtml(item.medicine_name)}</strong> <span style="font-size: 0.775rem; color: var(--text-muted);">(${escapeHtml(item.medicine_code)})</span></td>
                        <td>${escapeHtml(item.dosage)}</td>
                        <td>${escapeHtml(item.frequency)}</td>
                        <td>${escapeHtml(item.duration)}</td>
                        <td><strong style="color: var(--text-dark); font-size: 0.95rem;">${item.quantity} ${escapeHtml(item.unit)}</strong></td>
                        <td>
                          <strong style="color: ${item.hasSufficientStock ? 'var(--text-dark)' : 'var(--danger)'};">${item.stock_qty} ${escapeHtml(item.unit)}</strong>
                        </td>
                        <td>
                          ${item.dispensed ? '<span class="badge badge-success">Dispensed</span>' : stockBadge}
                        </td>
                        <td>
                          ${item.dispensed ? `
                            <span style="font-size: 0.775rem; color: var(--text-muted);">Dispensed ${formatDate(item.dispensed_at)}</span>
                          ` : `
                            <button class="btn ${item.hasSufficientStock ? 'btn-primary' : 'btn-secondary'}"
                                    style="font-size: 0.8rem; padding: 0.4rem 0.9rem;"
                                    onclick="dispensePrescriptionItem(${p.id}, ${item.id}, '${escapeHtml(item.medicine_name)}', ${item.hasSufficientStock})">
                              Dispense Line Item
                            </button>
                          `}
                        </td>
                      </tr>
                    `;
                  }).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      `;
    }).join('');
  } catch (err) {
    showToast('Error loading prescription queue.', 'error');
  }
}

async function dispensePrescriptionItem(prescriptionId, itemId, medicineName, hasSufficientStock) {
  // EDGE CASE WARNING IF STOCK IS INSUFFICIENT
  if (!hasSufficientStock) {
    const confirmChoice = confirm(
      `⚠️ INSUFFICIENT STOCK WARNING!\n\nCurrent inventory stock for "${medicineName}" is insufficient to fulfill this prescribed quantity.\n\nThe system will block negative stock levels. Do you still want to attempt dispensing?`
    );
    if (!confirmChoice) return;
  }

  try {
    const res = await fetch(`${API_BASE}/prescriptions/${prescriptionId}/dispense-item/${itemId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    const data = await res.json();

    // EDGE CASE HANDLING: If insufficient stock, backend returns 400 Bad Request with alert
    if (!res.ok || !data.success) {
      alert(`⛔ DISPENSING BLOCKED!\n\n${data.message}`);
      showToast(data.message, 'error');
      return;
    }

    showToast(data.message, 'success');

    if (data.lowStockAlert) {
      showToast(data.alertMessage, 'warning');
    }

    loadPrescriptionsQueue();
  } catch (err) {
    showToast('Server error during dispensing.', 'error');
  }
}

async function dispenseEntirePrescription(prescriptionId) {
  if (!confirm(`Are you sure you want to dispense all pending items for Prescription #${prescriptionId}? This will automatically deduct inventory stock.`)) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/prescriptions/${prescriptionId}/dispense`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${authToken}` }
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      alert(`⛔ DISPENSING CANCELLED!\n\n${data.message}`);
      showToast(data.message, 'error');
      return;
    }

    showToast(data.message, 'success');
    loadPrescriptionsQueue();
  } catch (err) {
    showToast('Server error dispensing prescription.', 'error');
  }
}

/* ==========================================================================
   UTILITY HELPERS
   ========================================================================== */

function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  let icon = 'ℹ️';
  if (type === 'success') icon = '✅';
  if (type === 'error') icon = '❌';
  if (type === 'warning') icon = '⚠️';

  toast.innerHTML = `<span style="font-size: 1.3rem;">${icon}</span> <div>${escapeHtml(message)}</div>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.4s ease';
    setTimeout(() => toast.remove(), 400);
  }, 4500);
}

function formatDate(dateStr) {
  if (!dateStr) return 'N/A';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/* ==========================================================================
   CHANGE PASSWORD & EXPORT CSV FEATURES
   ========================================================================== */

function openUserProfileModal() {
  const currentPassEl = document.getElementById('panelCurrentPassword');
  const newPassEl = document.getElementById('panelNewPassword');
  const confirmPassEl = document.getElementById('panelConfirmPassword');
  if (currentPassEl) currentPassEl.value = '';
  if (newPassEl) newPassEl.value = '';
  if (confirmPassEl) confirmPassEl.value = '';

  if (currentUser) {
    const nameEl = document.getElementById('panelUserName');
    const emailEl = document.getElementById('panelUserEmail');
    const avatarEl = document.getElementById('modalUserAvatar');
    const inputName = document.getElementById('panelInputName');
    const inputEmail = document.getElementById('panelInputEmail');

    if (nameEl) nameEl.textContent = currentUser.name;
    if (emailEl) emailEl.textContent = currentUser.email;
    if (inputName) inputName.value = currentUser.name;
    if (inputEmail) inputEmail.value = currentUser.email;

    if (avatarEl) {
      const initials = currentUser.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
      avatarEl.textContent = initials || 'PH';
    }
  }

  const modal = document.getElementById('modalUserProfile');
  if (modal) modal.classList.remove('hidden');
}

async function handlePanelUpdateProfileSubmit(e) {
  e.preventDefault();
  const name = document.getElementById('panelInputName').value.trim();
  const email = document.getElementById('panelInputEmail').value.trim();

  if (!name || !email) {
    showToast('Name and email address are required.', 'error');
    return;
  }

  try {
    const tokenVal = authToken || localStorage.getItem('hc_pharmacist_token');
    const res = await fetch(`${API_BASE}/auth/profile`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tokenVal}`
      },
      body: JSON.stringify({ name, email })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      currentUser.name = data.user.name;
      currentUser.email = data.user.email;
      showAppScreen();
      showToast(data.message, 'success');
    } else {
      showToast(data.message || 'Failed to update profile.', 'error');
    }
  } catch (err) {
    console.error('Error updating profile:', err);
    showToast('Network error while updating profile details.', 'error');
  }
}

function closeUserProfileModal() {
  const modal = document.getElementById('modalUserProfile');
  if (modal) modal.classList.add('hidden');
}

function openChangePasswordModal() {
  openUserProfileModal();
}

function closeChangePasswordModal() {
  closeUserProfileModal();
}

async function handlePanelChangePasswordSubmit(e) {
  e.preventDefault();
  const currentPassword = (document.getElementById('panelCurrentPassword') || document.getElementById('currentPasswordInput')).value;
  const newPassword = (document.getElementById('panelNewPassword') || document.getElementById('newPasswordInput')).value;
  const confirmPassword = (document.getElementById('panelConfirmPassword') || document.getElementById('confirmPasswordInput')).value;

  if (newPassword !== confirmPassword) {
    showToast('New password and confirm password do not match.', 'error');
    return;
  }

  if (newPassword.length < 6) {
    showToast('New password must be at least 6 characters long.', 'error');
    return;
  }

  try {
    const tokenVal = authToken || localStorage.getItem('hc_pharmacist_token');
    const res = await fetch(`${API_BASE}/auth/change-password`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tokenVal}`
      },
      body: JSON.stringify({ currentPassword, newPassword, confirmPassword })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      showToast(data.message, 'success');
      closeUserProfileModal();
    } else {
      showToast(data.message || 'Failed to update password.', 'error');
    }
  } catch (err) {
    console.error('Error changing password:', err);
    showToast('Network error while changing password.', 'error');
  }
}

async function handleChangePasswordSubmit(e) {
  return handlePanelChangePasswordSubmit(e);
}

async function exportLowStockCSV() {
  try {
    const res = await fetch(`${API_BASE}/pharmacy/alerts?filter=all&sortBy=deficit`, {
      headers: { 'Authorization': `Bearer ${authToken}` }
    });
    const data = await res.json();

    if (!data.success || !data.medicines || data.medicines.length === 0) {
      showToast('No low-stock items to export.', 'info');
      return;
    }

    let csvContent = 'data:text/csv;charset=utf-8,Code,Medicine Name,Unit,Current Stock,Min Threshold,Deficit Gap,Status\n';
    data.medicines.forEach(m => {
      const status = m.stock_qty <= 0 ? 'Out of Stock' : 'Low Stock';
      csvContent += `"${m.code}","${m.name}","${m.unit}",${m.stock_qty},${m.min_threshold},${m.deficit},"${status}"\n`;
    });

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    const dateStr = new Date().toISOString().split('T')[0];
    link.setAttribute('download', `HealthConnect_Low_Stock_Report_${dateStr}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showToast(`Exported ${data.medicines.length} low-stock alert items to CSV!`, 'success');
  } catch (err) {
    console.error('CSV Export Error:', err);
    showToast('Failed to export CSV report.', 'error');
  }
}

