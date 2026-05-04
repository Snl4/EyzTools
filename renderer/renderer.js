// nodeIntegration: true — we can use require() directly in the renderer.
const THREE = require('three');

// ── Global drag prevention ────────────────────────────────────────────────────
// Without this Electron navigates away when a file is dropped outside a zone.
document.addEventListener('dragover', (e) => e.preventDefault());
document.addEventListener('drop',     (e) => e.preventDefault());

// ══════════════════════════════════════════════════════════════════════════════
// 3D Preview
// ══════════════════════════════════════════════════════════════════════════════

class ModelPreview {
  constructor(containerId) {
    this.container  = document.getElementById(containerId);
    this.modelGroup = new THREE.Group();
    this.isDragging = false;
    this.autoRotate = true;
    this.lastMouse  = { x: 0, y: 0 };
    this.loadedTex  = null;

    this._initRenderer();
    this._initLights();
    this._initControls();
    this._showPlaceholder();
    this._animate();

    new ResizeObserver(() => this._resize()).observe(this.container);
  }

  _initRenderer() {
    const w = this.container.clientWidth  || 400;
    const h = this.container.clientHeight || 400;

    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.setSize(w, h);
    this.renderer.setClearColor(0x22252f);
    this.container.appendChild(this.renderer.domElement);

    this.scene  = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 2000);
    this.camera.position.set(36, 28, 36);
    this.camera.lookAt(0, 0, 0);

    const grid = new THREE.GridHelper(32, 8, 0x2e3240, 0x22252f);
    grid.position.y = -8.5;
    this.scene.add(grid);
    this.scene.add(this.modelGroup);
  }

  _initLights() {
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.55));
    const sun = new THREE.DirectionalLight(0xffffff, 0.85);
    sun.position.set(1, 2, 1.5);
    this.scene.add(sun);
    const fill = new THREE.DirectionalLight(0x8899ff, 0.3);
    fill.position.set(-1, -0.5, -1);
    this.scene.add(fill);
  }

  _initControls() {
    const el = this.renderer.domElement;

    el.addEventListener('mousedown', (e) => {
      this.isDragging = true;
      this.autoRotate = false;
      this.lastMouse  = { x: e.clientX, y: e.clientY };
    });
    window.addEventListener('mouseup', () => { this.isDragging = false; });

    el.addEventListener('mousemove', (e) => {
      if (!this.isDragging) return;
      const dx = e.clientX - this.lastMouse.x;
      const dy = e.clientY - this.lastMouse.y;
      this.modelGroup.rotation.y += dx * 0.008;
      this.modelGroup.rotation.x = Math.max(
        -Math.PI / 2,
        Math.min(Math.PI / 2, this.modelGroup.rotation.x + dy * 0.008),
      );
      this.lastMouse = { x: e.clientX, y: e.clientY };
    });

    el.addEventListener('wheel', (e) => {
      e.preventDefault();
      const s = 1 + e.deltaY * 0.001;
      this.camera.position.multiplyScalar(Math.max(0.3, Math.min(3, s)));
    }, { passive: false });

    // Double-click resets rotation and resumes auto-spin
    el.addEventListener('dblclick', () => {
      this.modelGroup.rotation.set(0, 0, 0);
      this.autoRotate = true;
    });
  }

  // ── Public API ──────────────────────────────────────────────────────────────

  loadModel(jsonString) {
    let model;
    try { model = JSON.parse(jsonString); } catch { return; }

    this._clearModel();

    const elements = model.elements ?? [];
    if (elements.length === 0) {
      this._showPlaceholder();
      setPreviewStatus('No geometry — showing placeholder');
      return;
    }

    elements.forEach((el) => this._addElement(el));
    setPreviewStatus(`${elements.length} element${elements.length !== 1 ? 's' : ''} loaded`);
  }

  applyTexture(dataUrl) {
    const loader = new THREE.TextureLoader();
    this.loadedTex = loader.load(dataUrl, (tex) => {
      tex.magFilter = THREE.NearestFilter;
      tex.minFilter = THREE.NearestFilter;
      this._applyTexToAll(tex);
    });
  }

  clearTexture() {
    this.loadedTex = null;
    this.modelGroup.traverse((obj) => {
      if (!obj.isMesh) return;
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
      mats.forEach((m) => {
        m.map = null; m.color.set(0x56c172);
        m.wireframe = false; m.transparent = false; m.opacity = 1;
        m.needsUpdate = true;
      });
    });
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  _showPlaceholder() {
    this._clearModel();
    const geo = new THREE.BoxGeometry(16, 16, 16);
    const mat = new THREE.MeshPhongMaterial({
      color: 0x56c172, wireframe: true, transparent: true, opacity: 0.45,
    });
    this.modelGroup.add(new THREE.Mesh(geo, mat));
    setPreviewStatus('No model loaded');
  }

  _clearModel() {
    while (this.modelGroup.children.length) {
      const child = this.modelGroup.children[0];
      child.traverse((o) => {
        o.geometry?.dispose();
        (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => m?.dispose());
      });
      this.modelGroup.remove(child);
    }
  }

  _addElement(el) {
    const [x1, y1, z1] = el.from;
    const [x2, y2, z2] = el.to;
    const w = x2 - x1, h = y2 - y1, d = z2 - z1;
    if (w <= 0 || h <= 0 || d <= 0) return;

    const geo = new THREE.BoxGeometry(w, h, d);
    const mat = this.loadedTex
      ? new THREE.MeshLambertMaterial({ map: this.loadedTex })
      : new THREE.MeshLambertMaterial({ color: 0x56c172, transparent: true, opacity: 0.85 });

    const mesh = new THREE.Mesh(geo, mat);
    const cx = x1 + w / 2 - 8;
    const cy = y1 + h / 2 - 8;
    const cz = z1 + d / 2 - 8;

    if (el.rotation) {
      const { angle, axis, origin: o } = el.rotation;
      const rad   = (angle * Math.PI) / 180;
      const pivot = new THREE.Object3D();
      pivot.position.set(o[0] - 8, o[1] - 8, o[2] - 8);
      pivot.rotation[axis] = rad;
      mesh.position.set(cx - (o[0] - 8), cy - (o[1] - 8), cz - (o[2] - 8));
      pivot.add(mesh);
      this.modelGroup.add(pivot);
    } else {
      mesh.position.set(cx, cy, cz);
      this.modelGroup.add(mesh);
    }
  }

  _applyTexToAll(tex) {
    this.modelGroup.traverse((obj) => {
      if (!obj.isMesh) return;
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
      mats.forEach((m) => {
        m.map = tex; m.color.set(0xffffff);
        m.wireframe = false; m.transparent = false; m.opacity = 1;
        m.needsUpdate = true;
      });
    });
  }

  _animate() {
    requestAnimationFrame(() => this._animate());
    if (this.autoRotate) this.modelGroup.rotation.y += 0.004;
    this.renderer.render(this.scene, this.camera);
  }

  _resize() {
    const w = this.container.clientWidth;
    const h = this.container.clientHeight;
    if (!w || !h) return;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  }
}

