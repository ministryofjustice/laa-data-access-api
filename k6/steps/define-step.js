const REQUIRED_METADATA = [
  'canonicalName',
  'actor',
  'inputs',
  'outputs',
  'endpoint',
  'assertion',
  'tags',
];

function filenameFromCanonicalName(canonicalName) {
  return canonicalName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export function defineStep(metadata, execute) {
  REQUIRED_METADATA.forEach((field) => {
    if (metadata[field] === undefined || metadata[field] === null) {
      throw new Error(`Step ${metadata.canonicalName || 'metadata'} is missing ${field}`);
    }
  });

  return Object.freeze({
    ...metadata,
    filename: filenameFromCanonicalName(metadata.canonicalName),
    execute,
  });
}
