import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { stepRegistry } from '../lib/step-registry.js';

const REQUIRED_METADATA = [
  'canonicalName',
  'actor',
  'inputs',
  'outputs',
  'endpoint',
  'assertion',
  'tags',
];

function featureSteps(feature) {
  return [...feature.matchAll(/^\s*(?:Given|When|Then|And|But)\s+(.+?)\s*$/gm)].map(
    ([, step]) => step,
  );
}

export function validateDocumentation({ registry, catalogue, features }) {
  const errors = [];
  const names = new Set();

  registry.forEach((step) => {
    REQUIRED_METADATA.forEach((field) => {
      if (step[field] === undefined || step[field] === null || step[field].length === 0) {
        errors.push(`Registered step ${step.canonicalName || 'metadata'} is missing ${field}.`);
      }
    });
    if (names.has(step.canonicalName)) {
      errors.push(`Registered step ${step.canonicalName} is duplicated.`);
    }
    names.add(step.canonicalName);
    if (!catalogue.includes(`## ${step.canonicalName}\n`)) {
      errors.push(`Registered step ${step.canonicalName} is absent from the catalogue.`);
    }
  });

  features.flatMap(featureSteps).forEach((step) => {
    if (!names.has(step)) {
      errors.push(`Feature references unknown canonical step: ${step}.`);
    }
  });

  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }
}

function filesIn(directory) {
  try {
    return readdirSync(directory)
      .filter((file) => file.endsWith('.feature'))
      .map((file) => readFileSync(join(directory, file), 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return [];
    }
    throw error;
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const k6Directory = new URL('..', import.meta.url);
  const catalogue = readFileSync(new URL('../STEP_CATALOGUE.md', import.meta.url), 'utf8');
  validateDocumentation({
    registry: stepRegistry,
    catalogue,
    features: filesIn(new URL('../features', k6Directory)),
  });
  process.stdout.write('k6 step documentation is valid.\n');
}
