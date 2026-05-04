const { contextBridge, ipcRenderer } = require('electron');

// Expose a safe, minimal API to the renderer — no direct Node access.
contextBridge.exposeInMainWorld('electronAPI', {
  chooseOutputFolder: ()        => ipcRenderer.invoke('choose-output-folder'),
  generatePack:       (payload) => ipcRenderer.invoke('generate-pack', payload),
});
