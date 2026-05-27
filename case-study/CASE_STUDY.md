# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

Cost allocation in a fulfillment environment is inherently complex because costs are shared across multiple entities (a single truck may serve several stores, a warehouse may store multiple product types for multiple stores) and occur at different levels of granularity. Before defining the scope of a cost tracking system, I would want to understand:

- **Allocation methodology**: Does the business want activity-based costing (ABC), where costs are traced to specific activities like pick-pack-ship, or a simpler proportional model based on volume or SKU count? ABC provides more accuracy but requires significantly more instrumentation.
- **Cost granularity**: Should costs be tracked per warehouse, per store, per product type, or down to the individual SKU? The finer the granularity, the more value — but also more data collection overhead.
- **Shared cost attribution**: How should overhead (rent, utilities, insurance) be split when a warehouse serves multiple stores? A per-square-meter or throughput-based key would need to be agreed upon with finance.
- **Temporal alignment**: When is a cost "incurred" vs. "recorded"? Transportation costs may be invoiced weeks after delivery. Labor may be accrued daily but payrolled monthly. This misalignment creates reconciliation challenges.
- **Data sources**: Which source systems (WMS, TMS, HR/payroll, ERP) own each cost category? Each integration adds complexity and potential for data inconsistency.
- **The warehouse replacement scenario**: Our system archives warehouses rather than deleting them. Cost history must remain attributable to the specific warehouse *instance* (using the `archivedAt` timestamp as a boundary), not just the Business Unit Code — otherwise a new warehouse reusing the same BUC would inherit the cost history of its predecessor, distorting performance comparisons.

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**

Optimization strategies are only credible when grounded in actual cost data — so this scenario has a hard dependency on Scenario 1 being in place first. Assuming baseline tracking exists, I would approach optimization in three phases:

**Identify**: Run a Pareto analysis across warehouses and stores. In most fulfillment networks, a minority of locations or routes drive a disproportionate share of costs. Key signals to look for: warehouses with chronically low utilization relative to capacity (our domain model tracks both capacity and stock, which is a direct proxy), stores with high transportation frequency from distant warehouses despite closer alternatives being available, and product types that are unnecessarily split across multiple warehouses when consolidation would reduce handling.

**Prioritize**: Separate quick wins (e.g., eliminating empty runs, renegotiating carrier rates on high-volume routes) from structural improvements (e.g., consolidating two underutilized warehouses, relocating stock closer to high-demand stores). Quick wins should be pursued immediately; structural changes need impact simulation and stakeholder alignment before committing.

**Implement and measure**: For each initiative, define a clear cost reduction hypothesis, a rollback plan, and a measurement window. Fulfillment operations have seasonal patterns, so measuring over too short a window (e.g., one week after a change) can produce misleading results. I would also want to model the interaction between constraints — for example, our system enforces a maximum of 3 warehouses per store and 5 product types per warehouse. Consolidation strategies must respect these constraints or explicitly challenge them if they are artificial.

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**

Integrating with financial systems is often where the complexity of cost tracking really materializes. The codebase already demonstrates a pattern for this kind of integration: `LegacyStoreManagerGateway` — a downstream call that is made only after the database change is committed (using `Panache.flush()` before the call). This same pattern is essential for financial system integration: **the operational change must be durably persisted before the financial event is published**, otherwise a system failure between the two creates inconsistencies that are difficult to reconcile.

Key considerations before designing this integration:

- **What financial systems are in play?** SAP, Oracle Financials, a custom ERP? Each has its own data model and integration capabilities (batch file, REST API, message queue). The answer shapes everything from the data format to the latency achievable.
- **Real-time vs. near-real-time vs. batch**: "Real-time" is often a business aspiration rather than a strict technical requirement. I would challenge this: do operational decisions genuinely depend on financial data being available in seconds, or is a 15-minute or end-of-day sync sufficient? The answer dramatically affects architecture complexity and cost.
- **Event-driven integration**: For operational changes (warehouse creation, replacement, archiving), publishing domain events to a message broker (Kafka, RabbitMQ) is a natural fit. The financial system consumes events asynchronously, and the broker provides durability, replay capability, and decoupling. This avoids the tight coupling of synchronous REST calls to the financial system from within a business transaction.
- **Idempotency**: Financial systems must not double-count. Every integration message should carry a unique, stable identifier (e.g., warehouse ID + event type + timestamp) so the financial system can detect and safely ignore duplicates.
- **Audit and traceability**: Financial records have regulatory retention requirements. The integration layer must ensure every cost event is traceable back to a specific operational event with a timestamp and actor identity. This aligns well with our soft-delete archiving approach — nothing is ever truly deleted.
- **Schema evolution**: Financial integrations tend to be long-lived. Designing the event schema with backward compatibility from the start (e.g., using versioned schemas in Avro or JSON Schema) avoids painful big-bang migrations later.

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**

