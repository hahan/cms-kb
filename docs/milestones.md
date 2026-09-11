# POC Milestones & Tasks

Scope: prove out the design in [`design.md`](design.md) end-to-end on a small,
representative sample of real content. This is a planning document only —
nothing here is implemented yet.

Out of scope for the POC: production CMS webhook integration, full-scale
graph/vector infra, review-queue UI, and any performance/scale hardening.
Those are productionization follow-ups after the POC validates the approach.

---

## Milestone 0 — Sample corpus & schema sign-off

- [ ] Pull a representative sample set of real KB content: ~15–20 HTML
      articles, ~5–8 PDFs (mix of native-text and at least 1–2 scanned/OCR
      cases), ~3–5 TXT files. Cover policy, FAQ, and SOP doc types across at
      least 3 categories (e.g. returns, price-match, delivery).
- [ ] Manually walk 3–4 sample docs through the `KBDocument` schema by hand to
      sanity-check the block taxonomy and entity taxonomy against real
      content before building anything automated.
- [ ] Finalize `block_type` enum and `entity_type` enum against what the
      sample corpus actually contains (add/trim values as needed).
- [ ] Decide POC access method for source content (static file export from
      CMS vs. read-only API) — no live webhook integration needed yet.

## Milestone 1 — HTML extraction adapter

- [ ] DOM parse + strip noise: `<script>`/`<style>`/nav/footer/cookie
      widgets/inline attributes; decode HTML entities.
- [ ] Convert tables → markdown/key-value text; convert lists → ordered plain
      text preserving sequence.
- [ ] Heuristic block-type classifier from tag structure (headings, lists,
      callout patterns) + LLM fallback for ambiguous freeform sections.
- [ ] Stable `block_id` generation (content-hash based) — verify idempotency:
      re-running the same unchanged document twice yields identical
      `block_id`s; an edited block yields a new one without shifting the IDs
      of unrelated blocks.
- [ ] Emit `KBDocument` JSON; validate every output against
      `schemas/kb-document.schema.json` automatically.
- [ ] Produce both `text` and `html` (sanitized fragment) per block; visually
      spot-check `html` renders correctly for a handful of docs.

## Milestone 2 — PDF & TXT adapters

- [ ] PDF: native text extraction; layout heuristics (font size/weight,
      indentation) for heading/list/step detection; table extraction; capture
      `source_ref` (`page_number`, `bbox`).
- [ ] PDF: OCR fallback path for scanned/image-based PDFs; populate
      `ocr_applied`/`ocr_confidence`.
- [ ] TXT: regex/pattern-based block segmentation (blank-line paragraphs,
      numbered-line patterns, ALL-CAPS heading guesses); capture `source_ref`
      (`line_range`).
- [ ] Extend the shared classifier to accept `pdf_layout` and
      `plaintext_pattern` block sources with appropriate confidence scoring.
- [ ] Implement the OCR review-routing rule: any block from an `ocr_applied`
      doc carrying a `dollar_threshold`/`time_window` entity is flagged
      regardless of classifier confidence.

## Milestone 3 — Entity & relation extraction

- [ ] Entity extraction pass (dollar thresholds, time windows, required
      documents, product categories, membership tiers) with confidence
      scores.
- [ ] Structural relation extraction (auto-derived, high trust): `SUPERSEDES`
      from version history, `PART_OF` from block nesting, `EXCEPTION_TO`/
      `ESCALATES_TO` from block-type adjacency.
- [ ] LLM-inferred relation extraction (lower trust): `REFERENCES`,
      `CONTRADICTS` — with confidence score attached to every inferred edge.
- [ ] Implement review-routing rules (low-confidence block classification,
      low-confidence inferred relations, OCR+numeric-entity combo,
      `sensitivity: restricted`) as a simple flagged-output list — a full
      review UI is out of scope for the POC.

## Milestone 4 — Milvus indexing

- [ ] Define the Milvus collection schema: vector field + scalar fields
      (`doc_id`, `doc_type`, `category`, `region`, `block_type`, `step_no`,
      `effective_date`, `version`, `sensitivity`).
- [ ] Chunk-to-embedding pipeline: heading-breadcrumb injection into `text`
      before embedding, embedding model selection, batch insert.
- [ ] Pre-insert noise guardrail (residual-tag check, non-alpha ratio check,
      length sanity check) — run against real extracted chunks and confirm it
      catches injected bad cases.
- [ ] Hybrid retrieval query path: scalar filter (category/region/freshness)
      combined with vector search; evaluate sparse+dense hybrid if available
      in the target Milvus version.
- [ ] Build a small labeled query set (~15–20 realistic customer-service
      queries with known correct source doc/block) and measure precision
      against a naive baseline (raw-HTML fixed-window chunking, no schema, no
      filtering) to quantify the improvement.

## Milestone 5 — Knowledge graph

- [ ] Choose a lightweight graph store for POC scale (e.g. Neo4j Community,
      or an in-memory graph library — full production graph DB selection is
      a post-POC decision).
- [ ] Graph writer: nodes from documents/blocks/entities, edges from the
      `relations` array.
- [ ] Incremental update on doc re-extraction (full rebuild is acceptable at
      POC scale; the incremental diff logic itself should still be exercised
      at least once to validate the approach).
- [ ] Build 2–3 example multi-hop queries that demonstrate value beyond
      vector search alone (e.g. "what does this SOP require me to check
      first," "find policies flagged as contradicting each other," "find all
      SOPs superseded in the last 90 days").

## Milestone 6 — Agentic retrieval demo

- [ ] Minimal orchestrator: takes a customer-style query → extracts
      category/region/product context → hybrid retrieval from Milvus → graph
      expansion for related/exception/escalation blocks → composes an answer
      with a real citation (`html` fragment or PDF page reference).
- [ ] Demonstrate against 5–10 representative customer-service scenarios
      spanning policy lookup, FAQ lookup, and SOP-with-escalation.

## Milestone 7 — Evaluation & learnings report

- [ ] Run the same query set against the naive raw-HTML-RAG baseline and the
      full pipeline; compare precision, citation quality, and correct
      handling of staleness/region/category disambiguation.
- [ ] Document findings, gaps, and a recommendation for what changes before
      productionization: CMS webhook integration, review-queue UI, graph/
      vector DB choice at production scale, and any schema/taxonomy
      adjustments discovered from real content during the POC.

---

## Suggested sequencing

Milestones 0–1 establish the pattern on the easiest format (HTML). Milestone 2
extends it to PDF/TXT once the pattern is proven — don't parallelize adapter
work before Milestone 1 validates the shared classifier/schema against real
content. Milestones 3–5 can run partially in parallel once Milestone 1 output
exists. Milestone 6 is the integration point; Milestone 7 closes the loop back
to the open questions in `design.md`.
