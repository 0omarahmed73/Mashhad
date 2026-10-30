import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@p-stream/providers': path.resolve(__dirname, '../tools/pstream-providers/lib/index.js'),
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