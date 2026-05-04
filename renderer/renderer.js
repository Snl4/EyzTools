// renderer.js – runs in the Electron renderer process.
// Communicates with the main process exclusively via window.electronAPI (preload bridge).

'use strict';

// ── DOM refs ──────────────────────────────────────────────────────────────────
const form         = document.getElementById('packForm');
const modelNameEl  = document.getElementById('modelName');
const cmdEl        = document.getElementById('customModelData');
const itemEl       = document.getElementById('selectedItem');
const dropZone     = document.getElementById('dropZone');
const dropLabel    = document.getElementById('dropLabel');
const fileInput    = document.getElementById('modelFile');
const fileHint     = document.getElementById('fileHint');
const outputPathEl = document.getElementById('outputPath');
const folderBtn    = document.getElementById('folderBtn');
const generateBtn  = document.getElementById('generateBtn');
const statusBanner = document.getElementById('statusBanner');

// ── State ─────────────────────────────────────────────────────────────────────
let modelJsonContent = null; // raw string from uploaded .json

// ── Drag & drop / file upload ─────────────────────────────────────────────────
dropZone.addEventListener('click', () => fileInput.click());

dropZone.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); }
});

dropZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));

dropZone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer?.files?.[0];
  if (file) handleFile(file);
});

fileInput.addEventListener('change', () => {
  if (fileInput.files[0]) handleFile(fileInput.files[0]);
});

function handleFile(file) {
  if (!file.name.toLowerCase().endsWith('.json')) {
    return showStatus('Only .json files are accepted.', false);
  }

  const reader = new FileReader();
  reader.onload = (e) => {
    const text = e.target.result;
    try {
      JSON.parse(text); // quick validation
    } catch {
      showStatus('The selected file is not valid JSON.', false);
      clearFile();
      return;
    }
    modelJsonContent = text;
    dropLabel.textContent = `✔  ${file.name}`;
    dropZone.classList.add('has-file');
    fileHint.textContent = `${(file.size / 1024).toFixed(1)} KB loaded`;
    clearStatus();
  };
  reader.readAsText(file);
}

function clearFile() {
  modelJsonContent = null;
  dropLabel.textContent = 'Drag & drop .json here, or click to browse';
  dropZone.classList.remove('has-file');
  fileHint.textContent = '';
  fileInput.value = '';
}

// ── Output folder picker ──────────────────────────────────────────────────────
folderBtn.addEventListener('click', async () => {
  const selected = await window.electronAPI.chooseOutputFolder();
  if (selected) {
    outputPathEl.value = selected;
    clearStatus();
  }
});

// ── Form submission ────────────────────────────────────────────────────────────
form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const modelName       = modelNameEl.value.trim();
  const customModelData = parseInt(cmdEl.value, 10);
  const selectedItem    = itemEl.value;
  const outputPath      = outputPathEl.value.trim();

  // ── Validation ──────────────────────────────────────────────────────────
  if (!modelName) {
    return showStatus('Model name cannot be empty.', false);
  }
  if (!/^[a-z0-9_]+$/.test(modelName)) {
    return showStatus('Model name may only contain lowercase letters, digits and underscores.', false);
  }
  if (!cmdEl.value || isNaN(customModelData) || customModelData < 1) {
    return showStatus('Custom Model Data must be a positive integer.', false);
  }
  if (!modelJsonContent) {
    return showStatus('Please upload a Blockbench model .json file.', false);
  }
  if (!outputPath) {
    return showStatus('Please choose an output folder.', false);
  }

  // ── Generate ─────────────────────────────────────────────────────────────
  setLoading(true);

  const result = await window.electronAPI.generatePack({
    customModelData,
    modelName,
    selectedItem,
    modelJson: modelJsonContent,
    outputPath,
  });

  showStatus(result.message, result.success);
  setLoading(false);
});

// ── Helpers ───────────────────────────────────────────────────────────────────
function setLoading(on) {
  generateBtn.disabled = on;
  generateBtn.innerHTML = on
    ? '<span class="spinner"></span> Generating…'
    : 'Generate Resource Pack';
}

function showStatus(msg, success) {
  statusBanner.textContent = msg;
  statusBanner.className = `banner ${success ? 'success' : 'error'}`;
}

function clearStatus() {
  statusBanner.textContent = '';
  statusBanner.className = 'banner hidden';
}
