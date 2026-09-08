// FIND-MISSING-PEP Command Center Web Application JavaScript

const API_BASE = '';
const AUTH_TOKEN = 'mock-token-admin'; // Mock token auto-provisions and authenticates in dev mode

// State
let casesList = [];
let sightingsList = [];
let currentFilter = 'ALL';
let autoRefreshTimer = null;

// DOM Elements
document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
  initFormUpload();
  loadAllData();
  startAutoRefresh();
});

// Navigation Tabs
function initNavigation() {
  const tabs = document.querySelectorAll('.nav-tab');
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      tab.classList.add('active');
      const targetId = tab.getAttribute('data-tab');
      const targetContent = document.getElementById(targetId);
      if (targetContent) {
        targetContent.classList.add('active');
      }

      if (targetId === 'tab-cases') {
        loadCases();
      } else if (targetId === 'tab-dashboard') {
        loadDashboard();
      } else if (targetId === 'tab-agents') {
        loadAgents();
      }
    });
  });

  // Filter buttons in Directory
  const filterBtns = document.querySelectorAll('.filter-btn');
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentFilter = btn.getAttribute('data-status');
      renderCases();
    });
  });

  // Search input
  const searchInput = document.getElementById('search-cases');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      renderCases();
    });
  }
}

// Auto-Refresh
function startAutoRefresh() {
  if (autoRefreshTimer) clearInterval(autoRefreshTimer);
  autoRefreshTimer = setInterval(() => {
    const activeTab = document.querySelector('.nav-tab.active');
    if (activeTab && activeTab.getAttribute('data-tab') === 'tab-dashboard') {
      loadDashboard(true);
    }
  }, 5000);
}

// Load All Data
async function loadAllData() {
  await Promise.all([loadDashboard(), loadCases(), loadAgents()]);
}

// Toast Notifications
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `
    <span>${type === 'success' ? '✅' : '⚠️'}</span>
    <div>${message}</div>
  `;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Dashboard & Live Sightings
// ─────────────────────────────────────────────────────────────────────────────

