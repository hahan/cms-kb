# cms-kb

Design and POC planning for an agentic knowledge base over Walmart CMS content
(policies, FAQs, SOPs) — RAG-based retrieval plus a knowledge graph, used to
help resolve customer issues.

**Status:** design + planning only. No implementation yet.

- [Design document](docs/design.md) — problem statement, architecture,
  document schema, chunking/indexing strategy, knowledge graph design,
  multi-format (HTML/PDF/TXT) ingestion, governance.
- [POC milestones & tasks](docs/milestones.md)
- [`schemas/kb-document.schema.json`](schemas/kb-document.schema.json) — the
  derived document index schema (JSON Schema)
- [`schemas/kb-document.example.json`](schemas/kb-document.example.json) —
  worked example
