# LLM Usage Disclosure

Project: **Ticket4U** - Group 15B
Course: Workshop on Software Engineering Project (20215141), BGU

This file documents where we used LLMs while building Ticket4U, following the course
LLM Usage Policy. Tools we touched over the semester were ChatGPT, Gemini, and Claude
(including Claude Code in the IDE). We used them mostly for test boilerplate, for
working through bugs, and for tidying up code we had already written. The core of the
system - the concurrency control, the purchase/discount policy evaluation, the
permission model, and the refund/rollback logic on failures - we designed and wrote
ourselves. Those were the parts the project is actually about, so it didn't make sense
to hand them off.

A note on each entry below: the "prompt summary" is the gist of what we asked, not a
transcript. We didn't keep every chat log, so some of these are reconstructed from
memory of the relevant work.

---

## Black-box & white-box test scaffolding

- **Purpose of LLM use:** speeding up the repetitive parts of writing service tests
  (mock setup, arrange/act/assert skeletons) so we could spend time on the actual
  cases worth checking.
- **Summary of prompt(s):** things like "here's the CheckoutService signature and its
  collaborators, give me a JUnit 5 + Mockito skeleton with the mocks wired up" and
  "what edge cases am I missing for adding/removing seats from an active order".
- **Output received (short description):** test-class skeletons with `@BeforeEach` mock
  wiring and a few stub test methods, plus suggestions for edge cases (empty order,
  over-capacity, already-checked-out).
- **Files / components affected:** parts of `white/Application/ActiveOrder/*`,
  `black/Application/{ActiveOrder,Lottery,Queue}/*`, and a few controller black tests
  under `black/Controller/Event/*`.
- **Modifications made:** rewrote assertions to match our real domain objects and error
  types, dropped suggested cases that didn't apply to our model, renamed everything to
  our conventions, and added the cases the model missed. The concurrency race tests
  (`CancelVsHoldRaceTest`, `ConcurrentSeatHoldTest`, `StandingQuantityRaceTest`, etc.)
  were written by hand - getting the timing and the shared latches right needed us to
  reason about our own locking, not a generated skeleton.
- **Initial gaps in understanding (if any):** early on we weren't consistent about when to
  use a Mockito mock vs. a real in-memory repository in a test.
- **Final understanding (brief explanation in your own words):** for service-level tests we
  mock the collaborators we don't own (external payment/ticket gateways) and use the
  real in-memory repos for our own aggregates, because the repo behavior is part of
  what we want to verify. Anything touching concurrency runs against real threads, not
  mocks, since a mock can't reproduce a race.

## Company-shutdown event cancellation refactor

- **Purpose of LLM use:** a second opinion on how to decouple `CompanyService` from the
  event layer once we decided that closing/suspending a company should cancel its
  events.
- **Summary of prompt(s):** "CompanyService directly reaches into event internals to cancel
  events when a company shuts down - how do I move this behind an interface so the
  dependency points the right way?"
- **Output received (short description):** the suggestion to expose a single method on the
  event-management interface (we named it `cancelForCompanyShutdown`) and have
  `CompanyService` depend on `IEventManagementService` instead of the concrete class.
- **Files / components affected:** `CompanyService`, `IEventManagementService` /
  `EventManagementService`.
- **Modifications made:** we kept the interface idea but wrote the cancellation + refund
  cascade ourselves, including the part that fires the cancellation event and the order
  in which refunds are issued before the event is marked cancelled. The rollback/refund
  behavior on a partial failure is our own design - we did not want a generated version
  of failure handling, since that's one of the core things being graded.
- **Initial gaps in understanding (if any):** whether the refund cascade should live in
  `CompanyService` or be pushed down into the event service.
- **Final understanding (brief explanation in your own words):** `CompanyService` only knows
  "shut this company down"; it shouldn't know how an event refunds its buyers. So it
  calls one method on the event service, and the event service owns the cancellation,
  the refunds, and the notification. That keeps the company layer from depending on
  event internals and means the refund logic has a single home.

## Bug fixing - auth/guest edge cases

