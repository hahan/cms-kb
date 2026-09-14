package com.walmart.kbpoc.query;

import java.util.List;
import java.util.Set;

/** The fixed, trap-annotated query set from docs/poc-scope.md. */
public final class QuerySet {

    public static List<QuerySpec> all() {
        return List.of(
                QuerySpec.vector("Q1",
                        "Can I return this TV without a receipt?",
                        "Must select the electronics policy/SOP, not marketplace or general merchandise",
                        Set.of("ret-elec-v2", "sop-elec-returns", "faq-returns-elec"),
                        Set.of("ret-marketplace", "ret-general")),

                QuerySpec.vector("Q2",
                        "What's the return window for electronics?",
                        "Must return the CURRENT version (30 days), not the superseded one (14 days)",
                        Set.of("ret-elec-v2"),
                        Set.of("ret-elec-v1")),

                QuerySpec.vector("Q3",
                        "Customer wants to return a $600 laptop with no receipt, what do I do?",
                        "Must surface the exception/escalation clause, not just the general eligibility rule",
                        Set.of("ret-elec-v2", "sop-elec-returns", "faq-returns-elec"),
                        Set.of("ret-marketplace", "ret-general")),

                QuerySpec.vector("Q4",
                        "What do I need to check before processing an electronics return?",
                        "Should surface an early procedure step (receipt/order lookup), not an unrelated policy",
                        Set.of("sop-elec-returns"),
                        Set.of("ret-marketplace", "ret-general", "faq-price-match", "faq-delivery")),

                QuerySpec.graphContradicts("Q5",
                        "Are there any policies that conflict on the return window for electronics accessories?",
                        "Answerable only via a CONTRADICTS edge -- vector similarity has no relationship notion at all",
                        "policy-window-conflict-a",
                        Set.of("policy-window-conflict-b")),

                QuerySpec.graphReferences("Q6",
                        "What other content references the Electronics Returns Policy?",
                        "Multi-hop -- requires walking incoming REFERENCES edges, not a similarity score",
                        "ret-elec-v2",
                        Set.of("faq-returns-elec", "sop-elec-returns", "policy-membership-returns")),

                QuerySpec.vector("Q7",
                        "How long does standard shipping take?",
                        "Sanity check: unrelated distractor topic, both pipelines should get this right",
                        Set.of("faq-delivery"),
                        Set.of()),

                QuerySpec.vector("Q8",
                        "Does Walmart price match online competitors?",
                        "Sanity check: unrelated distractor topic, both pipelines should get this right",
                        Set.of("faq-price-match"),
                        Set.of()),

                QuerySpec.vector("Q9",
                        "Do Walmart+ members get extra time to return electronics?",
                        "Sanity check: a real but narrow policy, both pipelines should ideally get this right",
                        Set.of("policy-membership-returns"),
                        Set.of())
        );
    }
}
