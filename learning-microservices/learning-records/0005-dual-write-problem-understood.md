# Understands the dual-write problem and outbox pattern conceptually

After seeing Payment's Kafka failure firsthand (Lesson 4), asked "what should we do instead" unprompted — correctly identified that DB save and Kafka publish are separate non-atomic steps. Explained the dual-write problem and the outbox pattern (write intent-to-publish in the same DB transaction, poll and publish separately) as the standard real-world fix; user confirmed understanding and declined a glossary entry (workspace has no GLOSSARY.md yet — not needed for one term, revisit if more distributed-systems vocabulary accumulates).

**Implication**: user reasons about failure modes proactively, not just following hands-on steps — future lessons can pose "what happens if X fails" as a genuine question rather than always supplying the answer.
