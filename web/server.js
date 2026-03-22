// =============================================================================
// Serveur HTTP pour le frontend NestSpend (production locale)
// Sert les fichiers statiques Angular et proxifie les appels /api/ vers le backend.
// =============================================================================

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 4200;
const API_HOST = 'localhost';
const API_PORT = 8080;
const STATIC_DIR = path.join(__dirname, 'nestspend-web/dist/nestspend-web/browser');

// Types MIME courants
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.eot': 'application/vnd.ms-fontobject',
  '.map': 'application/json',
};

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url);
  const pathname = parsedUrl.pathname;

  // Proxy des appels API vers le backend Spring Boot
  if (pathname.startsWith('/api/')) {
    // Le backend Spring Boot expose ses routes sous /api/ directement
    // On transmet le chemin complet (pas de suppression du préfixe /api)
    const backendPath = pathname;
    const options = {
      hostname: API_HOST,
      port: API_PORT,
      path: backendPath + (parsedUrl.search || ''),
      method: req.method,
      headers: { ...req.headers, host: `${API_HOST}:${API_PORT}` },
    };

    const proxyReq = http.request(options, (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res, { end: true });
    });

    proxyReq.on('error', (err) => {
      console.error('Erreur proxy API:', err.message);
      res.writeHead(502);
      res.end(JSON.stringify({ error: 'Bad Gateway', detail: err.message }));
    });

    req.pipe(proxyReq, { end: true });
    return;
  }

  // Fichiers statiques Angular
  let filePath = path.join(STATIC_DIR, pathname === '/' ? 'index.html' : pathname);

  // Vérifier si le fichier existe
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    // SPA routing : renvoyer index.html pour toutes les routes Angular
    filePath = path.join(STATIC_DIR, 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('404 Not Found');
      return;
    }
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`NestSpend Web Server démarré sur http://localhost:${PORT}`);
  console.log(`Proxy API : /api/* -> http://${API_HOST}:${API_PORT}/*`);
  console.log(`Fichiers statiques : ${STATIC_DIR}`);
});
