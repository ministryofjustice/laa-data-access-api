import { writeFileSync } from 'node:fs';

import { stepRegistry } from '../lib/step-registry.js';

function list(values) {
  return values.length === 0 ? 'None' : values.map((value) => `\`${value}\``).join(', ');
}

export function renderCatalogue(registry = stepRegistry) {
  const sections = registry.map(
    (step) => `## ${step.canonicalName}

| Metadata | Value |
| --- | --- |
| Actor | ${step.actor} |
| Inputs | ${list(step.inputs)} |
| Outputs | ${list(step.outputs)} |
| HTTP interaction | \`${step.endpoint}\` |
| Expected observable result | ${step.assertion} |
| Tags | ${list(step.tags)} |
`,
  );
  return `# k6 step catalogue

This file is generated from \`k6/lib/step-registry.js\`. Regenerate it with
\`node k6/scripts/generate-step-catalogue.js\`; validate it with
\`node k6/scripts/validate-documentation.js\`.

${sections.join('\n')}
`;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  writeFileSync(new URL('../STEP_CATALOGUE.md', import.meta.url), renderCatalogue());
}
