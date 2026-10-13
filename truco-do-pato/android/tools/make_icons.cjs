// Desenha os ícones do APK a partir da mesma cabeça de pato do app web.
// Uso: PLAYWRIGHT=/caminho/do/playwright node tools/make_icons.cjs
// Só precisa rodar quando o desenho mudar; os PNGs ficam versionados em res/.
const path = require('path');
const fs = require('fs');
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright');

const RES = path.join(__dirname, '..', 'res');

// Cabeça do pato no sistema de coordenadas do desenho original (viewBox 200x200).
const HEAD = `
  <circle cx="122" cy="72" r="40" fill="#ffc928"/>
  <ellipse cx="134" cy="45" rx="14" ry="7" fill="#ffe27a" opacity=".8" transform="rotate(-22 134 45)"/>
  <path d="M114 34c-5-14 8-20 11-8 2-10 15-9 10 6" fill="none" stroke="#f2a900" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <ellipse cx="134" cy="88" rx="7" ry="4.2" fill="#ff8f6b" opacity=".55"/>
  <path d="M146 92h30c9 0 10 5 4 8-12 6-28 4-34 0z" fill="#e8700f"/>
  <path d="M145 77c15-7 37-5 45 4 3 4-3 9-13 9l-32 3z" fill="#ff8a1f"/>
  <ellipse cx="171" cy="80" rx="2.4" ry="1.4" fill="#a8480a"/>
  <path d="M117 46c8-5 17-6 26-2" fill="none" stroke="#d98f00" stroke-width="3.4" stroke-linecap="round"/>
  <path d="M112 57 86 63" stroke="#0b0b0c" stroke-width="4" stroke-linecap="round"/>
  <path d="M106 53h60" stroke="#0b0b0c" stroke-width="6" stroke-linecap="round"/>
  <path d="M109 54h33l-2 13c-1 5-5 7-11 7h-9c-6 0-9-3-10-8z" fill="#0b0b0c"/>
  <path d="M147 54h17l-1 12c-1 4-3 6-7 6h-3c-4 0-6-3-6-7z" fill="#0b0b0c"/>
  <path d="M141 58h7" stroke="#0b0b0c" stroke-width="3.5"/>
  <g stroke="#fff" stroke-linecap="round" fill="none" opacity=".85">
    <path d="M116 58l7 10" stroke-width="2.6"/>
    <path d="M124 58l3 4" stroke-width="2"/>
    <path d="M151 58l4 7" stroke-width="2"/>
  </g>`;

// Silhueta pra ícone temático (Android 13+): o sistema pinta tudo que não é transparente.
// Os óculos ficam vazados, com o brilho das lentes de volta.
const MONO_MASK = `
  <mask id="m" maskUnits="userSpaceOnUse" x="0" y="0" width="200" height="200">
    <circle cx="122" cy="72" r="40" fill="#fff"/>
    <path d="M114 34c-5-14 8-20 11-8 2-10 15-9 10 6" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M146 92h30c9 0 10 5 4 8-12 6-28 4-34 0z" fill="#fff"/>
    <path d="M145 77c15-7 37-5 45 4 3 4-3 9-13 9l-32 3z" fill="#fff"/>
    <path d="M147 91.5h31" stroke="#000" stroke-width="2.2" stroke-linecap="round"/>
    <path d="M112 57 86 63" stroke="#000" stroke-width="4" stroke-linecap="round"/>
    <path d="M106 53h60" stroke="#000" stroke-width="6" stroke-linecap="round"/>
    <path d="M109 54h33l-2 13c-1 5-5 7-11 7h-9c-6 0-9-3-10-8z" fill="#000"/>
    <path d="M147 54h17l-1 12c-1 4-3 6-7 6h-3c-4 0-6-3-6-7z" fill="#000"/>
    <path d="M141 58h7" stroke="#000" stroke-width="3.5"/>
    <g stroke="#fff" stroke-linecap="round" fill="none">
      <path d="M116 58l7 10" stroke-width="2.6"/>
      <path d="M151 58l4 7" stroke-width="2"/>
    </g>
    <path d="M86 63l-3 1" stroke="#fff" stroke-width="4" stroke-linecap="round"/>
  </mask>
  <rect width="200" height="200" fill="#fff" mask="url(#m)"/>`;

const FELT = `
  <defs>
    <radialGradient id="felt" cx="50%" cy="35%" r="75%">
      <stop offset="0" stop-color="#14603f"/>
      <stop offset="1" stop-color="#072a1d"/>
    </radialGradient>
  </defs>`;

// Camadas do ícone adaptativo: 108dp, desenhadas a 432px (xxxhdpi). Zona segura = círculo de 66dp.
const ADAPTIVE_HEAD = 'translate(216 220) scale(1.95) translate(-136 -66)';
const svg = (size, view, body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${view} ${view}">${body}</svg>`;

const LEGACY_HEAD = 'translate(236 274) scale(3.2) translate(-122 -72)';
const legacy = (shape) => `${FELT}${shape}<g transform="${LEGACY_HEAD}">${HEAD}</g>`;
const SQUARE = '<rect x="16" y="16" width="480" height="480" rx="104" fill="url(#felt)"/>';
const ROUND = '<circle cx="256" cy="256" r="240" fill="url(#felt)"/>';

const jobs = [
  ['drawable-nodpi/ic_launcher_background.png', svg(432, 432, `${FELT}<rect width="432" height="432" fill="url(#felt)"/>`)],
  ['drawable-nodpi/ic_launcher_foreground.png', svg(432, 432, `<g transform="${ADAPTIVE_HEAD}">${HEAD}</g>`)],
  ['drawable-nodpi/ic_launcher_monochrome.png', svg(432, 432, `<g transform="${ADAPTIVE_HEAD}">${MONO_MASK}</g>`)],
];
for (const [dpi, px] of [['mdpi', 48], ['hdpi', 72], ['xhdpi', 96], ['xxhdpi', 144], ['xxxhdpi', 192]]) {
  jobs.push([`mipmap-${dpi}/ic_launcher.png`, svg(px, 512, legacy(SQUARE))]);
  jobs.push([`mipmap-${dpi}/ic_launcher_round.png`, svg(px, 512, legacy(ROUND))]);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [file, markup] of jobs) {
    const size = Number(markup.match(/width="(\d+)"/)[1]);
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<html><body style="margin:0;background:transparent">${markup}</body></html>`);
    const out = path.join(RES, file);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await page.screenshot({ path: out, omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
    console.log('icone', file, `${size}px`);
  }
  await browser.close();
})();
