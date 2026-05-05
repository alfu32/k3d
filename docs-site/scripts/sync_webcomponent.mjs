import { cp, mkdir, readdir, stat, writeFile } from 'node:fs/promises';
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
  const elementScript = path.join(source, 'octodraw-element.js');
  if (!(await exists(elementScript))) {
    throw new Error(
      [
        `Octodraw webcomponent bundle was not found at ${source}.`,
        'Build it first from the repository root:',
        './gradlew :web:prepareWebComponentBundle'
      ].join('\n')
    );
  }

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
