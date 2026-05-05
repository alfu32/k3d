import { mkdir, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const docsRoot = path.resolve(new URL('..', import.meta.url).pathname);
const generatedDir = path.join(docsRoot, 'src/lib/data/generated');
const baseUrl = resolveBaseUrl();

function resolveBaseUrl() {
  const argIndex = process.argv.findIndex((arg) => arg === '--base-url');
  if (argIndex >= 0 && process.argv[argIndex + 1]) {
    return process.argv[argIndex + 1].replace(/\/$/, '');
  }
  return (process.env.MCP_BASE_URL || 'http://127.0.0.1:8765').replace(/\/$/, '');
}

async function getJson(pathname) {
  const response = await fetch(`${baseUrl}${pathname}`);
  if (!response.ok) {
    throw new Error(`${pathname} returned ${response.status} ${response.statusText}`);
  }
  return response.json();
}

async function listCommandsWithFallback() {
  try {
    const primary = await getJson('/scene/listCommands');
    const primaryCommands = Array.isArray(primary.commands) ? primary.commands : [];
    if (primaryCommands.length > 0) {
      return { endpoint: '/scene/listCommands', commands: primaryCommands };
    }
  } catch (error) {
    console.warn(`/scene/listCommands failed, trying /scene/commands: ${error.message}`);
  }
  const fallback = await getJson('/scene/commands');
  const fallbackCommands = Array.isArray(fallback.commands) ? fallback.commands : [];
  return { endpoint: '/scene/commands', commands: fallbackCommands };
}

function normalizeCommand(command) {
  return {
    id: String(command.id ?? ''),
    name: String(command.name ?? ''),
    category: String(command.category ?? 'Uncategorized'),
    description: String(command.description ?? ''),
    icon: command.icon == null ? null : String(command.icon),
    priority: Number.isFinite(Number(command.priority)) ? Number(command.priority) : null,
    tags: Array.isArray(command.tags) ? command.tags.map((tag) => String(tag)) : []
  };
}

function summarizeCategories(commands) {
  const counts = new Map();
  for (const command of commands) {
    counts.set(command.category, (counts.get(command.category) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .map(([category, count]) => ({ category, count }))
    .sort((a, b) => a.category.localeCompare(b.category));
}

async function writeJson(fileName, payload) {
  await mkdir(generatedDir, { recursive: true });
  const outputPath = path.join(generatedDir, fileName);
  await writeFile(`${outputPath}.tmp`, `${JSON.stringify(payload, null, 2)}\n`);
  await rename(`${outputPath}.tmp`, outputPath);
}

async function main() {
  const status = await getJson('/mcp/status');
  if (!status.success || !status.running) {
    throw new Error(`MCP is not running at ${baseUrl}`);
  }
  const catalog = await listCommandsWithFallback();
  const commands = catalog.commands
    .map(normalizeCommand)
    .filter((command) => command.id)
    .sort((a, b) => a.category.localeCompare(b.category) || a.id.localeCompare(b.id));
  const generatedAt = new Date().toISOString();
  const common = {
    schemaVersion: 1,
    generatedAt,
    source: {
      baseUrl,
      endpoint: catalog.endpoint,
      status
    }
  };
  await writeJson('command_catalog.json', {
    ...common,
    commandCount: commands.length,
    categories: summarizeCategories(commands),
    commands
  });
  const tools = commands.filter((command) => command.category === 'Tools' || command.id.startsWith('tool.'));
  await writeJson('tool_catalog.json', {
    ...common,
    toolCount: tools.length,
    tools
  });
  console.log(`Generated ${commands.length} command records and ${tools.length} tool records from ${catalog.endpoint}.`);
}

main().catch((error) => {
  console.error(`Command reference generation failed: ${error.message}`);
  process.exit(1);
});
