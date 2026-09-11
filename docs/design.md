# Agentic Knowledge Base — Design Document

## Status
Design + POC planning stage. Nothing in this repo is implemented yet.

## 1. Goal

Walmart's CMS hosts Knowledge Base content — policies, FAQs, and SOPs — used by
customer service agents (human and, increasingly, AI agents) to resolve customer
issues. We want to build the retrieval/reasoning layer that lets an **agentic**
system answer specific customer queries against this content reliably: correct,
current, scoped to the right policy variant, and traceable back to source.

## 2. Constraints

- **Authoring experience must not change.** Authors keep writing content exactly
  as they do today. No new fields to fill in, no manual tagging.
- **No schema is hand-edited.** Every piece of structure/metadata used downstream
  must be *derived*, never authored.
- **Source CMS is classical HTML-body authoring** — a flat HTML body field per
  article, not a structured/headless content model. There is no native block
  structure to lean on; block boundaries must be recovered from markup.
- **Source content isn't only HTML.** Some SOPs and reference material exist as
  PDF or plain TXT files.
- We plan to use **Milvus** as the vector database for RAG.
- We want a **knowledge graph** over this content to support relationship-aware,
  multi-hop agentic reasoning (not just similarity search).

## 3. Why plain vector RAG over raw HTML is not sufficient

1. **False precision from semantic similarity.** "Return policy" for electronics,
   marketplace items, and general merchandise reads as semantically similar text
   but has materially different rules. Pure vector search will confidently
   retrieve the wrong variant.
2. **Staleness.** Policies get revised. Without effective-dates/versioning, an
   old and new version of the same policy can both sit in the index and either
   could be retrieved.
3. **Chunking destroys procedural structure.** SOPs are step-by-step with
   preconditions and exceptions. Naive fixed-token chunking can sever a step
   from its precondition, and an agent then executes a truncated instruction
   with full confidence.
4. **No query-context scoping.** Region, membership tier, channel, and product
   category are structured context a real query carries — pure text similarity
   doesn't exploit this; you want to filter first, search second.
5. **No auditability.** Policy-driven answers need a citation defensible in a
   dispute: "this exact clause, version N, approved on date Y." Raw text chunks
   carry no such provenance.
6. **Raw HTML noise pollutes embeddings.** Tags, attributes, inline styles,
   nav/footer boilerplate, and encoded entities consume embedding capacity on
   tokens with no semantic signal, and repeated boilerplate across articles
   creates false-positive similarity clusters.

**Conclusion:** treat this as a hybrid structured + semantic retrieval problem,
not a pure semantic search problem.

## 4. Core architectural pattern: derived index, not a second authored source

```
   authored source (single source of truth, untouched)
   ┌───────────────────────────────────────────┐
   │  HTML body (classical CMS)  /  PDF  /  TXT │
   └───────────────────────────────────────────┘
                     │  publish / update event
                     ▼
        ┌─────────────────────────────┐
        │  Extraction / Enrichment     │
        │  Pipeline (format-specific   │
        │  adapter → shared classifier)│
        └─────────────────────────────┘
                     │  regenerates automatically, idempotent
                     ▼
        ┌─────────────────────────────┐
        │   KBDocument index (JSON)    │   ← schemas/kb-document.schema.json
        │   text + html + entities +   │
        │   relations + governance     │
        └─────────────────────────────┘
                     │
     ┌───────────────┼────────────────┬─────────────────┐
     ▼                ▼                ▼                 ▼
  Milvus         Knowledge Graph   Display/Citation   Governance/QA
 (RAG retrieval)  (multi-hop        (agent-facing       (freshness,
                   reasoning)        formatted answer)   contradiction
                                                          detection,
                                                          review routing)
```

The HTML/PDF/TXT file stays the one artifact authors and the CMS version and
diff. The JSON index is a **materialized view** — mechanically regenerated on
every publish, never maintained by hand, never a second thing that can drift
out of sync through human effort (or lack of it).

**Operational commitment this pattern requires:** the extraction pipeline must
be reliable and idempotent — the same source content re-run must produce the
same `block_id`s, or every relation/citation built on top of the index rots
silently on every republish. This needs the same rigor as a data-migration/ETL
system: monitoring, re-run safety, staleness detection.

## 5. Document schema — `KBDocument`