const preview = new ModelPreview('previewCanvas');

// ══════════════════════════════════════════════════════════════════════════════
// UI — form logic and event listeners
// ══════════════════════════════════════════════════════════════════════════════

const form             = document.getElementById('packForm');
const modelNameEl      = document.getElementById('modelName');
const cmdEl            = document.getElementById('customModelData');
const itemEl           = document.getElementById('selectedItem');
const modelDropZone    = document.getElementById('modelDropZone');
const modelDropLabel   = document.getElementById('modelDropLabel');
const modelFileInput   = document.getElementById('modelFile');
const modelFileHint    = document.getElementById('modelFileHint');
const textureDropZone  = document.getElementById('textureDropZone');
const textureDropLbl   = document.getElementById('textureDropLabel');
const textureFileInput = document.getElementById('textureFile');
const textureThumb     = document.getElementById('textureThumb');
const textureImg       = document.getElementById('textureImg');
const textureClearBtn  = document.getElementById('textureClear');
const outputPathEl     = document.getElementById('outputPath');
const folderBtn        = document.getElementById('folderBtn');
const generateBtn      = document.getElementById('generateBtn');
const statusBanner     = document.getElementById('statusBanner');

let modelJsonContent = null;
let textureBase64    = null;
let textureName      = null;

// ── Model file ────────────────────────────────────────────────────────────────
// <label for="modelFile"> handles clicks natively — only keyboard fallback needed
modelDropZone.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); modelFileInput.click(); }
});
modelDropZone.addEventListener('dragover', (e) => {
  e.stopPropagation(); e.preventDefault(); modelDropZone.classList.add('drag-over');
});
modelDropZone.addEventListener('dragleave', () => modelDropZone.classList.remove('drag-over'));
modelDropZone.addEventListener('drop', (e) => {
  e.stopPropagation(); e.preventDefault(); modelDropZone.classList.remove('drag-over');
  const f = e.dataTransfer?.files?.[0];
  if (f) handleModelFile(f);
});
modelFileInput.addEventListener('change', () => {
  if (modelFileInput.files[0]) handleModelFile(modelFileInput.files[0]);
});

