import { defineConfig } from 'vite';
import type { Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = fileURLToPath(new URL('.', import.meta.url));

function mashhadPwa(): Plugin {
  return {
    name: 'mashhad-pwa',
    apply: 'build',
    generateBundle(_options, bundle) {
      const assetUrls = Object.keys(bundle)
        .filter((fileName) => fileName.startsWith('assets/'))
        .map((fileName) => `/${fileName}`);
      const fontUrls = readdirSync(path.resolve(webRoot, 'public/fonts'))
        .filter((fileName) => fileName.endsWith('.woff2'))
        .map((fileName) => `/fonts/${fileName}`);
      const precacheUrls = [
        '/',
        '/offline.html',
        '/manifest.webmanifest',
        '/icons/icon-192.png',
        '/icons/icon-512.png',
        '/icons/icon-maskable-512.png',
        '/icons/apple-touch-icon.png',
        ...fontUrls,
        ...assetUrls,
      ];

      const buildHash = createHash('sha256');
      for (const fileName of Object.keys(bundle).sort()) {
        const output = bundle[fileName];
        buildHash.update(fileName);
        buildHash.update('\0');
        buildHash.update(output.type === 'chunk' ? output.code : output.source);
        buildHash.update('\0');
      }
      for (const url of precacheUrls.filter((entry) => entry !== '/' && !entry.startsWith('/assets/'))) {
        const publicPath = path.resolve(webRoot, 'public', url.slice(1));
        buildHash.update(url);
        buildHash.update('\0');
        buildHash.update(readFileSync(publicPath));
        buildHash.update('\0');
      }

      const source = readFileSync(path.resolve(webRoot, 'src/pwa/sw-template.js'), 'utf8')
        .replace('__BUILD_ID__', buildHash.digest('hex').slice(0, 12))
        .replace('__PRECACHE_URLS__', JSON.stringify(precacheUrls));
      this.emitFile({ type: 'asset', fileName: 'sw.js', source });
    },
  };
}

export default defineConfig({
  plugins: [react(), mashhadPwa()],
  resolve: {
    alias: {
      '@p-stream/providers': path.resolve(webRoot, '../tools/pstream-providers/lib/index.js'),
    },
  },
  server: {
    proxy: {
      '/stream-proxy': {
        target: 'http://127.0.0.1:3001',
        rewrite: (p) => p.replace(/^\/stream-proxy/, ''),
        changeOrigin: true,
      },
    },
  },
});