async function loadDashboard(silent = false) {
  try {
    // 1. Fetch cases
    const resCases = await fetch(`${API_BASE}/api/reports/all`, {
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (resCases.ok) {
      const data = await resCases.json();
      casesList = data.items || [];
      updateDashboardStats();
    }

    // 2. Fetch sightings
    const resSightings = await fetch(`${API_BASE}/api/sightings/`, {
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (resSightings.ok) {
      sightingsList = await resSightings.json();
      renderSightingsFeed();
    }
  } catch (err) {
    if (!silent) console.error('Failed to load dashboard data:', err);
  }
}

function updateDashboardStats() {
  const total = casesList.length;
  const active = casesList.filter(c => c.status === 'ACTIVE' || c.status === 'PROCESSING').length;
  const found = casesList.filter(c => c.status === 'FOUND').length;
  const matches = sightingsList.length;

  document.getElementById('stat-active-cases').textContent = active;
  document.getElementById('stat-total-cases').textContent = total;
  document.getElementById('stat-matches').textContent = matches;
  document.getElementById('stat-found').textContent = found;
}

function renderSightingsFeed() {
  const container = document.getElementById('sightings-feed-container');
  if (!container) return;

  if (sightingsList.length === 0) {
    container.innerHTML = `
      <div style="text-align: center; padding: 3rem; color: var(--text-muted); background: var(--bg-card); border-radius: var(--radius-lg); border: 1px dashed var(--border-color);">
        <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">📡</div>
        <div style="font-weight: 600; color: var(--text-primary);">No CCTV Sightings Yet</div>
        <div style="font-size: 0.85rem; margin-top: 0.25rem;">Live AI detections from connected Edge Agents will appear here in real-time.</div>
      </div>
    `;
    return;
  }

  container.innerHTML = sightingsList.map(s => {
    const simPct = (s.similarity_score * 100).toFixed(1);
    const dateStr = s.detected_at ? new Date(s.detected_at).toLocaleString() : 'Just now';
    const isConfirmed = s.status === 'CONFIRMED';
    const isRejected = s.status === 'REJECTED';

    // Find person name from cases
    const person = casesList.find(c => c.id === s.person_id);
    const personName = person ? person.full_name : `Person ${s.person_id.substring(0, 8)}`;
    const regPhoto = person && person.photos && person.photos.length > 0 ? person.photos[0].original_path : '/static/placeholder.jpg';

    return `
      <div class="sighting-card">
        <div class="sighting-photos">
          <div class="photo-box">
            <img src="${s.face_crop_path || '/static/placeholder.jpg'}" alt="CCTV Crop" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'80\\' height=\\'80\\'><rect fill=\\'%23222\\' width=\\'80\\' height=\\'80\\'/><text fill=\\'%23666\\' x=\\'50%\\' y=\\'50%\\' text-anchor=\\'middle\\'>Crop</text></svg>'">
            <span>CCTV Crop</span>
          </div>
          <div style="font-size: 1.25rem; color: var(--text-muted);">⚡</div>
          <div class="photo-box">
            <img src="${regPhoto}" alt="Registered Photo" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'80\\' height=\\'80\\'><rect fill=\\'%23222\\' width=\\'80\\' height=\\'80\\'/><text fill=\\'%23666\\' x=\\'50%\\' y=\\'50%\\' text-anchor=\\'middle\\'>Profile</text></svg>'">
            <span>Database</span>
          </div>
        </div>

        <div class="sighting-info">
          <div class="sighting-title">
            <span>${personName}</span>
            <span class="case-badge ${isConfirmed ? 'badge-found' : isRejected ? 'badge-closed' : 'badge-processing'}">${s.status || 'PENDING'}</span>
          </div>
          <div class="sighting-loc">
            📍 <strong>${s.camera_location || 'Camera Channel'}</strong> • 🕒 ${dateStr}
          </div>
          <div class="sighting-score-gauge">
            <div class="progress-bar-bg">
              <div class="progress-bar-fill" style="width: ${Math.min(100, simPct)}%;"></div>
            </div>
            <span class="score-text">${simPct}% Match</span>
          </div>
        </div>

        <div class="sighting-actions">
          ${!isConfirmed && !isRejected ? `
            <button class="btn btn-success btn-sm" onclick="confirmSighting('${s.id}')">
              ✓ Confirm Match
            </button>
            <button class="btn btn-danger btn-sm" onclick="rejectSighting('${s.id}')">
              ✕ Reject
            </button>
          ` : `
            <span style="font-size: 0.85rem; color: var(--text-muted); font-style: italic;">
              ${isConfirmed ? '✅ Confirmed by Operator' : '❌ Marked False Positive'}
            </span>
          `}
          ${s.full_frame_path ? `
            <a href="${s.full_frame_path}" target="_blank" class="btn btn-secondary btn-sm" title="View Full Frame">
              🔍 Frame
            </a>
          ` : ''}
        </div>
      </div>
    `;
  }).join('');
}

async function confirmSighting(sightingId) {
  try {
    const res = await fetch(`${API_BASE}/api/sightings/${sightingId}/confirm`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ review_notes: 'Confirmed via web command center' })
    });
    if (res.ok) {
      showToast('Sighting confirmed! Reporter and family notified.', 'success');
      loadDashboard();
    } else {
      showToast('Failed to confirm sighting', 'error');
    }
  } catch (err) {
    showToast('Network error while confirming', 'error');
  }
}

async function rejectSighting(sightingId) {
  try {
    const res = await fetch(`${API_BASE}/api/sightings/${sightingId}/reject`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ review_notes: 'False positive rejected by operator' })
    });
    if (res.ok) {
      showToast('Sighting rejected.', 'success');
      loadDashboard();
    } else {
      showToast('Failed to reject sighting', 'error');
    }
  } catch (err) {
    showToast('Network error while rejecting', 'error');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Cases Directory
// ─────────────────────────────────────────────────────────────────────────────

async function loadCases() {
  try {
    const res = await fetch(`${API_BASE}/api/reports/all`, {
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (res.ok) {
      const data = await res.json();
      casesList = data.items || [];
      renderCases();
    }
  } catch (err) {
    console.error('Failed to load cases:', err);
  }
}

function renderCases() {
  const container = document.getElementById('cases-grid-container');
  if (!container) return;

  const searchQuery = (document.getElementById('search-cases')?.value || '').toLowerCase().trim();

  let filtered = casesList;
  if (currentFilter !== 'ALL') {
    filtered = filtered.filter(c => c.status === currentFilter);
  }
  if (searchQuery) {
    filtered = filtered.filter(c => 
      (c.full_name && c.full_name.toLowerCase().includes(searchQuery)) ||
      (c.last_seen_location && c.last_seen_location.toLowerCase().includes(searchQuery))
    );
  }

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1 / -1; text-align: center; padding: 4rem; color: var(--text-muted); background: var(--bg-card); border-radius: var(--radius-lg); border: 1px dashed var(--border-color);">
        <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🔍</div>
        <div style="font-weight: 600; color: var(--text-primary);">No Cases Found</div>
        <div style="font-size: 0.85rem; margin-top: 0.25rem;">Try adjusting your search query or register a new missing person case.</div>
      </div>
    `;
    return;
  }

  container.innerHTML = filtered.map(c => {
    const photoUrl = c.photos && c.photos.length > 0 ? c.photos[0].original_path : 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80';
    const lastSeenDate = c.last_seen_time ? new Date(c.last_seen_time).toLocaleDateString() : 'Unknown';

    let badgeClass = 'badge-processing';
    if (c.status === 'ACTIVE') badgeClass = 'badge-active';
    else if (c.status === 'FOUND') badgeClass = 'badge-found';
    else if (c.status === 'CLOSED') badgeClass = 'badge-closed';

    return `
      <div class="case-card">
        <div class="case-header">
          <img src="${photoUrl}" alt="${c.full_name}" class="case-photo" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'300\\' height=\\'200\\'><rect fill=\\'%2318181b\\' width=\\'300\\' height=\\'200\\'/><text fill=\\'%2371717a\\' x=\\'50%\\' y=\\'50%\\' text-anchor=\\'middle\\'>No Photo</text></svg>'">
          <span class="case-badge ${badgeClass}">${c.status}</span>
        </div>

        <div class="case-body">
          <div class="case-name">${c.full_name}</div>
          <div class="case-meta">
            <div class="meta-item">Age: <strong>${c.age || 'N/A'} yrs</strong></div>
            <div class="meta-item">Gender: <strong>${c.gender || 'N/A'}</strong></div>
            <div class="meta-item" style="grid-column: span 2;">
              📍 Last Seen: <strong>${c.last_seen_location || 'Not Specified'}</strong>
            </div>
            <div class="meta-item">Date: <strong>${lastSeenDate}</strong></div>
            <div class="meta-item">Height: <strong>${c.height_cm ? c.height_cm + ' cm' : 'N/A'}</strong></div>
          </div>
          ${c.description ? `<div class="case-desc">${c.description}</div>` : ''}
        </div>

        <div class="case-footer">
          <button class="btn btn-secondary btn-sm" onclick="openTimelineModal('${c.id}', '${c.full_name}')">
            📊 View Timeline
          </button>
          
          <div style="display: flex; gap: 0.35rem;">
            ${c.status !== 'FOUND' ? `
              <button class="btn btn-success btn-sm" onclick="markCaseStatus('${c.id}', 'FOUND')">
                Found
              </button>
            ` : ''}
            ${c.status !== 'CLOSED' ? `
              <button class="btn btn-secondary btn-sm" onclick="closeCase('${c.id}')" title="Close Case">
                Close
              </button>
            ` : ''}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

async function markCaseStatus(reportId, newStatus) {
  try {
    const res = await fetch(`${API_BASE}/api/reports/${reportId}`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ status: newStatus })
    });
    if (res.ok) {
      showToast(`Case marked as ${newStatus}!`, 'success');
      loadCases();
      loadDashboard();
    } else {
      showToast('Failed to update case status', 'error');
    }
  } catch (err) {
    showToast('Network error updating case', 'error');
  }
}

async function closeCase(reportId) {
  if (!confirm('Are you sure you want to close this case? AI Edge Agents will prune this person from active CCTV monitoring.')) return;

  try {
    const res = await fetch(`${API_BASE}/api/reports/${reportId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (res.ok) {
      showToast('Case closed and deactivated from CCTV edge index.', 'success');
      loadCases();
      loadDashboard();
    } else {
      showToast('Failed to close case', 'error');
    }
  } catch (err) {
    showToast('Network error closing case', 'error');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Register New Missing Person Form
// ─────────────────────────────────────────────────────────────────────────────

let selectedPhotoFiles = [];

function initFormUpload() {
  const dropzone = document.getElementById('photo-dropzone');
  const fileInput = document.getElementById('photo-input');
  const previewContainer = document.getElementById('photo-preview-container');
  const form = document.getElementById('register-case-form');

  if (!dropzone || !fileInput) return;

  dropzone.addEventListener('click', () => fileInput.click());

  dropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzone.classList.add('dragover');
  });

  dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));

  dropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  });

  fileInput.addEventListener('change', () => {
    if (fileInput.files && fileInput.files.length > 0) {
      handleFiles(fileInput.files);
    }
  });

  function handleFiles(files) {
    selectedPhotoFiles = Array.from(files);
    previewContainer.innerHTML = '';

    selectedPhotoFiles.forEach((file, index) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const item = document.createElement('div');
        item.className = 'preview-item';
        item.innerHTML = `
          <img src="${e.target.result}" alt="Preview ${index}">
          <div style="position: absolute; bottom: 0; left: 0; right: 0; background: rgba(0,0,0,0.6); font-size: 10px; color: #fff; text-align: center;">
            ${index === 0 ? 'Primary' : 'Photo ' + (index + 1)}
          </div>
        `;
        previewContainer.appendChild(item);
      };
      reader.readAsDataURL(file);
    });
  }

  // Handle Form Submission
  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      if (selectedPhotoFiles.length === 0) {
        showToast('Please upload at least one clear face photo', 'error');
        return;
      }

      const submitBtn = document.getElementById('submit-case-btn');
      submitBtn.disabled = true;
      submitBtn.innerHTML = '⏳ Processing Face AI...';

      const formData = new FormData();
      formData.append('full_name', document.getElementById('reg-name').value);
      formData.append('age', document.getElementById('reg-age').value);
      formData.append('gender', document.getElementById('reg-gender').value);
      formData.append('height_cm', document.getElementById('reg-height').value || '170');
      formData.append('last_seen_location', document.getElementById('reg-location').value);
      formData.append('last_seen_time', document.getElementById('reg-date').value ? new Date(document.getElementById('reg-date').value).toISOString() : new Date().toISOString());
      formData.append('description', document.getElementById('reg-desc').value);
      formData.append('contact_info', document.getElementById('reg-contact').value);

      // Append photos
      selectedPhotoFiles.forEach(file => {
        formData.append('photos', file);
      });

      try {
        const res = await fetch(`${API_BASE}/api/reports/`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${AUTH_TOKEN}`
          },
          body: formData
        });

        if (res.ok) {
          const result = await res.json();
          showToast(`Case "${result.full_name}" registered! ArcFace 512-D embeddings generated.`, 'success');
          form.reset();
          selectedPhotoFiles = [];
          previewContainer.innerHTML = '';

          // Switch to Directory tab
          document.querySelector('.nav-tab[data-tab="tab-cases"]').click();
          loadCases();
          loadDashboard();
        } else {
          const errData = await res.json();
          showToast(`Registration failed: ${errData.detail || 'Unknown error'}`, 'error');
        }
      } catch (err) {
        showToast(`Network error: ${err.message}`, 'error');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '⚡ Submit Case & Generate AI Embeddings';
      }
    });
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Timeline Modal
// ─────────────────────────────────────────────────────────────────────────────

