# POC Scope — Demonstrating the Schema/Graph Benefit

## Purpose

Before investing in the full build described in [`milestones.md`](milestones.md),
prove — fast, on a small corpus — that the schema-derived, graph-augmented
design actually outperforms the simpler alternative it's being justified
against: **cleaned HTML chunked directly into Milvus, vector search only.**

This POC produces exactly one deliverable: a side-by-side comparison table of
baseline vs. enhanced retrieval on the same queries, over the same documents,
so the gap is shown, not argued.

**Out of scope for this POC** (deferred to the milestones in `milestones.md`
once this proves the approach): PDF/TXT adapters, OCR, a production graph
database, the human review queue, CMS webhook integration, and any
performance/scale testing.

## 1. Corpus — small and deliberately adversarial

~10–12 HTML documents, synthetic (written in Walmart policy/SOP style) is
fine — the point is to trigger specific failure modes, not to be
representative in volume. Include realistic HTML noise (nav, footer, inline
styles, a boilerplate disclaimer repeated across docs) so the cleanup
difference is visible, not assumed.

Deliberately include:

- **2–3 near-duplicate policies** (e.g. electronics returns / marketplace
  returns / general merchandise returns) — tests disambiguation
- **1 policy in two versions**, old explicitly marked superseded by new —
  tests staleness handling
- **1–2 SOPs** with explicit ordered steps, an exception clause, and an
  escalation step — tests procedural integrity
- **1 pair of docs that reference each other**, and **1 pair with a mild
  contradiction** (e.g. two docs stating different day-windows for the same
  scenario) — tests graph value

## 2. Baseline pipeline (naive, but genuinely competent — not a strawman)

- Strip `<script>`/`<style>`/nav/footer, decode HTML entities
- Fixed-window chunking (~300–400 tokens, light overlap)
- Embed → Milvus with minimal metadata (`doc_id`, `title`, `url` only)
- Plain vector similarity search, top-k, no filters

## 3. Enhanced pipeline — a thin slice of the full design

Simplified relative to `design.md` on purpose — enough to prove the point,
not the production-grade version:

- **Block segmentation via a single LLM pass** over cleaned HTML (skip the
  rule-based DOM classifier + LLM-fallback hybrid from the full design — one
  prompt producing typed blocks is sufficient here)
- **Minimal field set**: `doc_type`, `category`, `region`, `effective_date`,
  `supersedes`, block `text` + `block_type`, a handful of extracted entities
  (dollar thresholds, day windows)
- Embed block `text` with a heading-breadcrumb prefix; scalar fields
  alongside the vector in Milvus
- Retrieval = scalar filter + vector search (hybrid), not vector-only
- **Graph = in-memory only** (e.g. NetworkX or a plain adjacency list), not a
  real graph database — a few dozen edges are enough to answer 2–3 multi-hop
  queries; standing up Neo4j for 12 documents proves nothing the adjacency
  list can't

## 4. Query set — each query names the trap it's testing

~8–10 fixed queries, each with a known correct answer and a named reason
baseline is expected to fail it:

| # | Query | Trap it tests |
|---|---|---|
| 1 | "Can I return this TV without a receipt?" | Must select the electronics policy, not marketplace/general |
| 2 | "What's the return window for electronics?" | Must return the current version, not the superseded one |
| 3 | "Customer wants to return a $600 laptop with no receipt" | Must surface the exception + escalation step, not just the general rule |
| 4 | "What do I need to check before processing this return?" | Requires correct step order — baseline may return steps out of sequence or incomplete |
| 5 | "Are there any policies that conflict on the return window?" | Answerable only via the graph's `CONTRADICTS` edge — structurally impossible for baseline |
| 6 | "What references this policy?" | Multi-hop — baseline has no relationship notion at all |

(Fill in 2–4 more once the corpus is drafted, covering FAQ-style lookups and
at least one query each pipeline should get right, so the comparison isn't
only failure cases.)

## 5. Output: one comparison table

For each query: baseline's top result, enhanced's top result, pass/fail
against the named trap, and citation quality (URL-only vs. exact
block/version reference). This table is the deliverable — it answers the one
question this POC exists to answer: *does the schema+graph approach actually
outperform the naive baseline on the failure modes claimed for it?*

## Sequencing

1. Draft the corpus (including the two policy versions, the contradiction
   pair, and the SOP with exception/escalation).
2. Finalize the query set alongside the corpus, so each trap has a real
   document behind it.
3. Build the baseline pipeline first — it's the smaller effort and the
   control for the comparison.
4. Build the enhanced pipeline's thin slice.
5. Run both against the query set, produce the comparison table.
6. Decide whether the result justifies proceeding into `milestones.md`.
