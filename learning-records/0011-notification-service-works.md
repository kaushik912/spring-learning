# Notification service verified (checked MailDev), full stack recovery handled

User checked MailDev inbox to confirm Lesson 9's emails, then hit a VS Code crash that killed all Java process terminals (Docker infra was unaffected — separate from VS Code). Gave a full ordered restart checklist (config-server → discovery → customer/product/payment/order → gateway → notification) with reasoning tied back to dependencies taught in earlier lessons. No DB/Mongo data was lost since those live in Docker containers that stayed up.

**Implication**: user now has a working mental model of service startup order/dependencies (asked for "the list," implying they already knew roughly what was needed, just wanted it complete) — future lessons can assume they'll self-recover from a full-stack restart without a repeated explanation of why the order matters.
