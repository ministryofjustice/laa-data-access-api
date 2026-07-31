// Data pool helpers backed by k6's SharedArray.
//
// SharedArray reads the file ONCE at init time and shares the memory across
// every VU — without it, each VU gets its own copy and memory blows up at
// high concurrency. Always use `loadPool` for any non-trivial fixture data.
//
// Files must be JSON arrays. Place them under performance-tests/fixtures/
// and reference them by relative path from the test file.

import { SharedArray } from 'k6/data';

// Loads a JSON-array file into a SharedArray. Returns the array directly.
// Pool name is just a label k6 uses in logs — make it unique per pool.
export const loadPool = (name, path) =>
  new SharedArray(name, () => {
    const parsed = JSON.parse(open(path));
    if (!Array.isArray(parsed)) {
      throw new Error(`loadPool(${name}): expected JSON array at ${path}`);
    }
    return parsed;
  });

// Picks one entry per VU iteration. Cycles deterministically by VU + iteration
// so different VUs hit different rows — useful when you want every row exercised.
export const pickRoundRobin = (pool) => pool[(__VU + __ITER) % pool.length];

// Picks a random entry. Use when row order doesn't matter; gives a more uniform
// distribution at high VU counts than round-robin.
export const pickRandom = (pool) => pool[Math.floor(Math.random() * pool.length)];
