# POC Comparison: Baseline vs Enhanced

| # | Query | Trap | Baseline result | Baseline | Enhanced result | Enhanced |
|---|---|---|---|---|---|---|
| Q1 | Can I return this TV without a receipt? | Must select the electronics policy/SOP, not marketplace or general merchandise | faq-returns-elec [FAQ: Electronics Returns] score=0.686 | FAIL | ret-elec-v2 [ret-elec-v2-b2 [eligibility_criteria]] score=0.610 | PASS |
| Q2 | What's the return window for electronics? | Must return the CURRENT version (30 days), not the superseded one (14 days) | faq-returns-elec [FAQ: Electronics Returns] score=0.577 | FAIL | policy-window-conflict-a [policy-window-conflict-a-b2 [eligibility_criteria]] score=0.621 | FAIL |
| Q3 | Customer wants to return a $600 laptop with no receipt, what do I do? | Must surface the exception/escalation clause, not just the general eligibility rule | ret-elec-v2 [Electronics Returns Policy] score=0.594 | PASS | ret-elec-v2 [ret-elec-v2-b3 [exception]] score=0.645 | PASS |
| Q4 | What do I need to check before processing an electronics return? | Should surface an early procedure step (receipt/order lookup), not an unrelated policy | sop-elec-returns [SOP: Processing Electronics Returns] score=0.663 | PASS | sop-elec-returns [sop-elec-returns-b1 [intro]] score=0.609 | PASS |
| Q5 | Are there any policies that conflict on the return window for electronics accessories? | Answerable only via a CONTRADICTS edge -- vector similarity has no relationship notion at all | N/A (vector-only; top hit: policy-window-conflict-a, no relation info) | FAIL | policy-window-conflict-b | PASS |
| Q6 | What other content references the Electronics Returns Policy? | Multi-hop -- requires walking incoming REFERENCES edges, not a similarity score | N/A (vector-only; top hit: ret-elec-v2, no relation info) | FAIL | faq-returns-elec, sop-elec-returns, policy-membership-returns | PASS |
| Q7 | How long does standard shipping take? | Sanity check: unrelated distractor topic, both pipelines should get this right | faq-delivery [FAQ: Delivery & Shipping] score=0.761 | PASS | faq-delivery [faq-delivery-b1 [qa_pair]] score=0.816 | PASS |
| Q8 | Does Walmart price match online competitors? | Sanity check: unrelated distractor topic, both pipelines should get this right | faq-price-match [FAQ: Price Match Policy] score=0.731 | PASS | faq-price-match [faq-price-match-b1 [qa_pair]] score=0.795 | PASS |
| Q9 | Do Walmart+ members get extra time to return electronics? | Sanity check: a real but narrow policy, both pipelines should ideally get this right | policy-membership-returns [Walmart+ Membership Extended Returns] score=0.764 | PASS | policy-membership-returns [policy-membership-returns-b2 [eligibility_criteria]] score=0.724 | PASS |

**Score: baseline 5/9, enhanced 8/9**
