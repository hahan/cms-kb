# cms-kb

Design and POC planning for an agentic knowledge base over Walmart CMS content
(policies, FAQs, SOPs) — RAG-based retrieval plus a knowledge graph, used to
help resolve customer issues.

**Status:** design + planning only. No implementation yet.

- [Design document](docs/design.md) — problem statement, architecture,
  document schema, chunking/indexing strategy, knowledge graph design,
  multi-format (HTML/PDF/TXT) ingestion, governance.
- [POC scope](docs/poc-scope.md) — the smaller, first POC: prove the
  schema/graph approach beats cleaned-HTML-into-Milvus before committing to
  the full build
- [POC milestones & tasks](docs/milestones.md) — the full build, once the POC
  above validates the approach
- [`schemas/kb-document.schema.json`](schemas/kb-document.schema.json) — the
  derived document index schema (JSON Schema)
- [`schemas/kb-document.example.json`](schemas/kb-document.example.json) —
  worked example
- [`poc/`](poc/) — the working POC implementation (Java + Milvus + local
  embeddings + Anthropic API for block segmentation). **Result: baseline
  5/9, enhanced 8/9** on the fixed query set — see [`poc/README.md`](poc/README.md)
