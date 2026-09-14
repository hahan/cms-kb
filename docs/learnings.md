# Learnings So Far

This tracks what the POC (see [`poc-scope.md`](poc-scope.md), implementation
in [`../poc/`](../poc/)) has actually taught us, updated as findings land.
It's meant to be read alongside `poc/README.md`, which carries the
per-query detail; this file carries the conclusions and the reasoning
behind each design decision made in response to them.

## Finding 1: the knowledge graph capability is real and unambiguous

Two queries (Q5: "do any policies conflict on this?", Q6: "what references
this policy?") are structurally unanswerable by vector search regardless of
chunking quality or embedding model -- there is no similarity score that
encodes "contradicts" or "references." A handful of hand-authored edges in
an in-memory graph (JGraphT, no graph database needed at this scale)
answered both correctly. This is the cleanest, highest-confidence result
from the POC: **build the graph layer with confidence.**

## Finding 2: metadata + breadcrumb embeddings alone did not solve disambiguation

The original run showed enhanced tying baseline (both FAILing) on Q2:
"What's the return window for electronics?" A near-duplicate sub-category
document ("Electronics > Accessories") outscored the correct general
electronics policy on raw similarity, even with heading-breadcrumb-prefixed
embeddings and full governance metadata available. The freshness filter
(exclude superseded docs) worked correctly in isolation -- but disambiguating
between sibling categories needs more than embedding proximity plus a
freshness filter; it needs an explicit filter on category intent.

## Fix: a category-aware query router

Implemented as `CategoryRouter.java`: a deterministic, keyword-based rule,
not an LLM call or ML classifier. For each "distinguishing" sub-category
in the corpus (Accessories, Marketplace, General Merchandise, Membership,
Price Match, Delivery), if the query mentions that sub-category's keywords,
results must carry that tag; if it doesn't, results carrying that tag are
excluded. Applied as a post-retrieval filter in Java over an over-fetched
candidate set (not a Milvus-side expression, to avoid depending on `LIKE`
support across Milvus versions), with a defensive fallback to the
unfiltered ranking if the rule would otherwise return zero results.

**Result: enhanced went from 8/9 to 9/9, with zero regressions** on any
other query -- including the graph queries (untouched) and Q9, which
explicitly mentions "Walmart+" and still correctly routes to the membership
policy rather than being over-excluded.

**Known limitation, stated plainly:** this is a hardcoded keyword list tied
to this corpus's specific taxonomy and phrasing. It will not generalize to
a new category or an unanticipated way of phrasing "accessories" without
someone maintaining the keyword map. A production version would likely
replace this with either an LLM-based query classifier (more general, adds
a per-query API call) or a systematic taxonomy-driven match (embedding the
category names/descriptions themselves and comparing against the query,
rather than a maintained keyword list). This fix proves the *mechanism*
(filter on category intent, not just freshness) is necessary and sufficient
for the one failure mode we found -- it doesn't prove this particular
implementation of that mechanism will hold up on content we haven't tested.

## Should the query set be scaled to 50 cases?

Asked and answered before the fix existed: **not yet, and the same
reasoning applies now.** Scaling queries against the same 11-document
corpus mostly re-asks known failure modes rather than discovering new
ones -- the lever for new insight is corpus diversity (more near-duplicate
pairs, deeper reference chains, more contradiction pairs), not query count
against a fixed small corpus.

Now that the category filter exists, though, the sequencing this pointed to
is actionable: **the next high-value step is expanding both the corpus and
the query set together**, specifically to (a) test whether the keyword-based
router generalizes to sub-categories and phrasings it wasn't built against,
and (b) stress-test the graph across more relation types and deeper
multi-hop chains than the two edges Q5/Q6 currently exercise.

## Current status

Baseline: 6/9. Enhanced: 9/9. All of the differentiation traces to two
identified, understood mechanisms -- the graph (Q5, Q6) and the category
filter (Q2) -- not to a vague "better chunking" effect. That's a more
defensible result than the raw score implies: every point of the gap has a
named cause, and every named cause has a known scope limit.
