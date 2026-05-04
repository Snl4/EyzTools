// With contextIsolation: false the preload runs in the same JS context as the
// renderer, so we expose the Electron IPC API via a simple window assignment.
const { ipcRenderer } = require('electron');

window.electronAPI = {
  chooseOutputFolder: ()        => ipcRenderer.invoke('choose-output-folder'),
  generatePack:       (payload) => ipcRenderer.invoke('generate-pack', payload),
};