- **Purpose of LLM use:** help reading stack traces and narrowing down a couple of
  null/permission bugs while fixing UI guest access.
- **Summary of prompt(s):** "this NPE happens when `callerId` is null in the policy-query
  path - where should the guard go?" and "guests should be able to read purchase and
  discount policies but a member check is blocking them, what's the cleanest fix".
- **Output received (short description):** pointed at the missing null check before the
  permission lookup and suggested an `isMissingToken` helper to keep the token checks
  readable.
- **Files / components affected:** `CompanyService` (token/`callerId` handling), and the
  React `CheckoutPage` / `EventDetailsPage` policy queries.
- **Modifications made:** added the null guard and the helper, but we decided the actual
  access rule (guests may read policies, not write) ourselves based on the spec; the
  LLM only helped locate where the check was failing.
- **Initial gaps in understanding (if any):** we initially treated "no token" and "invalid
  token" the same way, which is what caused the guest path to break.
- **Final understanding (brief explanation in your own words):** a missing token means
  "guest", which is a valid caller with limited rights, while an invalid token is an
  error. Separating those two cases is what fixed the guest policy view without opening
  up anything a guest shouldn't see.

## Docker / environment config & documentation

- **Purpose of LLM use:** wording and formatting help on the README and the Docker/`.env`
  setup, plus debugging the dotenv loading for local runs.
- **Summary of prompt(s):** "review this README section for clarity", "why isn't my
  `.env` being picked up when I run `mvnw spring-boot:run` outside Docker".
- **Output received (short description):** phrasing suggestions for the README tables and a
  pointer toward loading the `.env` via an `EnvironmentPostProcessor` that walks up the
  directory tree.
- **Files / components affected:** `README.md`, `docker-compose.yml`, `.env.example`,
  `DotenvEnvironmentPostProcessor`.
- **Modifications made:** kept our own config structure and profile design; used the
  suggestions mainly for clearer docs and to confirm the post-processor approach, which
  we then implemented and tested ourselves (`DotenvEnvironmentPostProcessorTest`).
- **Initial gaps in understanding (if any):** how Spring resolves a `.env` before the
  context is built, vs. ordinary `application.properties`.
- **Final understanding (brief explanation in your own words):** a normal properties file is
  loaded once the context starts, which is too late for things that influence the
  profile. An `EnvironmentPostProcessor` runs earlier, so that's where we read the
  `.env` and feed the values in before the rest of the config is resolved.

## This disclosure file

- **Purpose of LLM use:** documentation assistance (allowed under §3.2) - drafting and
  formatting this `llm_usage.md` from notes we provided about our own usage.
- **Summary of prompt(s):** "read the course LLM policy and the repo, then draft the
  disclosure file from what we actually used LLMs for".
- **Output received (short description):** a structured draft in the required format.
- **Files / components affected:** `llm_usage.md`.
- **Modifications made:** we provided the facts (tools, areas, percentage) and reviewed every
  entry for accuracy against what we really did.
- **Initial gaps in understanding (if any):** none.
- **Final understanding (brief explanation in your own words):** the file has to match the
  codebase, because it gets cross-checked, so the content is ours even if the formatting
  had help.

---

## Overall Usage Summary

- **Approximate percentage of code influenced by LLMs:** roughly 10-25%, concentrated in
  test scaffolding, small refactors, and bug-hunting. Most of that was edited heavily
  before it landed, so "influenced" is more accurate than "generated".
- **Main areas where LLMs were used:** test boilerplate and edge-case brainstorming,
  mechanical refactors (e.g. introducing `IEventManagementService` to decouple the
  company layer), debugging null/permission bugs, and documentation/config wording.
- **Main areas implemented without LLM assistance:** concurrency control and the seat-hold
  /race handling, the purchase and discount policy evaluation and composition, the
  role/permission enforcement (Founder/Owner/Manager + System Admin), the
  refund/rollback logic on event cancellation and external-service failure, the lottery
  and virtual-queue mechanics, and the overall Domain/Application/Infrastructure/
  Controller architecture. These are the parts we're expected to be able to explain and
  modify on the spot, and they're ours.
