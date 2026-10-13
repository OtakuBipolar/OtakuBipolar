#!/usr/bin/env python3
"""Gera a pasta www/ que vai dentro do APK a partir do index.html do app web.

O APK roda sem internet: as fontes do Google Fonts viram @font-face embutido,
e somem o manifesto, os ícones e o service worker, que só servem pro navegador.

Uso: make_www.py <index.html> <pasta-das-fontes> <pasta-de-saída>
"""
import base64
import pathlib
import re
import shutil
import sys

src_html, fonts_dir, out_dir = (pathlib.Path(a) for a in sys.argv[1:4])
html = src_html.read_text(encoding="utf-8")


def remove(pattern, text, flags=0):
    new, n = re.subn(pattern, "", text, flags=flags)
    if n == 0:
        sys.exit(f"make_www: não achei o trecho esperado no index.html: {pattern}")
    return new


# Coisas de navegador/PWA que não existem dentro do APK.
html = remove(r'<link rel="manifest"[^>]*>\n', html)
html = remove(r'<link rel="icon"[^>]*>\n', html)
html = remove(r'<link rel="apple-touch-icon"[^>]*>\n', html)
html = remove(r'<link rel="preconnect"[^>]*>\n', html)
html = remove(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>\n', html)
html = remove(r"\n  /\* PWA:BEGIN \*/.*?/\* PWA:END \*/\n", html, re.S)
html = re.sub(r"<!-- ARTIFACT:[A-Z]+ -->\n", "", html)


def face(family, weight, file):
    data = base64.b64encode((fonts_dir / file).read_bytes()).decode("ascii")
    return (
        f'@font-face {{ font-family: "{family}"; font-style: normal; font-weight: {weight}; '
        f'font-display: block; src: url(data:font/woff2;base64,{data}) format("woff2"); }}'
    )


fonts_css = "\n".join([
    face("Bungee", "400", "Bungee-Regular.latin.woff2"),
    face("Rubik", "300 900", "Rubik-Variable.latin.woff2"),
])
if html.count("<style>") != 1:
    sys.exit("make_www: esperava exatamente um <style> no index.html")
html = html.replace("<style>", f"<style>\n{fonts_css}\n</style>\n<style>", 1)

for leftover in ("fonts.googleapis.com", "fonts.gstatic.com", "serviceWorker", "manifest.webmanifest"):
    if leftover in html:
        sys.exit(f"make_www: sobrou referência a {leftover} no HTML do APK")

if out_dir.exists():
    shutil.rmtree(out_dir)
(out_dir / "licenses").mkdir(parents=True)
(out_dir / "index.html").write_text(html, encoding="utf-8")
for lic in ("OFL-Bungee.txt", "OFL-Rubik.txt"):
    shutil.copy(fonts_dir / lic, out_dir / "licenses" / lic)
print(f"make_www: {out_dir / 'index.html'} ({len(html.encode()) // 1024} KB)")
