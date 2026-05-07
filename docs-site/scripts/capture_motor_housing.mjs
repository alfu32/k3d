import { copyFile, mkdir, readdir, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const docsRoot = path.resolve(new URL('..', import.meta.url).pathname);
const repoRoot = path.resolve(docsRoot, '..');
const captureWorkDir = path.join(repoRoot, 'dist/docs-site-captures');
const outputDir = path.join(docsRoot, 'static/images/generated');
const baseUrl = (process.env.MCP_BASE_URL || 'http://127.0.0.1:8765').replace(/\/$/, '');
const delayMs = envInt('MCP_STEP_DELAY_MS', 1200);
const preCaptureDelayMs = envInt('MCP_PRE_CAPTURE_DELAY_MS', 1800);
const pollDelayMs = envInt('MCP_CAPTURE_POLL_DELAY_MS', 250);

function envInt(name, fallback) {
  const parsed = Number.parseInt(process.env[name] ?? '', 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function getJson(pathname) {
  const response = await fetch(`${baseUrl}${pathname}`);
  if (!response.ok) {
    throw new Error(`${pathname} returned ${response.status} ${response.statusText}: ${await response.text()}`);
  }
  return parseMcpJson(await response.text(), pathname);
}

async function postText(pathname, body) {
  const response = await fetch(`${baseUrl}${pathname}`, { method: 'POST', body });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${pathname} returned ${response.status} ${response.statusText}: ${text}`);
  }
  return parseMcpJson(text, pathname);
}

function parseMcpJson(text, context) {
  try {
    return JSON.parse(text);
  } catch (error) {
    const sanitized = text.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, ' ');
    try {
      return JSON.parse(sanitized);
    } catch {
      throw new Error(`Could not parse JSON from ${context}: ${error.message}`);
    }
  }
}

async function executeCommand(id) {
  const result = await getJson(`/scene/command?id=${encodeURIComponent(id)}`);
  if (!result.success) {
    throw new Error(`Command ${id} failed: ${result.message || 'no message'}`);
  }
  await sleep(delayMs);
  return result;
}

async function listCommandsWithFallback() {
  for (const endpoint of ['/scene/listCommands', '/scene/commands']) {
    try {
      const payload = await getJson(endpoint);
      const commands = Array.isArray(payload.commands) ? payload.commands : [];
      console.log(`${endpoint}: ${commands.length} command(s)`);
      if (commands.length > 0) {
        return commands;
      }
    } catch (error) {
      console.warn(`${endpoint} failed: ${error.message}`);
    }
  }
  return [];
}

async function latestPng(directory) {
  const entries = await readdir(directory, { withFileTypes: true }).catch(() => []);
  const pngs = [];
  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.endsWith('.png')) {
      continue;
    }
    const file = path.join(directory, entry.name);
    const info = await stat(file);
    pngs.push({ file, mtimeMs: info.mtimeMs });
  }
  pngs.sort((a, b) => b.mtimeMs - a.mtimeMs);
  return pngs[0]?.file ?? null;
}

function screenshotPathFromResponse(response) {
  const text = [
    response.stdout,
    response.message,
    ...(Array.isArray(response.stdoutLines) ? response.stdoutLines : [])
  ].join('\n');
  return text.match(/(?:[A-Za-z]:\\|\/)[^"\n\r]+?\.png/g)?.at(-1) ?? null;
}

function motorHousingScript() {
  return String.raw`
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.Color
import com.github.alfu32.sketch.model.GroupScene

def v = { x, y, z -> new Vector3(x as float, y as float, z as float) }
def color = { r, g2, b -> new Color(r as float, g2 as float, b as float, 1f) }
def housing = color(0.78, 0.81, 0.77)
def cover = color(0.88, 0.89, 0.84)
def rib = color(0.64, 0.69, 0.64)
def dark = color(0.16, 0.17, 0.18)
def terminal = color(0.54, 0.57, 0.54)
def guide = color(0.08, 0.10, 0.12)

def root = scene.activeGroup()
def current = null
def parts = []

def beginPart = { name ->
  current = [
    name: name,
    tris: new ArrayList(),
    segs: new ArrayList(),
    min: v(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
  ]
}

def touch = { p ->
  current.min.x = Math.min(current.min.x, p.x)
  current.min.y = Math.min(current.min.y, p.y)
  current.min.z = Math.min(current.min.z, p.z)
}

def finishPart = {
  if (current != null && (!current.tris.isEmpty() || !current.segs.isEmpty())) {
    parts.add(current)
  }
  current = null
}

def tri = { a, b, c, col ->
  current.tris.add(new GroupScene.MeshTriangle(a, b, c, col))
  touch(a); touch(b); touch(c)
}
def edge = { a, b ->
  current.segs.add(new GroupScene.MeshSegment(a, b))
  touch(a); touch(b)
}
def quad = { a, b, c, d, col ->
  tri(a, b, c, col); tri(a, c, d, col)
}
def cp = { cx, cz, r, a, y -> v(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r) }

def ring = { cx, cz, ro, ri, y0, y1, col, n ->
  for (int i = 0; i < n; i++) {
    double a0 = Math.PI * 2d * i / n
    double a1 = Math.PI * 2d * (i + 1) / n
    def o0b = cp(cx, cz, ro, a0, y0); def o1b = cp(cx, cz, ro, a1, y0)
    def o0t = cp(cx, cz, ro, a0, y1); def o1t = cp(cx, cz, ro, a1, y1)
    quad(o0b, o1b, o1t, o0t, col)
    if (ri > 0.01) {
      def i0b = cp(cx, cz, ri, a0, y0); def i1b = cp(cx, cz, ri, a1, y0)
      def i0t = cp(cx, cz, ri, a0, y1); def i1t = cp(cx, cz, ri, a1, y1)
      quad(i1b, i0b, i0t, i1t, col)
      quad(o0t, o1t, i1t, i0t, col)
      quad(o1b, o0b, i0b, i1b, col)
    }
  }
}

def box = { cx, cy, cz, sx, sy, sz, col ->
  def x0 = cx - sx / 2f; def x1 = cx + sx / 2f
  def y0 = cy - sy / 2f; def y1 = cy + sy / 2f
  def z0 = cz - sz / 2f; def z1 = cz + sz / 2f
  def p000 = v(x0,y0,z0); def p100 = v(x1,y0,z0); def p110 = v(x1,y1,z0); def p010 = v(x0,y1,z0)
  def p001 = v(x0,y0,z1); def p101 = v(x1,y0,z1); def p111 = v(x1,y1,z1); def p011 = v(x0,y1,z1)
  quad(p000,p100,p110,p010,col); quad(p101,p001,p011,p111,col)
  quad(p001,p000,p010,p011,col); quad(p100,p101,p111,p110,col)
  quad(p010,p110,p111,p011,col); quad(p001,p101,p100,p000,col)
}

def ribBox = { angle, r0, r1, width, y0, y1, col ->
  double ca = Math.cos(angle); double sa = Math.sin(angle)
  double px = -sa; double pz = ca
  def a = v(ca*r0 + px*width/2d, y0, sa*r0 + pz*width/2d)
  def b = v(ca*r1 + px*width/2d, y0, sa*r1 + pz*width/2d)
  def c = v(ca*r1 - px*width/2d, y0, sa*r1 - pz*width/2d)
  def d = v(ca*r0 - px*width/2d, y0, sa*r0 - pz*width/2d)
  def at = v(a.x, y1, a.z); def bt = v(b.x, y1, b.z); def ct = v(c.x, y1, c.z); def dt = v(d.x, y1, d.z)
  quad(a,b,c,d,col); quad(at,dt,ct,bt,col); quad(a,at,bt,b,col); quad(b,bt,ct,c,col); quad(c,ct,dt,d,col); quad(d,dt,at,a,col)
}

// 98 mm max OD -> diameter 19.6 units. 15 mm nominal thickness -> 3 units.
beginPart('Structural outer housing frame - 98 mm maximum diameter')
ring(0f, 0f, 9.8f, 7.9f, -1.5f, 1.5f, housing, 32)
finishPart()

beginPart('Front cover plate and raised outer retaining lip')
ring(0f, 0f, 9.55f, 3.05f, 1.5f, 1.85f, cover, 32)
ring(0f, 0f, 9.95f, 9.45f, 1.28f, 1.72f, cover, 32)
finishPart()

beginPart('Rear cover plate and lower retaining lip')
ring(0f, 0f, 9.55f, 3.05f, -1.85f, -1.5f, cover, 32)
ring(0f, 0f, 9.95f, 9.45f, -1.72f, -1.28f, cover, 32)
finishPart()

beginPart('Central shaft hub boss with 8 mm through bore')
ring(0f, 0f, 2.25f, 0.8f, -2.15f, 2.65f, cover, 24)
ring(0f, 0f, 1.1f, 0.8f, 2.65f, 3.25f, cover, 20)
finishPart()

beginPart('Top stiffening ribs over 80 mm active disc opening')
ring(0f, 0f, 8.0f, 2.25f, 1.85f, 2.08f, rib, 32)
for (int i = 0; i < 10; i++) {
  ribBox(Math.PI * 2d * i / 10d, 3.2f, 7.6f, 0.45f, 2.08f, 2.42f, rib)
}
finishPart()

for (int i = 0; i < 3; i++) {
  double a = -Math.PI / 2d + Math.PI * 2d * i / 3d
  float cx = (float)(Math.cos(a) * 8.4d)
  float cz = (float)(Math.sin(a) * 8.4d)
  beginPart('Mounting foot ' + (i + 1) + ' with 4.5 mm through hole on 84 mm PCD')
  ring(cx, cz, 1.35f, 0.45f, -1.65f, 1.65f, housing, 20)
  box((float)((Math.cos(a) * 9.05d + cx) / 2d), 0f, (float)((Math.sin(a) * 9.05d + cz) / 2d), 1.15f, 2.9f, 0.65f, housing)
  finishPart()
}

beginPart('Side electrical terminal housing and cover lip')
box(10.72f, 0.05f, -2.35f, 2.45f, 2.25f, 2.05f, terminal)
box(11.98f, 0.05f, -2.35f, 0.28f, 1.55f, 1.35f, dark)
box(10.35f, 1.78f, -2.35f, 1.55f, 0.28f, 1.50f, cover)
finishPart()

beginPart('Non-printing reference guides for 80 mm active disc and 84 mm mounting PCD')
for (int i = 0; i < 32; i++) {
  double a0 = Math.PI * 2d * i / 32d
  double a1 = Math.PI * 2d * (i + 1) / 32d
  edge(cp(0f, 0f, 8.0f, a0, 2.48f), cp(0f, 0f, 8.0f, a1, 2.48f))
  edge(cp(0f, 0f, 8.4f, a0, -1.95f), cp(0f, 0f, 8.4f, a1, -1.95f))
}
finishPart()

parts.each { part ->
  def prototype = scene.createImportedPrototype(
    part.name,
    part.tris,
    part.segs,
    part.segs.isEmpty()
  )
  if (prototype != null) {
    scene.createInstanceAtWorld(
      prototype,
      root,
      new Vector3(part.min),
      new Vector3(1f, 0f, 0f),
      new Vector3(0f, 1f, 0f),
      new Vector3(0f, 0f, 1f)
    )
  }
}

save.set("${path.join(captureWorkDir, 'electromotor-housing-98mm-1u-5mm.octd').replace(/\\/g, '\\\\')}")
status.message = "Electromotor housing component objects: 98 mm OD, 15 mm nominal thickness, scale 1 unit = 5 mm"
cameraCtl.setPosition(18f, 12f, 18f)
cameraCtl.setTarget(0f, 0.4f, 0f)
camera.up.set(0f, 1f, 0f)
camera.lookAt(0f, 0.4f, 0f)
camera.update()
null`;
}

async function main() {
  await mkdir(captureWorkDir, { recursive: true });
  await mkdir(outputDir, { recursive: true });

  const status = await getJson('/mcp/status');
  if (!status.success || !status.running) {
    throw new Error(`MCP is not running: ${JSON.stringify(status)}`);
  }

  const commands = await listCommandsWithFallback();
  const screenshotCommand =
    commands.find((command) => command.id === 'export.screenshot') ??
    commands.find((command) => /screenshot/i.test(`${command.id} ${command.name}`));
  if (!screenshotCommand) {
    throw new Error('No screenshot command discovered.');
  }

  await executeCommand('edit.reset_scene_for_capture');
  await sleep(delayMs);

  const scriptResponse = await postText('/scene/console', motorHousingScript());
  if (!scriptResponse.success) {
    throw new Error(`Motor housing script failed: ${scriptResponse.message}`);
  }
  await sleep(delayMs);
  await executeCommand('view.camera.orbit').catch(() => {});
  await sleep(delayMs);

  const before = await latestPng(captureWorkDir);
  await sleep(preCaptureDelayMs);
  const screenshotResponse = await executeCommand(screenshotCommand.id);
  let source = screenshotPathFromResponse(screenshotResponse);
  if (!source) {
    for (let attempt = 0; attempt < 50; attempt += 1) {
      const candidate = await latestPng(captureWorkDir);
      if (candidate && candidate !== before) {
        source = candidate;
        break;
      }
      await sleep(pollDelayMs);
    }
  }
  if (!source) {
    throw new Error('Screenshot command completed, but no PNG was found.');
  }

  const target = path.join(outputDir, 'electromotor-housing-98mm-1u-5mm.png');
  await copyFile(source, target);
  await writeFile(
    path.join(outputDir, 'electromotor-housing-98mm-1u-5mm.capture.json'),
    `${JSON.stringify(
      {
        id: 'electromotor-housing-98mm-1u-5mm',
        file: '/images/generated/electromotor-housing-98mm-1u-5mm.png',
        source: 'mcp-render-buffer',
        generatedAt: new Date().toISOString(),
        appTarget: 'desktop',
        commandsUsed: ['edit.reset_scene_for_capture', 'view.camera.orbit', screenshotCommand.id],
        scriptUsed: 'docs-site/scripts/capture_motor_housing.mjs',
        notes: 'Housing modeled from supplied axial-flux electrostatic motor drawing. Scale: 1 unit = 5 mm; OD 98 mm = 19.6 units; nominal housing thickness 15 mm = 3 units. Components are emitted as separate named object prototypes for easier correction: structural outer housing frame, front cover plate, rear cover plate, central shaft hub boss, top stiffening ribs, three mounting feet, side electrical terminal housing, and reference guides.'
      },
      null,
      2
    )}\n`
  );
  console.log(`Captured ${target}`);
}

main().catch((error) => {
  console.error(`Motor housing capture failed: ${error.message}`);
  process.exit(1);
});
