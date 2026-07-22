# Draft-first lifecycle: every application drafts before it submits

The canonical lifecycle for an application is `DRAFT → SUBMITTED | DISCARDED`. Every application — including those submitted immediately — passes through the draft state. There is no one-shot create-and-submit path as a separate first-class concept.

This was not the obvious choice: an immediate-submit path feels simpler for integrators who don't need staged drafting. We chose draft-first because having two genesis paths (draft-create vs immediate-submit) led to an identity-collision bug — a draft and its submission resolved to different aggregate instances, breaking the draft→submit transition. One lifecycle with one genesis event (`ApplicationDrafted`) removes the ambiguity and gives Civil Apply a consistent pre-submission window for document attachment.

## Consequences

- `POST /applications` always creates a draft; `POST /applications/{id}/submit` is the explicit, separate transition.
- A one-shot convenience (e.g. `?submit=true`) can be added as sugar over this path later without touching the lifecycle model.
- Abandoned drafts require an explicit discard endpoint and a TTL sweep backstop.
