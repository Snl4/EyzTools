const fs   = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');

// Copy Three.js ES module build into renderer so it works without a bundler
const src  = path.join(root, 'node_modules', 'three', 'build', 'three.module.min.js');
const dest = path.join(root, 'renderer', 'three.module.min.js');

fs.copyFileSync(src, dest);
console.log('✓ three.module.min.js copied to renderer/');
