import { cp, mkdir, readdir, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const docsRoot = process.cwd();
const defaultSource = path.resolve(docsRoot, '../web/build/dist/webcomponent');
const source = path.resolve(process.argv[2] || process.env.OCTODRAW_WEBCOMPONENT_DIR || defaultSource);
const target = path.resolve(docsRoot, 'static/webcomponent');

async function exists(filePath) {
  try {
    await stat(filePath);
    return true;
  } catch {
    return false;
  }
}

async function main() {
  const requiredFiles = [
    'octodraw-element.js',
    'octodraw-runtime.js',
    'scripts/gdx.wasm.js',
    'assets/assets.txt'
  ];
  const missingFiles = [];
  for (const file of requiredFiles) {
    if (!(await exists(path.join(source, file)))) {
      missingFiles.push(file);
    }
  }

  if (missingFiles.length > 0) {
    throw new Error(
      [
        `Octodraw webcomponent bundle at ${source} is incomplete.`,
        `Missing: ${missingFiles.join(', ')}`,
        'Build it first from the repository root:',
        './gradlew :web:prepareWebComponentBundle'
      ].join('\n')
    );
  }

  await rm(target, { recursive: true, force: true });
  await mkdir(target, { recursive: true });

  const entries = await readdir(source, { withFileTypes: true });
  for (const entry of entries) {
    const from = path.join(source, entry.name);
    const to = path.join(target, entry.name);
    await cp(from, to, { recursive: true, force: true });
  }

  await writeFile(path.join(target, '.gitkeep'), '\n');
  console.log(`Synced Octodraw webcomponent bundle from ${source} to ${target}`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