function handleModelFile(file) {
  if (!file.name.toLowerCase().endsWith('.json'))
    return showStatus('Only .json model files are accepted.', false);
  const reader = new FileReader();
  reader.onload = (e) => {
    const text = e.target.result;
    try { JSON.parse(text); } catch {
      showStatus('The model file is not valid JSON.', false);
      clearModelFile();
      return;
    }
    modelJsonContent = text;
    modelDropLabel.textContent = `✔  ${file.name}`;
    modelDropZone.classList.add('has-file');
    modelFileHint.textContent  = `${(file.size / 1024).toFixed(1)} KB`;
    clearStatus();
    preview.loadModel(text);
  };
  reader.readAsText(file);
}

function clearModelFile() {
  modelJsonContent = null;
  modelDropLabel.textContent = 'Drag & drop .json here, or click to browse';
  modelDropZone.classList.remove('has-file');
  modelFileHint.textContent = '';
  modelFileInput.value = '';
}

// ── Texture file ──────────────────────────────────────────────────────────────
textureDropZone.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); textureFileInput.click(); }
});
textureDropZone.addEventListener('dragover', (e) => {
  e.stopPropagation(); e.preventDefault(); textureDropZone.classList.add('drag-over');
});
textureDropZone.addEventListener('dragleave', () => textureDropZone.classList.remove('drag-over'));
textureDropZone.addEventListener('drop', (e) => {
  e.stopPropagation(); e.preventDefault(); textureDropZone.classList.remove('drag-over');
  const f = e.dataTransfer?.files?.[0];
  if (f) handleTextureFile(f);
});
textureFileInput.addEventListener('change', () => {
  if (textureFileInput.files[0]) handleTextureFile(textureFileInput.files[0]);
});
textureClearBtn.addEventListener('click', () => clearTextureFile());

function handleTextureFile(file) {
  if (!/\.(png|jpe?g)$/i.test(file.name))
    return showStatus('Only .png / .jpg texture files are accepted.', false);
  const reader = new FileReader();
  reader.onload = (e) => {
    const dataUrl  = e.target.result;
    textureBase64  = dataUrl.split(',')[1];
    textureName    = file.name;
    textureImg.src = dataUrl;
    textureThumb.classList.remove('hidden');
    textureDropLbl.textContent = `✔  ${file.name}`;
    textureDropZone.classList.add('has-file');
    clearStatus();
    preview.applyTexture(dataUrl);
  };
  reader.readAsDataURL(file);
}

function clearTextureFile() {
  textureBase64 = null; textureName = null;
  textureImg.src = '';
  textureThumb.classList.add('hidden');
  textureDropLbl.textContent = 'Drop .png or click';
  textureDropZone.classList.remove('has-file');
  textureFileInput.value = '';
  preview.clearTexture();
}

// ── Output folder ─────────────────────────────────────────────────────────────
folderBtn.addEventListener('click', async () => {
  const selected = await window.electronAPI.chooseOutputFolder();
  if (selected) { outputPathEl.value = selected; clearStatus(); }
});

// ── Generate ──────────────────────────────────────────────────────────────────
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const modelName       = modelNameEl.value.trim();
  const customModelData = parseInt(cmdEl.value, 10);
  const selectedItem    = itemEl.value;
  const outputPath      = outputPathEl.value.trim();

  if (!modelName)
    return showStatus('Model name cannot be empty.', false);
  if (!/^[a-z0-9_]+$/.test(modelName))
    return showStatus('Model name: lowercase letters, digits and underscores only.', false);
  if (!cmdEl.value || isNaN(customModelData) || customModelData < 1)
    return showStatus('Custom Model Data must be a positive integer.', false);
  if (!modelJsonContent)
    return showStatus('Please upload a Blockbench .json model file.', false);
  if (!outputPath)
    return showStatus('Please choose an output folder.', false);

  setLoading(true);
  const result = await window.electronAPI.generatePack({
    customModelData, modelName, selectedItem,
    modelJson: modelJsonContent, outputPath,
    textureBase64, textureName,
  });
  showStatus(result.message, result.success);
  setLoading(false);
});

// ── Helpers ───────────────────────────────────────────────────────────────────
function setLoading(on) {
  generateBtn.disabled  = on;
  generateBtn.innerHTML = on
    ? '<span class="spinner"></span> Generating…'
    : 'Generate Resource Pack';
}
function showStatus(msg, success) {
  statusBanner.textContent = msg;
  statusBanner.className   = `banner ${success ? 'success' : 'error'}`;
}
function clearStatus() {
  statusBanner.textContent = '';
  statusBanner.className   = 'banner hidden';
}
function setPreviewStatus(msg) {
  const el = document.getElementById('previewStatus');
  if (el) el.textContent = msg;
}
