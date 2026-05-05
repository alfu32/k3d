import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const manifestPath = join(root, 'static/images/generated/manifest.json');
const baseUrl = resolveBaseUrl();

function resolveBaseUrl() {
  const argIndex = process.argv.findIndex((arg) => arg === '--base-url');
  if (argIndex >= 0 && process.argv[argIndex + 1]) {
    return process.argv[argIndex + 1].replace(/\/$/, '');
  }
  return (process.env.MCP_BASE_URL || 'http://127.0.0.1:8765').replace(/\/$/, '');
}

async function getJson(path) {
  const response = await fetch(`${baseUrl}${path}`);
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function listCommandsWithFallback() {
  try {
    const primary = await getJson('/scene/listCommands');
    const primaryCommands = Array.isArray(primary.commands) ? primary.commands : [];
    if (primaryCommands.length > 0) {
      return { endpoint: '/scene/listCommands', payload: primary, commands: primaryCommands };
    }
  } catch (error) {
    console.warn(`/scene/listCommands failed, trying /scene/commands: ${error.message}`);
  }
  const fallback = await getJson('/scene/commands');
  const fallbackCommands = Array.isArray(fallback.commands) ? fallback.commands : [];
  return { endpoint: '/scene/commands', payload: fallback, commands: fallbackCommands };
}

function loadManifest() {
  try {
    return JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch {
    return {
      schemaVersion: 1,
      images: [],
      lastMcpProbe: null,
      notes: 'Generated screenshots are produced by local MCP authoring scripts.'
    };
  }
}

function writeManifest(manifest) {
  mkdirSync(dirname(manifestPath), { recursive: true });
  writeFileSync(`${manifestPath}.tmp`, `${JSON.stringify(manifest, null, 2)}\n`);
  renameSync(`${manifestPath}.tmp`, manifestPath);
}

function findCaptureCommands(commands) {
  const pattern = /(screenshot|capture|render.?buffer|render|png)/i;
  return commands.filter((command) => {
    const text = [command.id, command.name, command.description, ...(command.tags ?? [])].join(' ');
    return pattern.test(text);
  });
}

try {
  const status = await getJson('/mcp/status');
  if (!status.success || !status.running) {
    throw new Error(`MCP is not running at ${baseUrl}`);
  }

  const catalog = await listCommandsWithFallback();
  const commands = catalog.commands;
  const captureCommands = findCaptureCommands(commands);
  const manifest = loadManifest();
  manifest.lastMcpProbe = {
    generatedAt: new Date().toISOString(),
    baseUrl,
    status,
    commandEndpoint: catalog.endpoint,
    commandCount: commands.length,
    likelyCaptureCommands: captureCommands.map((command) => ({
      id: command.id,
      name: command.name,
      description: command.description,
      tags: command.tags ?? []
    }))
  };
  manifest.notes =
    'This script intentionally discovers capture commands at runtime. Wire the actual capture call after confirming the command result shape for the current build.';
  writeManifest(manifest);

  if (commands.length === 0) {
    console.log('MCP is available, but both /scene/listCommands and /scene/commands returned no commands.');
    console.log('Manifest updated with the probe result. No image files were generated.');
  } else if (captureCommands.length === 0) {
    console.log(`MCP is available; ${commands.length} commands were discovered through ${catalog.endpoint}.`);
    console.log('No likely screenshot/render-buffer commands were discovered.');
    console.log('Manifest updated with the probe result. No image files were generated.');
  } else {
    console.log(`Likely capture commands discovered through ${catalog.endpoint}:`);
    for (const command of captureCommands) {
      console.log(`- ${command.id}: ${command.name}`);
    }
    console.log('Next step: wire one discovered command to generate PNG files and append image records.');
  }
} catch (error) {
  console.error(`MCP capture probe failed: ${error.message}`);
  console.error('Start Octodraw locally, enable MCP, and retry with --base-url http://127.0.0.1:8765.');
  process.exit(1);
}