See [`schemas/kb-document.schema.json`](../schemas/kb-document.schema.json)
(JSON Schema) and [`schemas/kb-document.example.json`](../schemas/kb-document.example.json)
(worked example: a "Processing Electronics Returns" SOP).

Four parts:

1. **Document envelope** — `doc_id`, `doc_type`, `title`, `category`, `audience`,
   `channel`, `region`, `source_format`. Reused from fields the CMS's publish
   workflow already captures wherever possible.
2. **Governance block** — `owner`, `approval_status`, `effective_date`/
   `expiration_date`, `version`, `supersedes`/`superseded_by`, `sensitivity`.
   Reused from the existing approval/versioning workflow.
3. **Ordered content blocks** — typed as `eligibility_criteria` /
   `procedure_steps` / `step` / `exception` / `escalation` / `qa_pair` /
   `table` / `disclaimer`. Each block carries **two parallel representations**:
   - `text` — fully stripped, clean text. This is what gets embedded in
     Milvus. Never used for display.
   - `html` — sanitized formatted fragment (divs/headers/bold/lists/tables
     preserved), or a reconstructed equivalent for PDF/TXT. Used to render
     citations and agent-facing answers with original formatting. Never
     embedded, never indexed.

   `block_source` records how the block's type/boundaries were determined,
   since none of the three source formats hand over pre-tagged blocks:
   - `html_heuristic` — DOM tag structure + LLM fallback for ambiguous markup
   - `pdf_layout` — font size/weight, indentation, bounding-box grouping + LLM
   - `plaintext_pattern` — regex/LLM only, weakest signal, routed to review
     more aggressively by default

   `source_ref` gives a provenance pointer back to the exact source location
   (`page_number`/`bbox` for PDF, `line_range` for TXT) — real, defensible
   citation ("Policy Manual, p.12"), not just a paraphrase.

4. **Entities + relations** — structured facts (dollar thresholds, time
   windows, required documents) and graph edges (`SUPERSEDES`, `PART_OF`,
   `EXCEPTION_TO`, `ESCALATES_TO`, `REFERENCES`, `CONTRADICTS`), each edge
   tagged with a `source` (`cms_structural` / `component_derived` /
   `llm_inferred`) and a confidence score, so structurally-certain edges are
   trusted outright and only semantically-inferred edges get reviewed.

## 6. Multi-format ingestion (HTML / PDF / TXT)

A format router dispatches to a per-format adapter; all adapters converge on
the same `KBDocument` output contract. What differs is how much structural
signal is available to the shared block-type classifier:

| | HTML (classical) | PDF | TXT |
|---|---|---|---|
| Structure signal | Tag semantics (`<h2>`, `<ul>`, `<table>`) — real but noisy | Layout heuristics: font size/weight, indentation, bounding boxes, numbered patterns | None — blank lines + regex patterns only |
| Display fragment | Sanitized `html` fragment, direct | Reconstructed from layout, or page-image snippet for citation | Low-fidelity reconstructed markup |
| Special risk | Theme/template drift breaking tag heuristics | **Scanned/image PDFs need OCR** — misreads silently corrupt extracted numbers | Weakest classification confidence of the three |
| Citation granularity | `source_url` + block anchor | `page_number` + bounding box | line range |

**OCR risk rule:** any block from a document with `ocr_applied: true` that also
carries a `dollar_threshold` or `time_window` entity is auto-routed to human
review regardless of the classifier's own confidence — an OCR misread on a
number is a policy-accuracy bug, not a search-quality bug.

## 7. Chunking & indexing strategy (Milvus)

- **Milvus never sees raw HTML/PDF markup.** Canonical source stays in the
  CMS/object store; Milvus holds only clean `text`, scalar metadata, and the
  embedding vector, with `doc_id` pointing back to the canonical source.
