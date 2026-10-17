#!/usr/bin/env python3
"""Repackage the existing AstroEmpire Android project with map regression fixes."""
from pathlib import Path
from tempfile import NamedTemporaryFile
from zipfile import ZipFile
import os

archive = Path("AstroEmpire_Android_Project.zip")
fallback = """
// Android WebView touchmove fallback: keep one-finger map panning independent
// of pointer capture, while preserving the existing two-finger pinch handler.
let aeOneFingerDrag = null;
viewport.addEventListener('touchstart', e => {
  if(e.touches.length !== 1 || uiHit(e.target)){
    aeOneFingerDrag = null;
    return;
  }
  const t = e.touches[0];
  aeOneFingerDrag = {id:t.identifier, sx:t.clientX, sy:t.clientY, wx:x, wy:y};
}, {passive:false});
viewport.addEventListener('touchmove', e => {
  if(!aeOneFingerDrag || e.touches.length !== 1 || pinchPointers.size >= 2) return;
  const t = e.touches[0];
  if(t.identifier !== aeOneFingerDrag.id) return;
  const dx = t.clientX - aeOneFingerDrag.sx;
  const dy = t.clientY - aeOneFingerDrag.sy;
  if(Math.hypot(dx,dy) <= 4) return;
  e.preventDefault();
  panMoved = true;
  x = aeOneFingerDrag.wx + dx * cameraPanFactor;
  y = aeOneFingerDrag.wy + dy * cameraPanFactor;
  render();
}, {passive:false});
viewport.addEventListener('touchend', e => {
  if(!e.touches.length) aeOneFingerDrag = null;
}, {passive:false});
viewport.addEventListener('touchcancel', () => {
  aeOneFingerDrag = null;
}, {passive:false});
"""

map_style = """
<style id="ae-map-input-and-visibility-fix">
/* Dim the unknown regions without erasing planets and map markers. */
#fogCanvas { opacity:.32 !important; pointer-events:none !important; }
#miniFog { opacity:.35 !important; pointer-events:none !important; }
/* Player ships and interactive markers must stay above the fog canvas. */
#scoutLayer { z-index:42 !important; }
#asteroidLayer { z-index:36 !important; }
/* Empty map layers must not steal one-finger gestures. */
#planetLayer { pointer-events:none !important; }
#planetLayer .planetHit { pointer-events:auto !important; }
#viewport, #world { touch-action:none !important; }
</style>
"""

def replace_once(s, old, new):
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one anchor, got {count}: {old[:80]}")
    return s.replace(old, new, 1)

def patch_html(s):
    if 'id="ae-map-input-and-visibility-fix"' in s:
        raise RuntimeError("Archive already patched")
    anchor = "viewport.addEventListener('wheel', e => { e.preventDefault(); const delta = e.deltaY < 0 ? 0.12 : -0.12; setZoom(scale + delta, e.clientX, e.clientY); }, {passive:false});"
    s = replace_once(s, anchor, anchor + "\n" + fallback)
    s = replace_once(s, "</body>", map_style + "\n</body>")
    for expected in ("function spawnInitialScouts()", "function buildPlanets()", "function updateWorldCulling(", "function drawFog("):
        if expected not in s:
            raise RuntimeError("Missing original gameplay function " + expected)
    return s

modified = 0
with NamedTemporaryFile(dir=archive.parent, prefix="astro-zip-", suffix=".zip", delete=False) as temp:
    tmp_name = temp.name
try:
    with ZipFile(archive, 'r') as zin, ZipFile(tmp_name, 'w') as zout:
        for info in zin.infolist():
            raw = zin.read(info)
            if info.filename.endswith("/app/src/main/assets/index.html"):
                html = raw.decode("utf-8")
                updated = patch_html(html)
                raw = updated.encode("utf-8")
                modified += 1
            zout.writestr(info, raw)
    if modified != 1:
        raise RuntimeError(f"Expected 1 HTML asset, found {modified}")
    os.replace(tmp_name, archive)
finally:
    if os.path.exists(tmp_name):
        os.remove(tmp_name)
print(f"Patched {modified} game asset in {archive}")
