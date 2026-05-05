import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const tutorialDir = new URL('../static/tutorials/', import.meta.url);
const knownActionTypes = new Set(['none', 'command', 'script', 'camera', 'loadScene', 'highlight']);
const knownSuccessTypes = new Set(['manual', 'command-active', 'scene-state', 'selection']);
const errors = [];
const ids = new Set();

function fail(file, message) {
  errors.push(`${file}: ${message}`);
}

function requireField(file, object, field, type) {
  if (!(field in object)) {
    fail(file, `missing required field "${field}"`);
    return;
  }
  if (type && typeof object[field] !== type) {
    fail(file, `field "${field}" must be ${type}`);
  }
}

for (const file of readdirSync(tutorialDir).filter((name) => name.endsWith('.json')).sort()) {
  const path = join(tutorialDir.pathname, file);
  let tutorial;
  try {
    tutorial = JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    fail(file, `invalid JSON: ${error.message}`);
    continue;
  }

  for (const [field, type] of [
    ['id', 'string'],
    ['title', 'string'],
    ['level', 'string'],
    ['estimatedMinutes', 'number'],
    ['initialScene', 'string']
  ]) {
    requireField(file, tutorial, field, type);
  }

  if (typeof tutorial.id === 'string') {
    if (ids.has(tutorial.id)) {
      fail(file, `duplicate tutorial id "${tutorial.id}"`);
    }
    ids.add(tutorial.id);
  }

  if (!Array.isArray(tutorial.steps) || tutorial.steps.length === 0) {
    fail(file, 'steps must be a non-empty array');
    continue;
  }

  tutorial.steps.forEach((step, index) => {
    const label = `${file} step ${index + 1}`;
    for (const [field, type] of [
      ['id', 'string'],
      ['title', 'string'],
      ['body', 'string']
    ]) {
      requireField(label, step, field, type);
    }

    const actions = step.actions ?? [];
    if (!Array.isArray(actions)) {
      fail(label, 'actions must be an array when present');
    } else {
      actions.forEach((action, actionIndex) => {
        if (!knownActionTypes.has(action.type)) {
          fail(label, `action ${actionIndex + 1} has unknown type "${action.type}"`);
        }
      });
    }

    const success = step.success ?? [];
    if (!Array.isArray(success)) {
      fail(label, 'success must be an array when present');
    } else {
      success.forEach((condition, conditionIndex) => {
        if (!knownSuccessTypes.has(condition.type)) {
          fail(label, `success condition ${conditionIndex + 1} has unknown type "${condition.type}"`);
        }
      });
    }
  });
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

console.log(`Validated ${ids.size} tutorial JSON file(s).`);
