import { invoke } from '@tauri-apps/api/core';
import { open } from '@tauri-apps/plugin-dialog';

// ── DOM refs ──────────────────────────────────────────────────
const form          = document.getElementById('packForm');
const modelNameEl   = document.getElementById('modelName');
const cmdEl         = document.getElementById('customModelData');
const itemEl        = document.getElementById('selectedItem');
const dropZone      = document.getElementById('dropZone');
const dropLabel     = document.getElementById('dropLabel');
const fileInput     = document.getElementById('modelFile');
const fileHint      = document.getElementById('fileHint');
const outputPathEl  = document.getElementById('outputPath');
const folderBtn     = document.getElementById('folderBtn');
const generateBtn   = document.getElementById('generateBtn');
const statusBanner  = document.getElementById('statusBanner');

// ── State ──────────────────────────────────────────────────────
let modelJsonContent = null; // string content of the uploaded .json

// ── Drag & drop / file upload ──────────────────────────────────
dropZone.addEventListener('click', () => fileInput.click());

dropZone.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    fileInput.click();
  }
});

dropZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => {
  dropZone.classList.remove('drag-over');
});

dropZone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  const file = e.dataTransfer?.files?.[0];
  if (file) handleFile(file);
});

fileInput.addEventListener('change', () => {
  const file = fileInput.files?.[0];
  if (file) handleFile(file);
});

function handleFile(file) {
  if (!file.name.endsWith('.json')) {
    showStatus('Only .json files are accepted.', false);
    return;
  }

  const reader = new FileReader();
  reader.onload = (e) => {
    const text = e.target.result;
    // Quick sanity check: must be valid JSON
    try {
      JSON.parse(text);
    } catch {
      showStatus('The selected file is not valid JSON.', false);
      clearFile();
      return;
    }
    modelJsonContent = text;
    dropLabel.textContent = `✔ ${file.name}`;
    dropZone.classList.add('has-file');
    fileHint.textContent = `${(file.size / 1024).toFixed(1)} KB loaded`;
    clearStatus();
  };
  reader.readAsText(file);
}

function clearFile() {
  modelJsonContent = null;
  dropLabel.textContent = 'Drag & drop your .json here, or click to browse';
  dropZone.classList.remove('has-file');
  fileHint.textContent = '';
  fileInput.value = '';
}

// ── Output folder picker ───────────────────────────────────────
folderBtn.addEventListener('click', async () => {
  try {
    const selected = await open({ directory: true, multiple: false, title: 'Choose output folder' });
    if (selected && typeof selected === 'string') {
      outputPathEl.value = selected;
      clearStatus();
    }
  } catch (err) {
    showStatus(`Could not open folder dialog: ${err}`, false);
  }
});

// ── Form submission ────────────────────────────────────────────
form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const modelName       = modelNameEl.value.trim();
  const customModelData = parseInt(cmdEl.value, 10);
  const selectedItem    = itemEl.value;
  const outputPath      = outputPathEl.value.trim();

  // ── Validation
  if (!modelName) {
    return showStatus('Model name cannot be empty.', false);
  }
  if (!/^[a-z0-9_]+$/.test(modelName)) {
    return showStatus('Model name may only contain lowercase letters, digits and underscores.', false);
  }
  if (!cmdEl.value || isNaN(customModelData) || customModelData < 1) {
    return showStatus('Custom Model Data must be a positive number.', false);
  }
  if (!modelJsonContent) {
    return showStatus('Please upload a Blockbench model .json file.', false);
  }
  if (!outputPath) {
    return showStatus('Please choose an output folder.', false);
  }

  // ── Generate
  setLoading(true);

  try {
    const result = await invoke('generate_resource_pack', {
      payload: {
        custom_model_data: customModelData,
        model_name: modelName,
        selected_item: selectedItem,
        model_json: modelJsonContent,
        output_path: outputPath,
      },
    });

    showStatus(result.message, result.success);
  } catch (err) {
    showStatus(`Unexpected error: ${err}`, false);
  } finally {
    setLoading(false);
  }
});

// ── Helpers ────────────────────────────────────────────────────
function setLoading(loading) {
  generateBtn.disabled = loading;
  generateBtn.innerHTML = loading
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
