# EyzTools – Minecraft Resource Pack Generator

Electron desktop app for generating Minecraft Java Edition resource packs with **CustomModelData** support. No Rust required — just Node.js.

## Generated pack structure

```
<output>/
├── pack.mcmeta
└── assets/minecraft/
    ├── models/item/
    │   ├── {model_name}.json       ← your Blockbench model
    │   └── {selected_item}.json    ← override with predicate
    └── textures/item/              ← drop your textures here manually
```

## Requirements

| Tool | Version |
|------|---------|
| Node.js | 18 LTS+ |
| npm | 9+ |

## Run in development

```bash
npm install
npm run dev        # or: npm start
```

## Build Windows installer (.exe)

```bash
npm install
npm run build
```

Output: `dist/EyzTools Setup 1.0.0.exe`

## Project structure

```
├── main.js          ← Electron main process (IPC handlers, fs logic)
├── preload.js       ← Context bridge — exposes safe API to renderer
├── renderer/
│   ├── index.html   ← UI
│   ├── renderer.js  ← Frontend logic & validation
│   └── styles.css   ← Dark theme
├── assets/
│   └── icon.ico     ← App icon
└── package.json     ← electron-builder config
```

## pack_format reference

| pack_format | Minecraft version |
|-------------|-------------------|
| 13 | 1.19.4 |
| 15 | 1.20 – 1.20.1 |
| 18 | 1.20.2 |
| 22 | 1.20.3 – 1.20.4 |
| 34 | 1.21 |

Edit `pack_format` in `main.js → ipcMain.handle('generate-pack', ...)`.
