import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = normalize(join(fileURLToPath(new URL('..', import.meta.url)), 'dist'))
const port = Number(process.env.PORT || 4173)

const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2'
}

createServer((request, response) => {
  const rawPath = decodeURIComponent((request.url || '/').split('?')[0])
  const safePath = normalize(rawPath).replace(/^(\.\.[/\\])+/, '')
  let filePath = join(root, safePath === '/' ? 'index.html' : safePath)

  if (!filePath.startsWith(root) || !existsSync(filePath) || statSync(filePath).isDirectory()) {
    filePath = join(root, 'index.html')
  }

  response.setHeader('Content-Type', mimeTypes[extname(filePath)] || 'application/octet-stream')
  createReadStream(filePath).pipe(response)
}).listen(port, () => {
  console.log(`PLM preview: http://localhost:${port}/`)
  console.log(`PLM preview: http://127.0.0.1:${port}/`)
})