Budgeting and forecasting in fulfillment is particularly challenging because costs are driven by a combination of planned variables (contracted warehouse capacity, headcount targets) and highly volatile variables (order volumes, fuel prices, carrier surcharges, seasonal demand peaks). A well-designed forecasting capability needs to account for both.

Before designing a forecasting system, I would want to understand:

- **Planning horizon**: Are we forecasting for the next month, quarter, or fiscal year? Short-term forecasts (daily to weekly) are primarily operational and driven by order pipeline data. Medium-to-long-term forecasts are strategic and need macroeconomic inputs alongside operational ones.
- **What drives costs in this domain**: The entities in our system are direct inputs to the forecast. Warehouse capacity and location determine fixed cost envelopes. The number of active product-warehouse-store associations drives fulfillment complexity and variable costs. Planned store openings or warehouse replacements should trigger budget revisions automatically.
- **Rolling forecasts vs. annual budgets**: Annual budgets quickly become stale in dynamic fulfillment environments. A rolling 12-month forecast that is updated monthly provides a more accurate basis for resource allocation decisions. I would advocate for this over a rigid annual budget cycle.
- **Variance analysis and early warning**: The most actionable part of a forecasting system is not the forecast itself but the alerting when actuals diverge significantly from the plan. Automated variance thresholds (e.g., flag if actual spend exceeds forecast by more than 10% in any warehouse cluster) enable proactive management rather than retrospective explanation.
- **Scenario modeling**: The warehouse replacement operation in our system is a perfect example of a what-if scenario — "what happens to the cost profile if we replace warehouse X with a higher-capacity warehouse at the same location?" The forecasting system should support parameterized scenarios that can be approved or discarded without affecting the baseline.
- **Data quality dependency**: Forecasting accuracy is bounded by the quality of the historical cost data feeding it. If the cost tracking foundation (Scenario 1) is incomplete or inconsistently attributed, the forecast will inherit those errors. I would always invest in data quality tooling before adding forecasting sophistication.

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**

This scenario is directly grounded in a core operation of our system: the warehouse replacement, which archives the old warehouse instance and creates a new one reusing the same Business Unit Code. Cost history preservation is not just a nice-to-have — it has practical operational and compliance implications.

**Why preserving cost history matters:**

- **Audit and compliance**: Finance and regulatory auditors require a complete, unbroken cost record for each physical location. If cost records are associated only by BUC, and BUC is reused, it becomes impossible to distinguish costs incurred by the old warehouse from those of the new one without additional disambiguation.
- **Budget baseline integrity**: The new warehouse should start with a clean budget slate. If historical costs from the archived warehouse are merged into the new one's reporting, performance metrics for the new operation are immediately distorted — a warehouse that has only been running for two months appears to have twelve months of accumulated overhead.
- **Performance benchmarking**: To evaluate whether the replacement was a good decision (e.g., did replacing an undersized warehouse with a larger one reduce per-unit costs?), you need to compare the cost profile of the old instance against the new one over comparable time windows. This is only possible if the two are treated as distinct cost entities.

**How to approach this:**

The `archivedAt` timestamp on the warehouse entity is the natural boundary for cost attribution. Any cost event with a timestamp before `archivedAt` belongs to the old instance; any cost after the new warehouse's creation timestamp belongs to the new one. The data model for cost records should therefore reference the warehouse's internal unique ID (not just the BUC), and the BUC should be treated as a human-readable business identifier that may be reused — not as the cost entity key.

I would also ask: what is the retention policy for archived warehouse cost data? If the business standard is a 7-year financial record retention, the archiving mechanism must ensure that data is immutable for that period, even if the warehouse no longer appears in operational reports.

Finally, this pattern reinforces why soft deletes (archiving rather than physical deletion) are essential in any system where financial history is a requirement — a point worth emphasizing when discussing the system's design decisions with the business stakeholders.

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
