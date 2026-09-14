# kb-poc

Working implementation of the POC scoped in [`../docs/poc-scope.md`](../docs/poc-scope.md):
a side-by-side comparison of the naive baseline (cleaned HTML -> fixed-window
chunks -> Milvus, vector-only) against the enhanced design (LLM-based block
segmentation + governance metadata + hybrid filter/vector retrieval + an
in-memory relationship graph), run against the same 11-document synthetic
corpus and the same fixed query set.

## Stack

- **Java 21 + Maven**
- **Milvus 2.4.14 standalone**, via Docker Compose (`docker/docker-compose.yml`).
  Note: the official compose file's `minio/minio` image is gated on Docker
  Hub as of 2024 (licensing change) -- this one is patched to pull
  `quay.io/minio/minio` instead.
- **Embeddings**: `sentence-transformers/all-MiniLM-L6-v2`, run locally via
  DJL + ONNX Runtime. No API key, fully offline after the first model
  download. Both pipelines use the *same* embedding model, so any quality
  difference between them comes from chunking/structure, not a stronger model.
- **Block segmentation + entity extraction** (enhanced pipeline only): live
  calls to the Anthropic Messages API (`claude-sonnet-5`). Requires
  `ANTHROPIC_API_KEY` in `poc/.env` (git-ignored -- never commit this file).
- **Graph**: JGraphT, in-memory, built from `corpus/relations.json` -- no
  graph database for this POC, per `poc-scope.md`.

## Running it

1. Start Milvus: `cd docker && docker compose up -d`
2. Put your Anthropic key in `poc/.env`: `ANTHROPIC_API_KEY=sk-ant-...`
3. From `poc/`:
   ```
   mvn -q exec:java -Dexec.mainClass=com.walmart.kbpoc.pipeline.IngestBaseline
   mvn -q exec:java -Dexec.mainClass=com.walmart.kbpoc.pipeline.IngestEnhanced
   mvn -q exec:java -Dexec.mainClass=com.walmart.kbpoc.pipeline.RunComparison
   ```
4. Result: `output/comparison.md` (also printed to stdout).

## Corpus

`src/main/resources/corpus/` -- 11 synthetic HTML documents (with realistic
nav/footer/script noise) plus:
- `metadata.json` -- the CMS envelope (category, region, effective_date,
  version, supersedes/supersededBy, sensitivity) that a real CMS publish
  workflow would already carry. The baseline pipeline deliberately ignores
  everything here except `title`/`sourceUrl`.
- `relations.json` -- hand-authored `SUPERSEDES` / `REFERENCES` /
  `CONTRADICTS` edges, standing in for what a real CMS's version history and
  internal links would already provide structurally.

## Latest result

**Baseline: 6/9. Enhanced: 9/9.** See [`output/comparison.md`](output/comparison.md)
for the full per-query breakdown, and [`../docs/learnings.md`](../docs/learnings.md)
for the reasoning behind each fix below.

- **Category-aware filter (`CategoryRouter.java`) fixed Q2.** The freshness
  filter alone wasn't enough to stop a near-duplicate sub-category
  ("Electronics > Accessories") from outscoring the correct general
  electronics policy on raw similarity. A deterministic keyword-based query
  router -- if the query doesn't mention a sub-category's keywords, exclude
  results tagged with it -- fixed this with no regressions elsewhere. It's a
  hardcoded rule tied to this corpus's taxonomy, not a general solution; see
  `docs/learnings.md` for the honest limitation.

- **Q1's scoring was corrected after inspecting the actual retrieved text.**
  It originally showed baseline FAILing because its top hit was
  `faq-returns-elec` rather than the policy/SOP docs I'd pre-approved. But
  that FAQ text is a genuinely correct answer ("order lookup can substitute
  for a receipt... escalate per the Electronics Returns SOP for high-value
  items") -- the named trap for Q1 (marketplace/general confusion) never
  actually happened; baseline correctly ranked both of those out. The FAIL
  was an artifact of too strict an accepted-answer list, not a real retrieval
  failure, so `faq-returns-elec` was added to Q1's (and, for consistency,
  Q3's) accepted set in `QuerySet.java` and the comparison was regenerated.
  Same reasoning applies to Q3, though it didn't change that query's result.
- One nuance that's real but intentionally *not* scored: on Q1, enhanced's
  top-ranked block is the general `eligibility_criteria` clause (0.610),
  narrowly ahead of the more specific `exception` block (0.585) that
  actually addresses "no receipt." Both belong to the correct document, so
  this passes on the doc-level check the harness uses -- but it's a good
  illustration that block-level ranking isn't perfect either. Scoring
  pass/fail at the block level (not just the document level) would be a
  reasonable next refinement rather than something to paper over here.
- **Q2 -- originally the one enhanced miss, now fixed, kept here for the
  history.** The freshness filter worked exactly as designed: the
  superseded v1 policy (`ret-elec-v1`, 14-day window) was correctly excluded
  from enhanced's results by the `superseded_by == ""` scalar filter. But a
  *different* near-duplicate then won on pure similarity --
  `policy-window-conflict-a` ("Electronics Accessories Return Window") scored
  higher than the actual electronics policy for the query "what's the return
  window for electronics?", because "accessories return window" is lexically
  very close to the query. Heading-breadcrumb embeddings alone didn't
  guarantee category disambiguation. This is exactly what `CategoryRouter.java`
  (see above) was built to fix, and it now passes.
- **Q5, Q6** -- baseline cannot answer these at all; there's no vector-search
  equivalent of "what contradicts this" or "what references this." Enhanced
  answers both correctly via graph traversal.
- **Q7-Q9** -- both pipelines get the easy, unambiguous lookups right, as
  expected -- the comparison isn't rigged to make baseline look bad on
  everything.

## Known simplifications (intentional, see `docs/poc-scope.md`)

- PDF/TXT ingestion, OCR, a real graph database, the human review queue, and
  CMS webhook integration are all out of scope here -- see `docs/milestones.md`
  for the full build.
- Block `html` field is a normalized re-wrap (semantic wrapper + entity
  bolding), not a verbatim slice of the original markup.
- `CategoryRouter.java` is a hardcoded keyword rule, not an NLU layer or
  learned classifier -- it fixes the one disambiguation failure mode found
  so far, but won't generalize to unseen categories or phrasings without
  someone maintaining the keyword map. See `docs/learnings.md`.
