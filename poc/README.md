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

**Baseline: 5/9. Enhanced: 8/9.** See [`output/comparison.md`](output/comparison.md)
for the full per-query breakdown.

- **Q1, Q3** -- both pipelines eventually land on the right electronics
  content, but enhanced returns the exact block (`eligibility_criteria`,
  `exception`) rather than baseline's whole-FAQ chunk.
- **Q2 -- the one enhanced miss, and it's a genuine, useful finding, not a
  scripted failure.** The freshness filter worked exactly as designed: the
  superseded v1 policy (`ret-elec-v1`, 14-day window) was correctly excluded
  from enhanced's results by the `superseded_by == ""` scalar filter. But a
  *different* near-duplicate then won on pure similarity --
  `policy-window-conflict-a` ("Electronics Accessories Return Window") scored
  higher than the actual electronics policy for the query "what's the return
  window for electronics?", because "accessories return window" is lexically
  very close to the query. This is exactly the disambiguation failure mode
  the design set out to fix, showing up in a form the thin POC slice didn't
  fully solve: heading-breadcrumb embeddings help a lot (see Q1), but don't
  guarantee category disambiguation on their own. A fuller build would add
  an explicit category-aware retrieval step (see `docs/design.md` section 7)
  rather than relying on embedding proximity alone.
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
- No query-understanding/NLU layer for category filters -- enhanced relies on
  heading-breadcrumb-prefixed embeddings plus one always-on freshness filter,
  which is precisely what Q2 shows is not yet sufficient on its own.
