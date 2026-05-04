# EyzTools – Minecraft Resource Pack Generator

A lightweight Tauri desktop app that generates a valid Minecraft Java Edition resource pack with **CustomModelData** support.

## Features

- Set a Custom Model Data value and model name
- Choose any Minecraft item from the dropdown
- Upload your Blockbench-exported `.json` model (drag & drop supported)
- Pick an output folder
- One-click generation of the complete resource pack folder structure

## Generated Structure

```
<output>/
├── pack.mcmeta
└── assets/
    └── minecraft/
        ├── models/
        │   └── item/
        │       ├── {model_name}.json       ← your uploaded model
        │       └── {selected_item}.json    ← override with predicate
        └── textures/
            └── item/                       ← drop your textures here
```

## Prerequisites

| Tool | Version |
|------|---------|
| Rust | 1.80+ |
| Node.js | 18+ |
| npm | 9+ |
| Tauri CLI | 2.x |

### Windows extra requirements

- [Microsoft C++ Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/) (MSVC toolchain)
- WebView2 (included in Windows 10/11, or install the Evergreen Bootstrapper)

## Setup & Run

```bash
# 1. Install JS dependencies
npm install

# 2. Run in development mode (hot-reload)
npm run dev

# 3. Build production .exe
npm run build
```

The installer and `.exe` will be in `src-tauri/target/release/bundle/`.

## Tauri Architecture

```
src/           ← Frontend (Vanilla JS + CSS)
src-tauri/
  src/
    main.rs    ← Entry point
    lib.rs     ← Tauri commands (Rust filesystem logic)
  tauri.conf.json
  Cargo.toml
```

## Pack Format

`pack_format: 15` targets **Minecraft 1.20 – 1.20.1**. Adjust in `lib.rs → write_pack()` for other versions:

| pack_format | MC Version |
|-------------|-----------|
| 13 | 1.19.4 |
| 15 | 1.20 – 1.20.1 |
| 18 | 1.20.2 |
| 22 | 1.20.3 – 1.20.4 |
| 34 | 1.21 |