- **Extraction cleaning pass:** strip `<script>`/`<style>`/nav/footer/cookie
  widgets/inline attributes; decode HTML entities; convert tables to
  markdown/key-value text (don't flatten into a run-on sentence); convert
  lists to ordered plain text preserving sequence (step order matters);
  dedupe repeated boilerplate/disclaimers instead of embedding them per chunk;
  pull link `href`s out as graph edges rather than embedding raw URLs.
- **Chunk along block boundaries**, not fixed token windows — a procedure's
  steps stay coherent, an eligibility block and its exception stay
  distinguishable. Fixed-window chunking is a fallback only for legacy
  freeform text with no recoverable structure, split on paragraph/heading
  boundaries, never mid-sentence, with light overlap (~10–15%) only there.
- **Inject a heading breadcrumb** ("Returns Policy > Electronics >
  Eligibility") into the embedded text to recover the context a chunk loses
  when split out of the article — this is the mechanism that fixes
  "electronics return policy vs. marketplace return policy" collisions.
- **Milvus collection schema:** vector field + scalar fields for filtering
  (`doc_id`, `doc_type`, `category`, `region`, `block_type`, `step_no`,
  `effective_date`, `version`, `sensitivity`). Use scalar filtering (current,
  non-expired, correct category/region) *together with* ANN search — hybrid
  structured + semantic retrieval, not vector-only. Evaluate Milvus's
  sparse+dense hybrid search for exact-term matches (dollar amounts, policy
  names, SKUs) where dense embeddings alone tend to underperform. Consider
  partitioning by `category`/`region` if query patterns cluster that way.
- **Pre-insert guardrail:** reject/flag any chunk with residual `<`/`>` tags,
  an anomalous non-alpha character ratio, or anomalous length before it's
  embedded and inserted — catches extraction-pipeline bugs before they
  pollute the index.

## 8. Knowledge graph

**Node types:** Document, Category, Product/ProductCategory, Region, Procedure,
Step, Condition/Rule, Entity (threshold/time-window values), Channel,
Escalation-path, Tool/System (agent tool-calling targets).

**Edge types:**

| Edge | Source |
|---|---|
| `SUPERSEDES` / `SUPERSEDED_BY` | CMS version history — direct mapping |
| `PART_OF` (step → procedure) | Block nesting |
| `APPLIES_TO` (doc → region/category/product) | Existing taxonomy fields |
| `REFERENCES` / `RELATED_TO` | Existing internal links authors already add |
| `EXCEPTION_TO`, `ESCALATES_TO` | Block-type extraction |
| `CONTRADICTS` | LLM-inferred by comparing semantically overlapping docs — flagged for content-ops review, never auto-resolved |
| `TRIGGERS` (SOP step → agent tool) | SOP step mapped to an action/tool registry |

**Sync model:** event-driven, incremental — on publish/update, re-run
extraction for that doc only, diff against existing graph edges, update. Not a
full rebuild.

**Why the graph matters for agentic use, beyond search:**
- Multi-hop reasoning ("what must be checked before this," "what else
  references this policy") as graph traversal instead of re-inferring from
  text every time.
- Retrieval augmentation — expand a vector hit outward along graph edges
  (GraphRAG-style) to pull in a procedure or exception that wasn't
  semantically similar enough to surface on its own.
- Explainability — an answer can cite a traversal path, not just a similarity
  score.
- Governance signal — orphan nodes surface content gaps; `CONTRADICTS` edges
  surface policy conflicts before an agent encounters them live.

## 9. Governance & review

Schema is 100% derived, but low-confidence or high-stakes extractions still
need a human check — not authors, a lightweight content-ops review step:

- Low-confidence block classification (especially `plaintext_pattern` source)
- `llm_inferred` relations below a confidence threshold
- Any `ocr_applied` document carrying a numeric entity (dollar/time)
- `sensitivity: restricted` content

Reviewers confirm/reject in a queue — they never hand-edit schema syntax.
High-confidence extractions publish straight through with monitoring.

## 10. Open questions

- Confirm CMS's publish/update event mechanism (webhook vs. polling) for
  triggering the extraction pipeline.
- Embedding model choice for Milvus.
- Milvus version/feature availability for sparse+dense hybrid search.
- Graph store choice for production scale (Neo4j / Amazon Neptune / property
  graph layer) vs. POC scale (lighter-weight option, see milestones).
- `block_id` stability strategy specifics for HTML/PDF/TXT (content-hash
  approach, anchor selection).
- `html` field policy: verbatim source markup vs. normalized semantic
  wrappers — affects rendering consistency vs. fidelity to source.
- PDF table extraction and OCR engine selection.
- Legal/compliance review process integration for `sensitivity: restricted`
  content.
