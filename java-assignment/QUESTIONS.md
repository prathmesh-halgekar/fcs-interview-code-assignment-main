# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
Yes, refactor Store and Product toward the Warehouse pattern. The core problem with active record is that static methods can't be injected or mocked — StoreRepository was bolted on mid-project as a direct workaround for this. Separating the JPA entity from the domain model and hiding DB access behind a domain port makes use cases unit-testable without CDI and decouples domain logic from persistence details.

----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
OpenAPI-first pros: Contract is the single source of truth; enables parallel frontend/backend work; consumers can agree on spec before implementation; Swagger docs, mocks, and validators come free.
OpenAPI-first cons: Build-time dependency (quarkus:generate-code must run for the IDE to work — as experienced in this project); YAML maintenance overhead; can't add framework annotations (@Transactional) to generated code.
Hand-coded pros: Immediate IDE feedback; full annotation control; faster to prototype
Hand-coded cons: Spec and code can drift; no upfront contract for consumers
Choice: OpenAPI-first for external/stable APIs.
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
Unit tests first — use case business logic (Mockito, pure Java, < 1s per test). Highest ROI; For all use cases, repository and utility classes unit tests are a must.
Integration tests second — one @QuarkusTest + RestAssured suite per resource. Covers HTTP contract, DB wiring, error responses.

```