async function openTimelineModal(personId, personName) {
  const modal = document.getElementById('timeline-modal');
  const title = document.getElementById('modal-person-name');
  const timelineContent = document.getElementById('modal-timeline-content');

  title.textContent = `Chronological Sighting Timeline: ${personName}`;
  timelineContent.innerHTML = '<div style="text-align: center; padding: 2rem;">Loading sighting trail...</div>';
  modal.classList.add('active');

  try {
    const res = await fetch(`${API_BASE}/api/reports/${personId}/timeline`, {
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (!data.entries || data.entries.length === 0) {
        timelineContent.innerHTML = `
          <div style="text-align: center; padding: 2rem; color: var(--text-muted);">
            <div style="font-size: 2rem; margin-bottom: 0.5rem;">📍</div>
            <div>No sightings recorded yet for this person.</div>
            <div style="font-size: 0.8rem; margin-top: 0.25rem;">When Edge Agent cameras match this face, the chronological trail will appear here.</div>
          </div>
        `;
        return;
      }

      timelineContent.innerHTML = `
        <div style="margin-bottom: 1.5rem; padding: 0.85rem; background: rgba(59, 130, 246, 0.1); border-radius: var(--radius-md); border: 1px solid rgba(59, 130, 246, 0.3);">
          📍 <strong>Last Seen Camera:</strong> ${data.last_seen_camera || 'Unknown'}<br>
          🕒 <strong>Last Detected Time:</strong> ${data.last_seen_time ? new Date(data.last_seen_time).toLocaleString() : 'N/A'}
        </div>
        <div class="timeline">
          ${data.entries.map(e => `
            <div class="timeline-item">
              <div class="timeline-dot"></div>
              <div class="timeline-time">${new Date(e.detected_at).toLocaleString()}</div>
              <div class="timeline-title">${e.camera_name} (${e.camera_location || 'Camera Channel'})</div>
              <div style="font-size: 0.85rem; color: var(--text-secondary); margin-top: 0.2rem;">
                Match Confidence: <strong style="color: #34d399;">${(e.similarity_score * 100).toFixed(1)}%</strong> • Status: <strong>${e.status}</strong>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } else {
      timelineContent.innerHTML = '<div style="color: var(--danger); text-align: center;">Failed to load timeline</div>';
    }
  } catch (err) {
    timelineContent.innerHTML = `<div style="color: var(--danger); text-align: center;">Error: ${err.message}</div>`;
  }
}

function closeTimelineModal() {
  const modal = document.getElementById('timeline-modal');
  if (modal) modal.classList.remove('active');
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Edge Agents & Sync Controls
// ─────────────────────────────────────────────────────────────────────────────

async function loadAgents() {
  const container = document.getElementById('agents-list-container');
  if (!container) return;

  try {
    const res = await fetch(`${API_BASE}/api/agents/`, {
      headers: { 'Authorization': `Bearer ${AUTH_TOKEN}` }
    });
    if (res.ok) {
      const agents = await res.json();
      if (agents.length === 0) {
        container.innerHTML = `
          <div style="text-align: center; padding: 2rem; color: var(--text-muted); background: var(--bg-card); border-radius: var(--radius-lg);">
            No registered Edge Agents found. Run <code>py -m edge_agent.main</code> to enroll your local CCTV station.
          </div>
        `;
        return;
      }

      container.innerHTML = agents.map(a => `
        <div class="sighting-card" style="margin-bottom: 1rem;">
          <div>
            <div class="sighting-title">🖥️ ${a.device_id}</div>
            <div class="sighting-loc">📍 Location: <strong>${a.location_name || 'Main Building'}</strong> • Status: <span class="case-badge badge-active">${a.status || 'ACTIVE'}</span></div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.35rem;">
              Last Heartbeat: ${a.last_heartbeat ? new Date(a.last_heartbeat).toLocaleString() : 'Recent'} • Agent UUID: <code>${a.id}</code>
            </div>
          </div>
          <div>
            <span class="status-pill"><span class="status-dot"></span> Edge Online</span>
          </div>
        </div>
      `).join('');
    }
  } catch (err) {
    console.error('Failed to load agents:', err);
  }
}

async function triggerManualSync() {
  const btn = document.getElementById('btn-manual-sync');
  if (btn) btn.disabled = true;
  showToast('Broadcasting embedding synchronization signal...', 'success');

  try {
    const res = await fetch(`${API_BASE}/api/embeddings/sync`, {
      headers: { 'X-API-Key': 'fmp_agent_key_dev_seed_998877665544332211' }
    });
    if (res.ok) {
      const data = await res.json();
      showToast(`Sync complete! ${data.persons ? data.persons.length : 0} active person embeddings synchronized.`, 'success');
      loadDashboard();
    } else {
      showToast('Sync request returned error', 'error');
    }
  } catch (err) {
    showToast('Failed to connect to backend for sync', 'error');
  } finally {
    if (btn) btn.disabled = false;
  }
}
