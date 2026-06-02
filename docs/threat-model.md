# Apache Sling — Threat Model

## §1 Header

- **Project**: Apache Sling — a set of Java OSGi bundles for building RESTful, content-driven web applications backed by a JCR repository. This model covers the engine, API bundles, scripting integrations, JCR integration, authentication handlers, content distribution, the OSGi installer / bundle-install path, the repoinit DSL, the XSS API, and the operator surfaces (Web Console, JMX, WebDAV/DavEx). Example assemblies (Sling Starter, Sling CMS) and build-time tooling are explicitly placed out of model (§3).
- **Version / commit**: Written against the working-tree checkout under `/home/robert/sources/apache/sling` as of **2026-06-01** (315 `org-apache-sling-*` module directories present).
- **Author**: Sling PMC (draft, AI-assisted, derived outside-in from public artifacts).
- **Status**: **Draft v1 — for Sling PMC review.** Derived from the published security policy, the project wiki, published CVE advisories, and source inspection. The model was additionally informed by the project's historical private security-list triage practice (no private discussion, reporter, or individual is reproduced here — only positions that are public policy or are visible in published CVE advisories). No question wave has yet been answered by the current PMC; every architectural judgement below is *(inferred)* with a backing question in §14 unless it is *(documented)* in one of those sources.
- **Version binding**: Single model, tracking **HEAD**. A report against a specific bundle version is triaged against the model as written here; when the model changes, the change is dated in this header and past triage decisions may be revisited. The unit of supported product is the **individual OSGi bundle**, not an assembly *(inferred — §14 W1Q1; strongly supported by the per-bundle release cadence and the security-policy dependency stance)*.
- **Reporting cross-reference**: Findings that violate a §8 property of a specific bundle must be reported privately to **`security@sling.apache.org`** per the [Sling security policy](https://sling.apache.org/project-information/security.html) and the [ASF process](https://apache.org/security/committers.html). Findings that fall under §3 (out of scope) or §9 (disclaimed properties) will be acknowledged and typically closed with a pointer to this document.
- **Relationship to existing security documents**:
  - The [published security policy](https://sling.apache.org/project-information/security.html) (`security.html`) remains canonical for the **reporting process** and the two high-level non-findings it already states (vulnerable dependencies; generic DoS). This document is the detailed companion: scope, trust boundaries, claimed/disclaimed prope`r`ties, triage dispositions. It must remain a **strict superset** of `security.html` — nothing `security.html` asserts is weakened here (Appendix A back-maps every such statement).
  - The CWiki page [*Threat model*](https://cwiki.apache.org/confluence/display/SLING/Threat+model) (last modified 2023-06-26) is an **early stub**: it is marked DRAFT, references STRIDE/OWASP without applying them, and contains a single substantive paragraph (the Web Console must be restricted to fully-trusted operators). It is **not** an audit or risk register, so the "do not silently supersede a prior audit" rule does not bite — but the coexistence of two pages titled "Threat model" must be resolved by the PMC (§14 M2). Proposed: this document supersedes the wiki stub, and the stub is replaced by a link here. The stub's one substantive claim is preserved as a §8 property (operator surface) and a §10 responsibility.
- **Provenance legend**:
  - *(documented)* — stated in `sling.apache.org`, the CWiki, a bundle README/Javadoc, or a published CVE advisory. Cited inline.
  - *(maintainer)* — stated by a Sling PMC member in response to a §14 question raised by *this* process. None yet — this is a first draft.
  - *(inferred)* — reasoned from code structure, the pattern across published CVEs, or domain knowledge; not yet ratified for the current model. Every such tag has a matching §14 question.
- **Draft confidence**: **~65 documented inline tags / 0 maintainer / ~62 inferred tags.** The documented claims rest on `security.html`, the CWiki, bundle Javadoc/source inspection, the project's published CVE advisories (9 issued Sling CVEs), and the project's documented security-team triage practice. The inferred tags concentrate in the architectural framing (§2 role decomposition, §4 boundaries, §5 environment, in/out rulings) — each routes to a §14 question. **No claim has been ratified by the current PMC**, so nothing is yet tagged *(maintainer)*; this is the headline caveat for any triager using the document today.

**One-paragraph description**. Apache Sling is a framework of Java OSGi bundles for building RESTful, content-driven web applications backed by a JCR repository (typically Apache Jackrabbit Oak). Its organising idea is *URL → JCR resource → script*: an incoming HTTP request path is resolved to a node in a content tree, the node's `sling:resourceType` selects a rendering script (HTL/Sightly, JSP, and optionally JavaScript/Groovy/FreeMarker/Thymeleaf), and the script renders the response. Writes go through a single `SlingPostServlet` whose request parameters can create, modify, copy, move, and delete content, always through the requesting user's JCR session. Around this core sit pluggable authentication handlers (form, OAuth/OIDC, SAML2), an OSGi installer that can install bundles from JCR content, a repoinit DSL that provisions users and ACLs at boot, content distribution/replication between instances, and operator surfaces (Felix Web Console, JMX, WebDAV/DavEx). Each Sling bundle is independently versioned with its own contract; integrators compose production assemblies from a chosen subset. The Sling Starter and Sling CMS are *example* assemblies the project publishes for integration testing and demonstration; production products (e.g., Adobe Experience Manager) build their own.

---

## §2 Scope and intended use

**Primary intended use** *(documented — sling.apache.org)*: a framework for RESTful, content-driven web applications backed by a JCR repository, "from simple websites to complex web applications". It is a **library/framework set deployed as a long-running network service**, not a CLI, not an embedded library used in isolation, and not a sandbox.

**Unit of supported product** *(inferred — §14 W1Q1)*: **the individual OSGi bundle**. Each bundle is released and versioned on its own and carries its own §8 properties. Assemblies (Sling Starter, Sling CMS, downstream products) are compositions owned by the integrator. The model never treats "what the Starter does by default" as a Sling property — see §3.

**Deployment contexts**:
- **Custom assemblies** built via the Feature Model toolchain are the production norm. The integrator selects bundles, configures them, and owns the resulting composition.
- **Sling Starter** is an *example* assembly used for integration testing and demonstration; its defaults are dev-convenience, not a hardened production posture *(inferred — §14 W1Q1)*.
- **Sling CMS** (`org-apache-sling-app-cms`) is another example/reference assembly maintained by the project. It has been the subject of its own CVE (CVE-2023-22849, stored/reflected XSS in CMS UI) *(documented — published CVE advisory)*.
- **Library use** of individual API bundles (`api`, `models-api`, `commons-*`) embedded into other applications.

**Caller / role decomposition** *(inferred — §14 W1Q8)*. Sling is a network service, so "the caller" splits into several actors with different trust levels:

| Role | Trust level | Typical channel |
|---|---|---|
| Anonymous HTTP client | Untrusted | Public network (TB1) |
| Authenticated low-privilege user | Partially trusted; bounded by JCR ACLs | HTTP with session cookie / token (TB1) |
| Administrator (`admin` JCR principal) | Trusted for the instance | HTTP + Web Console |
| Service user (`serviceusermapper` mapping) | Trusted for its declared scope | In-process bundle |
| Distribution peer | Authenticated, possibly adversarial | HTTP / Kafka (TB2) |
| OSGi bundle author | Fully trusted — their code runs as Java | Installed bundle (TB4) |
| Content-package / repoinit author | Trusted for declared install paths / DSL | `.zip` package, Feature Model, boot config (TB4) |
| Operator | Fully trusted for the instance | Web Console, JMX, DavEx (TB7) |

**Component-family table.** Each family may have a different threat profile; this table is the primary orienting artifact. "Touches OS/network?" flags side effects outside the JVM. Anything "No (out of model)" reappears in §3 with a reason.

| Family | Representative entry point | Touches OS / network? | In model? |
|---|---|---|---|
| HTTP request engine | `SlingMainServlet` → `SlingRequestProcessor` | Network in | **Yes** |
| Resource resolution | `ResourceResolverFactory`, `ResourceUtil.normalize` | None directly | **Yes** |
| Servlet/script resolution | `servlets-resolver` (`SlingServletResolver`) | None | **Yes** |
| Authentication | `auth-core`, `auth-form`, `auth-oauth-client`, `auth-saml2` | Network out (OAuth/SAML IdP) | **Yes** |
| Authorization / access | JCR ACLs (Oak), `resourceaccesssecurity`, `jcr-resourcesecurity`, `commons-permissions*` | None | **Yes** |
| Write surface | `servlets-post` (`SlingPostServlet`) | None | **Yes** |
| Scripting | `scripting-{core,sightly,jsp,javascript,groovy,freemarker,thymeleaf,esx,...}` | Sandbox effectively absent; full Java for JSP/Groovy/JS | **Yes** |
| OSGi install path | `installer-core`, `installer-provider-{jcr,file,installhook}`, `installer-factory-*` | Filesystem | **Yes — high blast radius** |
| JCR integration | `jcr-{api,base,oak-server,resource,davex,webdav,contentloader,packageinit}` | Filesystem (segment store) | **Yes** |
| Repository-init DSL | `jcr-repoinit`, `repoinit-parser` | None | **Yes** |
| Content distribution | `distribution-{api,core,journal,journal-kafka,avro-serializer,kryo-serializer}` | Network out, Kafka | **Yes** |
| Content packages | `installer-factory-packages`, `jcr-contentloader`, `feature-extension-content` | Filesystem | **Yes** |
| XSS API | `xss` (`XSSAPI`, `XSSFilter`) | None | **Yes** |
| Output rewriting | `rewriter` | None | **Yes** |
| Resource merging | `resourcemerger` (`MergedResource`) | None | **Yes** |
| Operator surfaces | Felix Web Console, `extensions-webconsolesecurityprovider`, `jmx-provider`, `tooling-support-*`, `serviceuser-webconsole` | Network in (admin port) | **Yes — TB7** |
| Logging | `commons-log`, `commons-log-webconsole` | Filesystem (log files) | **Yes** |
| Job & event | `event`, `jobs`, `discovery-*` | Network out (peer discovery) | **Yes** |
| External integrations | `commons-messaging-mail`, `jms`, `mongodb`, `cassandra`, `nosql-couchbase-*`, `clam`, `mcp-server*` | Network out, native processes | **Yes — TB6** |
| Crypto convenience | `commons-crypto` | None | **Yes** |
| Legacy/retired utilities | `commons-json` (retired), `pipes` | None | **Yes (but see §3 / §11a)** |
| Feature Model build tooling | `feature`, `feature-launcher`, `slingfeature-maven-plugin`, `feature-cpconverter`, `feature-analyser*` | Filesystem, Maven repo (build time) | **No — build-time; §3** |
| Archetypes | `*-archetype/` | n/a (scaffolding) | **No — §3** |
| Committer / release tooling | `tooling-{github,jenkins,release,scm}`, `committer-cli`, `slingpackager`, `ide-tooling` | Network out | **No — out of layer; §3** |
| Samples & demo content | `samples/*`, `*-examples`, `starter-content`, `hapi-samplecontent`, `*-it-services`, `hc-samples` | n/a | **No — §3** |
| Test-only bundles | `*-it`, `testing-*`, `junit-*`, `paxexam-util`, `testing-clients` | n/a | **No — §3** |
| Website & dist mirrors | `site/`, `dist/`, `dist-dev/`, `private/` | n/a | **No — ASF infra; §3** |

---

## §3 Out of scope (explicit non-goals)

### Documented non-goals (from the [security policy](https://sling.apache.org/project-information/security.html))

- **Vulnerable dependencies of Sling bundles are not, by themselves, Sling security issues** *(documented)*. Sling bundles import dependencies at the lowest API version that maximises deployment compatibility; the **assembler/deployer** is responsible for resolving non-vulnerable versions into the assembled bundle set. Recorded exceptions where Sling *does* act *(documented — [Dependabot wiki](https://cwiki.apache.org/confluence/display/SLING/Dependabot))*: libraries inlined/embedded in a Sling bundle, Maven-plugin dependencies, bundles shipped directly in Sling Starter / Karaf features / Sling CMS, Node.js project dependencies, and "oldest secure version" bumps. Established practice is to verify that a dependency advisory describes a path *actually reachable in Sling* before acting — a published advisory on a dependency is frequently not exploitable as Sling uses it (e.g., a build/test-scope dependency that never ships in the bundle is out of scope) *(documented — Dependabot wiki + Sling security-team practice)*.
- **Generic denial of service is out of scope** *(documented)*: *"we cannot promise magic workarounds to generic problems (such as a client streaming lots of data to your server, or re-requesting the same URL repeatedly). In general our philosophy is to avoid any attacks which can cause the server to consume resources in a non-linear relationship to the size of inputs."* This is the threshold §8 property 9 uses: **super-linear resource consumption in input size is a bug; linear consumption proportional to input is not.**
- **Configuration questions, applicability of published CVEs, and patch-availability questions** are not security-team work; they go to the public users list *(documented)*.

### Out-of-scope code shipped in the repository

- **Samples & demo content** (`samples/`, `*-examples`, `starter-content`, `hapi-samplecontent`, `*-it-services`, `hc-samples`, `validation-examples`): illustrative, not for production. Reports against shipped scripts here → `OUT-OF-MODEL: unsupported-component` *(inferred — §14 W3Q1)*.
- **Archetypes** (`*-archetype/`): Maven scaffolding; generated code is the integrator's *(inferred — §14 W3Q1)*.
- **Committer / release tooling** (`tooling-github/jenkins/release/scm`, `committer-cli`, `slingpackager`, `ide-tooling`): out of layer *(inferred — §14 W3Q1)*.
- **Feature Model build-time tooling** (`slingfeature-maven-plugin`, `feature-launcher-maven-plugin`, `feature-cpconverter`, and Maven plugins generally): build-tool security is the integrator's CI concern. This is consistent with the established treatment of build/test-scope dependencies as out of scope *(inferred — §14 W2Q11; partially documented)*.
- **Website & dist mirrors** (`site/`, `dist/`, `dist-dev/`): content-integrity is ASF infrastructure's concern. Precedent: a report of an exposed `/.git` directory on the project website was handled as a **website-infrastructure hygiene fix in the `sling-site` repo, not a product vulnerability** (the site source is already public on `github.com/apache/sling-site`) *(documented — Sling security-team practice)*.
- **`private/`, `tmp/`**: not shipped components.
- **Test-only bundles** (`*-it`, `testing-*`, `junit-*`, `paxexam-util`, `testing-clients`): must never be installed into a production runtime *(inferred — §14 W3Q1)*.
- **Assembly-level defaults** (Starter ACLs, Starter bundle list, Felix Web Console default credentials, the example JCR `admin` password). Per-bundle defaults are in §5a; *assembly* defaults are the integrator's choice, not a Sling bundle property *(inferred — §14 W1Q1)*.

### Other out-of-scope threats

- **Compromise of an authenticated administrator / a bundle with code-deploy rights.** This is the single most explicit boundary in established Sling security practice: the ability to deploy code into the OSGi container is treated as total compromise, so any "exploit" that first requires deploying a bundle or holding administrative privileges is out of scope. (An expression-language-injection report against `pipes` was rejected on exactly this basis — it required administrative privileges to reach.) An attacker who holds admin, the JCR `admin` principal, or the ability to install a bundle has already won. *(documented — Sling security-team practice)*
- **Multi-tenant isolation within a single JVM.** Sling is single-tenant per JVM; OSGi visibility is the only inter-bundle isolation. Multi-tenancy requires separate JVMs *(inferred — §14 W1Q7)*.
- **Side channels** (timing, cache, power) — see §9; the only place they could matter is `commons-crypto`, and even there constant-time is not claimed by default.
- **Networking below TLS** (BGP, DNS, ARP), **local privilege escalation on the host**, and **the build/release pipeline** (TB5) — all out of layer / ASF-level.

---

## §4 Trust boundaries and data flow

### Trust boundaries

| # | Boundary | Untrusted side | Trusted side |
|---|---|---|---|
| TB1 | HTTP edge | Anonymous / low-priv authenticated client | Engine + scripts + JCR |
| TB2 | Distribution peer | Remote Sling instance (or anyone who can produce to the Kafka topic) | Local JCR write path |
| TB3 | JCR session | A given user's JCR session | Service-user / admin session |
| TB4 | Bundle / package / repoinit install | Content under `/apps/*/install`, watched FS dirs, content packages, boot repoinit | OSGi framework + repository setup |
| TB5 | Build / release | Contributors, dependencies, CI | Released artifacts (out of model, §3) |
| TB6 | External integrations | SMTP, Kafka, OAuth/SAML IdPs, MongoDB, Couchbase, Clam, MCP clients | Sling runtime |
| TB7 | Operator surface | Anyone on the admin network | Web Console + JMX + DavEx |

### High-level data flow

```
HTTP client ──TB1──▶ AuthN handler (auth-core / auth-form / auth-oauth / auth-saml2)
                          │
                          ▼
                  ResourceResolver ──▶ JCR (Oak)  [TB3 — Oak enforces ACLs]
                          │
                          ▼
                  ServletResolver (search-path bounded)
                          │
             ┌────────────┼─────────────┐
             ▼            ▼             ▼
        Script engine   POST servlet   GET servlets
        (HTL/JSP/JS)    (write path)   (default GET/JSON)
             │            │             │
             └────────────┼─────────────┘
                          ▼
                   Rewriter → XSS API → response
```

Write path (TB1 → TB3): `POST` → `SlingPostServlet` → JCR write via the **requesting principal's** session (Oak ACL-checked) → observation listeners → `JobManager` → `Distribution` → peers (TB2).

Install path (TB4): JCR node `/apps/*/install/*.jar` → `installer-provider-jcr` → OSGi installer → `BundleContext.installBundle()`. Equivalent paths: `installer-provider-file` on a watched directory, content packages via `installer-factory-packages` / `jcr-contentloader`, and the Web Console upload (TB7).

### Reachability preconditions per component

For each family, the condition a finding must meet to be in-model:

- **`engine`, `auth-*`, `servlets-{get,post,resolver}`, `resourceresolver`, `xss`, `rewriter`, `resourcemerger`**: in-model iff reachable from a TB1 HTTP request at the relevant §2 role.
- **`servlets-resolver` / `ResourceUtil.normalize`**: in-model iff a crafted URL can cause a script to be resolved/executed **outside the configured search paths** — this is exactly the CVE-2024-23673 class (a `normalize` flaw that left `/../` in the path and bypassed `isPathAllowed()`) *(documented — CVE-2024-23673 advisory)*.
- **`scripting-*`**: in-model iff invoked while resolving a script for an attacker-influenceable resource path. A bug only reachable via a script the attacker did not place (a sample/demo component never deployed) is `OUT-OF-MODEL: unsupported-component`.
- **`installer-provider-jcr` / `-file`**: in-model iff a **low-privilege** principal can cause a bundle install via a configured watch path. "An admin/operator installed a malicious bundle" is by design (TB4 trusted) *(inferred — §14 W2Q8; supported by established Sling security-team practice on code-deploy scope)*.
- **`servlets-post`**: in-model iff a low-privilege HTTP role can reach a write the model claims is gated by the requesting principal's JCR session.
- **`distribution-*`**: distribution authenticates the *peer* (HTTP Basic, OAuth, mTLS, or Kafka SASL/ACL) but **does not authenticate payloads**. In-model iff either (a) an unauthenticated party bypasses peer-authentication and writes to the receiver, or (b) an authenticated peer violates a property *other than* payload integrity (crash, super-linear consumption, JCR-session escape). A *legitimate* peer sending a malicious payload is `BY-DESIGN: property-disclaimed` *(inferred — §14 W2Q9)*.
- **`installer-factory-packages`, `jcr-contentloader`, `feature-extension-content`**: live runtime; packages are trusted input (operator-supplied) with no built-in authorship verification. In-model iff a low-priv principal can supply a package to one of these factories via a reachable path *(inferred — §14 W2Q11)*.
- **`repoinit-parser`, `jcr-repoinit`**: parsed input is operator-controlled. A finding against the parser itself on adversarial input is `OUT-OF-MODEL: trusted-input`; a bundle that fed *untrusted* content to the parser is the bug *(inferred — §14 W2Q10)*.
- **Web Console, JMX, DavEx, WebDAV, `mcp-server`**: in-model iff reachable from TB1 (i.e., these surfaces were left exposed to unauthenticated traffic by an assembly's mounts/ACLs). Operator deliberately exposing them is `OUT-OF-MODEL: trusted-input`.

---

## §5 Assumptions about the environment

*(All entries inferred unless tagged; §14 W2 carries the questions. The negative-claims inventory was spot-checked against core-bundle source on 2026-06-01.)*

- **Runtime**: Java 8 minimum, Java 11 the primary LTS baseline *(documented — [Java version support wiki](https://cwiki.apache.org/confluence/display/SLING/Java+version+support); 2019, may be stale — §14 W2Q13a)*; recent bundles raise this (e.g., `commons-log` 5.5.0 requires Java 11 per the CVE-2023-6378 advisory). OSGi R7+ on Apache Felix.
- **Repository**: Apache Jackrabbit Oak. **Oak ACLs/permissions are authoritative for content access; Sling does not re-implement or override repository authorization** (§8 property 2, §9 disclaimer on Oak semantics).
- **Concurrency**: Sling services are expected to be thread-safe. Scripts and Sling Models run on the request thread; thread-safety of *developer-introduced* mutable state is the developer's responsibility — see the Sling Models session-leak rejection in §11 *(documented — Sling security-team practice)*.
- **Time**: system clock used for session expiry, distribution timestamps, scheduler firing.
- **Filesystem**: `installer-provider-file` watches configured directories; write access there equals OSGi bundle-install rights (TB4).
- **Network**: TLS termination is the deployer's responsibility (reverse proxy / Dispatcher). Sling does not assume the wire is encrypted.

**Negative claims — what Sling does *not* do to its host** *(inferred — §14 W2Q13; core bundles spot-checked 2026-06-01)*:

- **Child processes**: no `Runtime.exec` / `ProcessBuilder` in core (`engine`, `servlets-post`, `servlets-resolver`, `auth-core` confirmed clean). *Outside* core, `commons-messaging-mail`, `clam`, and the JSP/Groovy/JS scripting engines can spawn by design.
- **Environment variables**: one opt-in exception — `resourceresolver`'s `StringInterpolationProviderImpl` calls `System.getenv` for OSGi-configured placeholder substitution in resolver mappings (confirmed present 2026-06-01). Operator-driven, not load-time.
- **Global JVM state**: scoped exceptions only — `jaxp-configurator` deliberately sets JVM-wide JAXP factory defaults (the documented XXE mitigation, §10); `commons-scheduler` sets `org.terracotta.quartz.skipUpdateCheck=true` at activation; `commons-log` transiently sets `java.util.logging.config.class` in a try/finally; `feature-launcher` sets slf4j system properties at launch (launcher only). *(inferred — §14 W2Q13)*
- **Signal handlers / shutdown hooks**: none in core runtime bundles; `feature-launcher` registers a shutdown hook to stop the framework cleanly (launcher only). *(inferred — §14 W2Q13)*

---

## §5a Build-time and configuration variants

"The project" is a set of bundles. This section lists the **per-bundle** OSGi configuration knobs that materially change a bundle's security envelope. **Assembly-level defaults** (what the Starter ships) are *not* knobs of this model — any default in the Starter is dev-convenience (§3).

| Bundle / knob | Bundle default | Effect | Stance |
|---|---|---|---|
| `jcr-base` `LoginAdminWhitelist` | Empty (deny) | Which bundles may call `loginAdministrative` | Safe default; assembler adds entries explicitly *(inferred — §14 W2)* |
| `serviceusermapper.amended` | None | Maps OSGi bundle → JCR principal | Each bundle needing a service user declares its mapping; otherwise privileged login denied |
| `installer-provider-jcr` watch paths | None | JCR paths bundles install from | In-model only if a low-priv principal can write to a configured path (§4) |
| `security` `ReferrerFilter` | Active when installed; conservative allow-list | Anti-CSRF for state-changing requests | Per-bundle property §8 #12 *(inferred — §14 W3)* |
| `security` `ContentDispositionFilter` | Disabled until configured with paths | Forces `Content-Disposition: attachment` for binary downloads | Property §8 #13 holds when enabled+configured |
| `jaxp-configurator` | When installed and started **first**, sets JVM-global JAXP factories to safe (XXE-off) defaults | Disables XXE in JAXP | Required mitigation for assemblies parsing user XML/PDF/Office *(documented — CVE-2025-66516 advisory)* |
| `auth-core` `sling.auth.requirements` | No path authenticated until configured | Which paths require auth | Per-assembly config *(inferred — §14 W2)* |
| `jcr-resourcesecurity` | `ResourceAccessGate` for JCR; per-config | Provider-context access gating | *(documented — resource-access-security)* |
| `extensions-webconsolesecurityprovider` | Replaces Felix default Web Console auth with JCR-backed auth when installed | Web Console hardening building block | Opt-in |
| `distribution-*` receive endpoint | Disabled until configured with peer identity | Accepts content from peers | Per-assembly config |
| `engine` include content-type-conflict check | **Off by default** (opt-in) | Throws when an included servlet sets a conflicting content-type — the CVE-2022-45064 hardening | Kept off-by-default for backward compatibility; may flip after "a sufficient period" *(documented — CVE-2022-45064 advisory)* |
| `mcp-server` | Opt-in; not in Starter | Exposes a Model Context Protocol surface | Operator-only surface *(inferred — §14 W1Q4)* |

### Scripting-engine bundle install (TB4 expansion)

Installing a scripting bundle adds an engine that executes scripts resolved from JCR content.

| Bundle | §8 contextual-escape contract? | Status |
|---|---|---|
| `scripting-sightly-*` (HTL) | **Yes** — contextual auto-escape (§8 #4) | First-class, canonical *(inferred — §14 W1Q5; well-documented in HTL spec)* |
| `scripting-jsp*` | **No** — authors must encode via `xss` `XSSAPI` | In scope; primary alongside HTL *(inferred — §14 W1Q5a)* |
| `scripting-javascript` (Rhino), `-groovy`, `-freemarker`, `-thymeleaf`, `-esx` | **No** | In scope as bundles (memory/correctness still apply) but **no encoding/sandboxing claim**; JSP/Groovy/JS reach full Java *(inferred — §14 W1Q5b)* |

### Insecure-default rulings (proposed — §14 W1)

- **Felix Web Console default credentials**, **example JCR `admin` password**: any "default `admin`/`admin` in production" report → `OUT-OF-MODEL: non-default-build`. The wiki threat-model stub already states operators must restrict the console *(documented — CWiki Threat model; §14 W1Q2/Q3 to ratify the disposition)*.
- **Distribution receive endpoint** left open: a finding is `VALID` only if peer-authentication can be *bypassed*; an operator who configured no peer identity and exposed it is `OUT-OF-MODEL: trusted-input`.

---

## §6 Assumptions about inputs

Sling is a network service; the first column is the route/protocol message, not a function.

### Per-endpoint trust table (high-value entries)

| Endpoint / route | Parameter | Attacker-controllable? | Caller / operator must enforce |
|---|---|---|---|
| `GET /<path>(.<selector>).<ext>(/suffix)` | Path, selectors, extension, suffix | **Yes** | Resolution rules; ACLs on `/apps`,`/libs`,`/etc`; selectors must not redirect to privileged scripts (CVE-2024-23673 class) |
| `POST <path>` (SlingPostServlet) | `:operation` (`delete`/`copy`/`move`/`import`/…) | **Yes** | Which roles may invoke each operation; all writes go through the requesting principal's session |
| `POST <path>` | `sling:resourceType`, `:redirect`, `@TypeHint`, `@CopyFrom/@MoveFrom`, `:name(Hint)` | **Yes** | ACLs preventing write to nodes whose type resolves to privileged scripts; open-redirect guard on `:redirect`; node-name sanitisation |
| Multipart upload (any servlet) | Filename, content-type, body | **Yes** | Upload-size caps; treat MIME detection as advisory (§9); `ContentDispositionFilter` for served binaries |
| `auth-form` `/j_security_check` | `j_username`, `j_password`, `resource` (redirect) | **Yes** | Edge rate-limiting (§9); redirect-target validation — the CVE-2017-15700 class (newline in `resource`) |
| `auth-oauth-client` callback | `code`, `state`, `id_token`, JWT claims | **Yes** | `state`/nonce correlation, audience/issuer/signature/expiry checks (§8 #8) |
| `auth-saml2` ACS | SAML response | **Yes** | Signature verification, replay protection (§8 #8) |
| `/system/console/*`, `/system/console/bundles` upload | All parameters, uploaded JAR | **Yes — must not be on TB1** | Operator gates this surface (TB7) |
| `jcr-davex` / `jcr-webdav` | HTTP method + body | **Yes** | Auth + ACLs; network-restrict |
| Distribution receive endpoint | Serialized package | **Yes (TB2)** | Authenticated peer; payload integrity is the operator's (sign externally) |
| Kafka topic (`distribution-journal-kafka`) | Avro/Kryo message | **Yes (TB2)** | Topic ACL; deserialization is trusted-only |
| `jcr-repoinit` parser input | Repoinit DSL | **Operator-supplied at boot** | Trusted-only (§4) |
| Tika / content detection | Uploaded binary (PDF/Office/XML) | **Yes** | XXE mitigation via `jaxp-configurator` (deployer must include it) *(documented — CVE-2025-66516)* |
| Legacy `commons-json` parse | JSON/XML text | **Yes** | Bounded nesting depth — unbounded recursion is the CVE-2022-47937 class; **module is retired, migrate to Johnzon** *(documented — CVE-2022-47937 advisory)* |
| `resourcemerger` `MergedResource` | Merge path | **Yes** | Path shape — repeated merge-root segments are the CVE-2023-26513 DoS class *(documented — CVE-2023-26513 advisory)* |

### Size / shape / rate assumptions

- **Per-request body size**: bounded by the servlet container; Sling imposes no global cap *(inferred)*.
- **Per-node property size / tree depth**: bounded by Oak; Sling does not validate further *(inferred)*.
- **Parser nesting depth**: the canonical Sling JSON/template parsers must not recurse unbounded on attacker-controlled nesting (CVE-2022-47937 lesson); the *retired* `commons-json` does, which is why migration — not a depth limit — is the stated remedy.
- **Rate**: no Sling-layer rate limiting — generic DoS is the deployer's problem *(documented)*.

---

## §7 Adversary model

### In scope

- **Anonymous TB1 attacker** — arbitrary HTTP, including malformed framing. Goals: RCE (script execution via resolution bypass or write-then-render), auth bypass, content tampering, disclosure of non-public content.
- **Low-privilege authenticated user** — holds a legitimate JCR principal with limited ACLs. Goals: privilege escalation across ACL boundaries; reaching a privileged `sling:resourceType`/script; the author→admin escalation realised by CVE-2024-23673.
- **Authenticated-but-Byzantine distribution peer (TB2)** — holds a legitimate peer identity, can produce any payload. Goals: corrupting replicated content; crashing the receiver with malformed packages. Honest-fraction threshold is **not formalised**; typical deployment is one author → N publish receivers, and an attacker controlling the author has fully compromised the system *(inferred — §14 W2Q9)*.
- **Crafted-input attacker** — supplies PDF/Office/XML for Tika parsing; supplies deeply-nested JSON/XML to a parser; supplies adversarial selectors/paths; supplies CRLF in URLs (log-injection, CVE-2022-32549).

### Capabilities / limits

- Can craft any HTTP request and upload arbitrary content to any path they hold write ACL for.
- **Cannot** tamper with the JVM, OSGi framework, or operator config; **cannot** read JVM memory; **cannot** observe cryptographic-precision timing side channels (§9 disclaims constant-time).

### Out of scope

- **Admin / code-deploy / host / JVM control** — already won; the ability to deploy code is treated as total compromise (§3).
- **Build/release-pipeline attackers** (TB5) — ASF-level.
- **Side-channel attackers** on secret comparison — except where a specific bundle explicitly claims constant-time (none does by default — §9).
- **Co-tenant attackers** in a shared JVM — single-tenant per JVM (§3).

---

## §8 Security properties Sling provides

Each property: statement + conditions; violation symptom; severity tier; provenance. Severity "CVE-class" means it warrants coordinated disclosure; "correctness/hardening" means an ordinary bug.

### Engine / resolution

1. **The servlet/script resolver respects its configured search-path list.** URL parts (selectors, extension, suffix, path) cannot cause a script to be resolved or executed *outside* the configured search paths (`/apps`, `/libs`, additional configured paths). Path normalisation must not leave traversal sequences that bypass the path check.
   - Bundles: `engine`, `servlets-resolver`, `api` (`ResourceUtil.normalize`).
   - Violation symptom: a crafted URL executes a script under a path the search list excludes (path traversal, selector confusion, resolver bypass).
   - Severity: **CVE-class** (RCE-equivalent). **This is a recurring, realised bug class** — CVE-2024-23673 (`normalize` left `/../`, bypassing `isPathAllowed()`, enabling author→admin RCE) *(documented — CVE-2024-23673 advisory)*.

2. **JCR (Oak) ACLs are authoritative for content access; Sling does not bypass them.** A principal without `jcr:read` cannot read a node via `ResourceResolver`; a principal without write rights cannot modify it via `SlingPostServlet`. (Sling's commitment is *non-bypass*; the *semantics* of Oak's evaluation belong to Jackrabbit — §9.)
   - Violation symptom: read/write succeeds for a principal Oak's ACL denies, due to Sling using a more-privileged session or skipping the session.
   - Severity: **CVE-class**. *(inferred — §14 W3Q14)*

3. **Bundle install requires write access to a configured install path.** Only a principal with write rights on `/apps/*/install` (or another configured path) can cause `installer-provider-jcr`/`-file` to install a bundle.
   - Violation symptom: a bundle installs from an unauthorised path or by a principal lacking that write right.
   - Severity: **CVE-class** (full RCE). *(inferred — §14 W2Q8)*

### Scripting / output encoding

4. **HTL / Sightly is a contextual auto-escaping template language.** A `${expression}` emits content escaped for the surrounding HTML/attribute/JS/URI/CSS context; the compiler infers context and applies the matching filter. **This applies only to HTL** — not JSP, not any other engine.
   - Violation symptom: a value emitted via standard HTL syntax appears unescaped for its context (explicit `@context='unsafe'` is a documented escape — §9).
   - Severity: **CVE-class** for the HTL bundles. *(inferred — §14 W1Q5; well-documented in HTL spec)*

5. **`org.apache.sling.xss.XSSAPI` / `XSSFilter` produce output safe for the *requested* context.** The right `encodeForX` call yields output safe to emit in context X (HTML body, attribute, JS, URI, CSS). JSP and non-HTL templates are expected to call into this bundle.
   - Violation symptom: input survives the *correct* `encodeForX` for its context and still executes there.
   - **Scope limit (documented):** `XSSAPI.getValidHref` guarantees a **syntactically valid** href, **not** a safe URL *scheme*. It blocks `javascript:` but, by design, passes other schemes (`data:`, etc.). A request demanding scheme-safety has been treated as a *feature request*, not a vulnerability: the Javadoc does not state that the result is semantically safe, and changing the method's semantics would be a backwards-incompatible change for existing callers *(documented — `xss` Javadoc; confirmed in `XSSFilterImpl` — `isValidHref` matches an AntiSamy allow-list regex, not a scheme blocklist)*. Using `getValidHref` output as a safety guarantee against scheme-based XSS is a §9 false friend.
   - Severity: **CVE-class** for a genuine encoder-bypass in the documented context.
   - *(documented — `xss` README/Javadoc)*

### Write surface

6. **`SlingPostServlet` dispatches every write through the requesting principal's JCR session.** Regardless of `:operation`/`@TypeHint`/`@CopyFrom`/etc., the write executes against the *requesting* user's session — never a silently-elevated (admin/service-user/system) session. The bundle commits to *correct session use*; it does **not** commit to any property of Oak's ACL evaluation itself.
   - Bundle: `servlets-post`.
   - Violation symptom: SlingPostServlet performs a write via an elevated session for a non-elevated request, or bypasses the session entirely.
   - **Out of property:** "Oak allowed X it shouldn't have" → routes to Jackrabbit (§9).
   - Severity: **CVE-class**. *(inferred — §14 W3Q14)*

### Authentication

7. **Authentication handlers either authenticate or pass through; they never silently elevate.** A handler that fails to authenticate leaves the request anonymous; it does not yield a session for another principal.
   - Violation symptom: a request becomes authenticated as principal P without valid credentials for P.
   - Severity: **CVE-class**. *(inferred — §14 W2)*

8. **OAuth/OIDC and SAML2 handlers verify the cryptographic envelope** (`auth-oauth-client`, `auth-saml2`): state/nonce, audience, issuer, signature, expiry.
   - Violation symptom: an unsigned, expired, replayed, or wrong-audience assertion produces a session.
   - Severity: **CVE-class**. *(inferred — §14 W2)*

### Resource consumption

9. **No super-linear resource consumption in input size for documented operations.** *"avoid any attacks which can cause the server to consume resources in a non-linear relationship to the size of inputs"* *(documented — security policy)*.
   - Threshold: **super-linear (O(n²), exponential, unbounded recursion) on inputs of reasonable size is a bug; linear-in-input is not.**
   - Violation symptom: a small input causes disproportionate CPU/memory/time (algorithmic-complexity DoS, billion-laughs, unbounded parser recursion, ReDoS in a shipped parser).
   - Severity:
     - **CVE-class** when the blowup is easily reachable by a *single, small, attacker-controlled* input via TB1. **Realised cases:** CVE-2023-26513 (repeated merge-root segments in a `resourcemerger` path spike CPU) and CVE-2022-47937 (unbounded recursion on nesting depth in the retired `commons-json` parser → StackOverflow/OOM) *(documented — CVE-2023-26513 and CVE-2022-47937 advisories)*.
     - **VALID-HARDENING** when the blowup needs careful crafting, large multi-step inputs, or rare runtime conditions.
   - *(documented threshold; severity calibration inferred — §14 W3Q17)*

### Output integrity

10. **`rewriter` does not itself introduce XSS;** transformations emitting attacker-controlled content do so through the configured escaping policy.
    - Severity: **CVE-class** for the bundle. *(inferred — §14 W3)*

11. **Logging does not allow log forging via request data.** Values derived from a request (URL, headers) that reach the log are neutralised against CRLF/control-character injection.
    - Violation symptom: a request containing `%0A%0D…` forges fake log lines (log injection / log forging).
    - Severity: **CVE-class**. **Realised:** CVE-2022-32549 (CRLF in URL forged log lines via `commons-log` logback integration *and* the engine request-progress logger — two bundles fixed) *(documented — CVE-2022-32549 advisory)*.

### Distribution

12. **Content distribution authenticates the *peer*, not the *payload*.** The receiver requires a configured peer identity (HTTP Basic, OAuth, mTLS, or Kafka SASL/ACL); no anonymous network party can inject content. Sling provides **no** payload signature/MAC/replay-nonce. The HTTP-transport `Digest` header is a corruption checksum, not a MAC.
    - Bundles: `distribution-{api,core,journal,journal-kafka}`.
    - Violation symptom: a request without/with-forged peer credentials mutates the receiver; or the digest check fails to reject a corrupted payload.
    - Severity: **CVE-class** for peer-auth bypass / digest-check bypass.
    - **Explicitly disclaimed:** payload integrity between authenticated peers (§9). *(inferred — §14 W2Q9)*

### Configuration / CSRF

13. **`ReferrerFilter` rejects state-changing requests whose Referer/Origin is not allow-listed** (default-deny for cross-origin POST/PUT/DELETE). This CSRF protection is treated as load-bearing — it was the basis for declining a POST-only XSS report on the grounds that the cross-origin write could not be triggered (the CVE-2019-0198 withdrawal; see §11a) *(documented — CVE-2019-0198 reservation/withdrawal)*.
    - Violation symptom: a cross-origin write succeeds despite a non-whitelisted Referer.
    - Severity: **CVE-class** for the bundle. *(inferred — §14 W3)*

14. **`ContentDispositionFilter` forces `Content-Disposition: attachment` for binary downloads under configured paths**, mitigating XSS via uploaded SVG/HTML.
    - Violation symptom: a binary from a configured path is served without the header.
    - Severity: **CVE-class** for the bundle. *(inferred — §14 W3)*

15. **The OSGi Web Console is a fully-privileged operator surface that must be access-restricted.** *"Access to the Web Console must be restricted and only permitted to users trusted with total control over the deployment"* *(documented — CWiki Threat model)*. Sling provides `extensions-webconsolesecurityprovider` as a hardening building block; the network/credential restriction itself is the operator's (§10).
    - Severity: **CVE-class** if a Sling bundle weakens an operator's configured restriction; the *absence* of a restriction in an example assembly is `OUT-OF-MODEL: non-default-build`.

---

## §9 Security properties Sling does *not* provide

- **No defense against generic DoS** *(documented)*: streaming-bomb uploads, request floods, repeated identical requests, slowloris — deployer rate-limits at the edge.
- **No transport security (TLS)** — deployer terminates upstream *(inferred — §14 W2)*.
- **No rate limiting / brute-force protection** on login endpoints *(inferred — §14 W2)*.
- **No multi-tenant isolation within a single JVM** *(inferred — §14 W1Q7)*.
- **No sandbox for bundle code, or for non-HTL script engines.** A bundle, or a JSP/Groovy/JS script, runs as full Java; the ability to deploy code is treated as total compromise (§3, §7) *(documented — Sling security-team practice)*.
- **No CSRF protection if `ReferrerFilter` is disabled/misconfigured** by the operator.
- **No constant-time comparison guarantees anywhere** unless a specific bundle's docs explicitly claim it (none do by default). Treat any equality check on a secret as non-constant-time *(inferred — §14 W3Q16)*.
- **No auto-escaping in JSP or in JavaScript/Groovy/FreeMarker/Thymeleaf/ESX.** Only HTL escapes for you; for everything else the author must call `XSSAPI`. JSTL `<c:out>` is a language convention, not a Sling property *(inferred — §14 W1Q5a/Q5b)*.
- **`XSSAPI.getValidHref` does not enforce a safe URL scheme** — see §8 #5. Syntactic validity ≠ semantic safety *(documented — `xss` Javadoc + source inspection)*.
- **No payload integrity/authenticity in distribution between authenticated peers.** A peer with a legitimate identity can ship arbitrary content; Sling does not sign/MAC payloads *(inferred — §14 W2Q9)*.
- **No content-package signature verification.** `installer-factory-packages`, `feature-extension-content`, `jcr-contentloader` accept FileVault packages without authorship verification *(inferred — §14 W2Q11)*.
- **No defensive parsing of `repoinit` input** — the parser assumes operator-controlled input *(inferred — §14 W2Q10)*.
- **No claim on Oak's ACL evaluation semantics** — reports of the shape "Oak permitted X" route to Jackrabbit, not Sling *(inferred — §14 W3Q14)*.
- **No script-source confidentiality.** Scripts under `/apps`/`/libs` are *content*; their readability is whatever the assembly's JCR ACLs allow. Secrets in script source is a §11 misuse, not a Sling failure *(inferred — §14 W3Q18)*.
- **No assembly-level guarantee.** Properties attach to bundles, not to the Starter/Sling CMS as a whole *(inferred — §14 W1Q1)*.
- **`getValidHref`, MIME detection, and the distribution `Digest` header are not the security primitives they resemble** — see false friends below.

### False friends (look like a security primitive; are not)

- **`XSSAPI.getValidHref` is not a safe-URL gate** — it validates href *syntax*, not scheme; `data:` and others pass *(documented — `xss` Javadoc + source inspection)*.
- **`commons-mime` content-type detection is advisory, not a security gate.** Treating detected MIME as proof of file shape is unsafe — Sling and downstream parsers (e.g., Tika) may disagree *(inferred — §14 W3Q15)*.
- **The distribution HTTP `Digest` header is a corruption checksum, not a MAC** *(inferred — §14 W2Q9)*.
- **`commons-crypto` is a convenience layer**; it accepts algorithms (short MACs, deprecated ciphers) that look usable but are inappropriate for new designs, and makes no constant-time guarantee *(inferred — §14 W3Q15)*.
- **A JCR session is an authorization context, not a MAC of its contents** *(inferred — §14 W3Q15)*.

### Well-known attack classes left to the caller

- **XXE in XML parsing** — mitigated by `jaxp-configurator` (deployer must include it, start it first); custom assemblies that don't are exposed *(documented — CVE-2025-66516)*.
- **Server-Side Template Injection** in FreeMarker/Thymeleaf/JSP/Groovy when operator templates concatenate untrusted input.
- **Expression-language injection** — e.g., Jexl in `pipes`; rejected as a vuln because it required admin rights to reach, but the class exists for operator-written pipes *(documented — Sling security-team practice)*.
- **ReDoS** in operator-supplied regexes (`auth-core` requirements, `ReferrerFilter` allow-lists, `urlrewriter` rules).
- **SSRF** from operator script HTTP calls (`commons-messaging-mail`, custom `HttpClient`).
- **Open redirect** via `:redirect` or auth-handler redirect params when not host-restricted (CVE-2017-15700 class).
- **Billion-laughs / unbounded recursion** in custom or legacy parsers (CVE-2022-47937 class).

---

## §10 Downstream responsibilities

The deployer / assembler must:

1. **Build a non-vulnerable assembly** — Sling depends on the lowest compatible versions; you pick the resolution *(documented)*.
2. **Treat the Starter as a starting point, not a production runtime** — harden every default *(inferred — §14 W1Q1)*.
3. **Include and start `jaxp-configurator` first** in any assembly parsing user XML/PDF/Office *(documented — CVE-2025-66516)*.
4. **Lock down the Web Console** — remove from production, restrict by network, or replace its auth with `extensions-webconsolesecurityprovider`; **change default credentials** *(documented — CWiki Threat model)*.
5. **Change all default credentials**, including the JCR `admin` password, before any non-dev exposure.
6. **Network-restrict `/system/console`, `/system/sling/*`, `jcr-davex`, `jcr-webdav`, `jmx-provider`, `mcp-server`** to the operator network.
7. **Set ACLs explicitly via `repoinit`** for `/apps`, `/libs`, `/etc`, and any `*/install` path — default-deny write for non-admins. Granting write on `/apps` or `/libs` to a non-admin is RCE-equivalent (§11).
8. **Configure `ReferrerFilter`** for the real production origin set; do not disable it.
9. **Configure `ContentDispositionFilter`** for all paths serving user-uploaded binaries.
10. **Configure `sling.auth.requirements`** so no path is unintentionally anonymous.
11. **Provide edge rate-limiting / WAF** for login, write, and search endpoints (§9).
12. **Run TLS** at the reverse proxy.
13. **Minimise the `loginAdministrative` whitelist**; prefer per-bundle service users.
14. **For distribution:** TLS the channel, ACL the Kafka topic, do not expose the receive endpoint to untrusted networks, and **sign packages externally** if per-payload authenticity is needed — Sling authenticates peers, not payloads.
15. **Treat content-package install as privileged** — never accept packages from untrusted sources; Sling does not verify authorship.
16. **Do not deploy samples/examples/test bundles to production** (§3).
17. **Audit installed scripting engines** — each expands the attack surface; only HTL auto-escapes.
18. **In JSP and non-HTL templates, call `XSSAPI` for every attacker-controlled output**, using the encoder for the *correct* context. Do not rely on `getValidHref` for scheme safety.
19. **Do not assume intra-JVM multi-tenancy isolation.**
20. **Migrate off retired modules** (`commons-json` → Johnzon) — CVE-2022-47937 will not be fixed in the retired module *(documented — CVE-2022-47937 advisory)*.
21. **Treat `repoinit` scripts as source code** — they create users/ACLs at boot; supply chain matters.

---

## §11 Known misuse patterns

- **Granting `jcr:write` on `/apps` or `/libs` to non-admins** — RCE-equivalent.
- **Forgetting that `sling:resourceType` is itself writable** — a user who can edit a node can point its type at a script search-path entry; stored-XSS-as-RCE if the script trusts content.
- **Relying on `getValidHref` for safe URL schemes** — it only checks href syntax (§8 #5, §9).
- **Treating MIME detection as a security gate** for uploads.
- **Calling `loginAdministrative` from a bundle with no service-user mapping** instead of receiving a scoped service user.
- **Exposing `/system/console/*` to the public internet** — the most common first-deployment mistake (the CWiki stub exists specifically because of this).
- **Composing FreeMarker/Thymeleaf/JSP/Groovy templates from request parameters** — SSTI.
- **Disabling `ReferrerFilter` "for convenience" in dev** and shipping that config to production.
- **Running Sling Starter as a production server.**
- **Sharing mutable per-user state across requests** — e.g., a `static` injection field in a Sling Model. A reported "session leak" of this shape has been treated as a developer programming error (unprotected shared mutable state), not a Sling vulnerability *(documented — Sling security-team practice)*.
- **Using the retired `commons-json` on untrusted input** — unbounded recursion (CVE-2022-47937).
- **Trusting selectors/extensions to be benign** — selectors steer script selection; operator scripts must validate any selector they branch on.

## §11a Known non-findings (recurring false positives)

*(Grounded in published CVE advisories and established Sling security-team triage practice.)*

- **"Dependency X has CVE Z."** Not a Sling bug unless X is embedded, shipped directly in a Sling assembly, a Maven plugin, or Node.js (Dependabot policy). Verify *actual usage*, not just presence. `DEPENDENCY-NON-FINDING` *(documented — Dependabot wiki + Sling security-team practice)*.
- **"Default `admin`/`admin` Web Console / JCR password in production."** `OUT-OF-MODEL: non-default-build` *(documented — CWiki stub; §14 W1Q2/Q3)*.
- **"Sling Starter ships weak ACLs / anonymous can read `/content/*.json`."** Starter is an example assembly; production ACLs are the operator's. `OUT-OF-MODEL: non-default-build` *(inferred — §14 W1Q1)*.
- **"`/system/console/configMgr` exposes secrets."** Must not be on a public listener. `OUT-OF-MODEL: trusted-input`.
- **"Crafted file download via the JSON renderer (e.g., suffix `.exe`)."** Treated as not exploitable — the response body is always valid JSON regardless of the suffix, so nothing executable is produced; a quality issue, not a vulnerability. `BY-DESIGN: property-disclaimed` *(documented — Sling security-team practice)*.
- **"XSS in `HtmlResponse`/`location` via a POST parameter."** The reserved CVE-2019-0198 was **withdrawn** — judged not exploitable because the vector is a POST request and cross-origin writes are blocked by CSRF protection (§8 #13); fixed as defense-in-depth without a live CVE. `KNOWN-NON-FINDING` *(documented — CVE-2019-0198 reservation/withdrawal)*.
- **"`getValidHref` allows `data:` URLs."** By design — syntactic validity only; changing it is a backward-incompatible feature request. `BY-DESIGN: property-disclaimed` *(documented — `xss` Javadoc; Sling security-team practice)*.
- **"Session leak in Sling Models."** Requires the developer to misuse a static field for per-user state. `BY-DESIGN: property-disclaimed` (developer error) *(documented — Sling security-team practice)*.
- **"Jexl expression injection in `pipes`."** Requires administrative privileges to reach, so the code-deploy/admin-scope boundary (§3, §7) applies. `OUT-OF-MODEL: adversary-not-in-scope` *(documented — Sling security-team practice)*.
- **"JNDI/RMI lookup in `jcr-base` `RepositoryAccessor`."** This *was* accepted (CVE-2023-25621) and the endpoint removed — so a report on a fixed version is `VALID`; on a current version it's a `KNOWN-NON-FINDING` (feature gone) *(documented — CVE-2023-25621 advisory)*.
- **"Groovy/JSP/JS script can call `Runtime.exec`."** Yes — non-HTL scripts run as Java; either operator-written (trusted) or the attacker had `jcr:write` on a script path (already RCE-equivalent). `BY-DESIGN: property-disclaimed` *(documented — Sling security-team practice on code-deploy scope)*.
- **"FreeMarker/Thymeleaf/ESX/JSP output is not auto-escaped."** Correct — only HTL is. Authors must call `XSSAPI`. `BY-DESIGN: property-disclaimed`.
- **"Streaming X GB to a POST exhausts memory."** Generic DoS. `BY-DESIGN: property-disclaimed` *(documented)*.
- **"`commons-json` OOM/StackOverflow on nested input."** Accepted as CVE-2022-47937 with **no code fix** — the module is retired; remedy is migration to Johnzon. A report on the retired module → point to the advisory and Johnzon; not a fixable Sling bug *(documented — CVE-2022-47937 advisory)*.
- **"Tika XXE/SSRF via attacker PDF."** If the assembly included and started `jaxp-configurator`, a mitigation failure is `VALID` against that bundle; if it didn't, the assembly choice is `OUT-OF-MODEL: trusted-input` against Sling. The Tika exposure itself is punted upstream to Jackrabbit Oak / Tika *(documented — CVE-2025-66516 advisory)*.
- **"`/.git` exposed on sling.apache.org."** Website-infrastructure hygiene, fixed in `sling-site`; the site source is already public, so it discloses nothing new. `OUT-OF-MODEL: unsupported-component` (site/infra) *(documented — Sling security-team practice)*.
- **"`commons-scheduler` sets `org.terracotta.quartz.skipUpdateCheck`; `commons-log` sets `java.util.logging.config.class`; `feature-launcher` registers a shutdown hook."** All scoped/by-design (§5). `KNOWN-NON-FINDING` *(inferred — §14 W2Q13)*.
- **"`resourceresolver` reads environment variables."** `StringInterpolationProviderImpl` reads `System.getenv` for operator-configured placeholders — opt-in, operator-controlled. `OUT-OF-MODEL: trusted-input` *(inferred — §14 W2Q13)*.
- **"Oak permitted access I didn't expect."** Routes to Jackrabbit/Oak — Sling claims non-bypass, not Oak's evaluation semantics. *(inferred — §14 W3Q14)*
- **"Non-constant-time equality in an auth bundle."** Generically disclaimed (§9). `BY-DESIGN: property-disclaimed` unless that bundle's docs claimed constant-time *(inferred — §14 W3Q16)*.
- **"`cve-2025-54988-poc/` is a malicious file in the repo."** A research POC checked into this working tree; not Sling code, not in any release. `OUT-OF-MODEL: unsupported-component`.

---

## §12 Conditions that would change this model

Per the HEAD-tracking binding (§1), the model is revised in place. Triggers:

- A new scripting bundle gains a §8 contextual-escape claim (today only HTL).
- A new auth handler introduces a new actor or trust boundary.
- A new external-network surface ships, or `mcp-server`'s default posture changes.
- A shipped-but-unsupported component (samples, archetypes, tooling) is promoted into the supported set.
- A default change to any §5a per-bundle knob that flips a security-relevant value (e.g., the engine include content-type check flipping on by default).
- Distribution adds a transport or gains a payload-authenticity mechanism (§8 #12 changes).
- Adoption of mandatory bundle/package signing.
- A change in the minimum Java version, OSGi framework, or Oak version.
- **A vulnerability report that cannot be cleanly routed to a §13 disposition** — the correct response is to revise the model (add the property to §8/§9), not to make an ad-hoc call.
- **Drift between this document, `security.html`, and the CWiki Threat model page** — resolving the three-way coexistence (§14 M2) and keeping them consistent thereafter.

## §13 Triage dispositions

> **Status: tentative** — the label set is proposed by this draft; the Sling PMC has not ratified it or aligned it with existing security-team practice (which historically operated without an explicit label vocabulary). Labels may be renamed/merged; the *closed-set* property and the section citations must be preserved (§14 M1).

| Disposition | Meaning | Licensed by |
|---|---|---|
| `VALID` | Violates a §8 property via an in-scope §7 adversary with §6-attacker-controllable input. | §6, §7, §8 |
| `VALID-HARDENING` | No §8 property violated, but a §11 misuse is easy enough that Sling elects to harden (often **opt-in, off by default** for backward compat — cf. CVE-2022-45064). Fixed at PMC discretion; may or may not get a CVE. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires attacker control of a §6 parameter marked trusted (repoinit, content-package source, operator config, env-var placeholders). | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires a §7-excluded capability (admin, code deploy, JVM control, side-channel observation). | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in §3 code (samples, archetypes, tooling, site/infra, POC). | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a §5a knob / assembly default the maintainer marks dev-only. | §5a |
| `OUT-OF-MODEL: assembly-level` | About an assembly's composition/defaults (Starter, Sling CMS), not a per-bundle property. | §1, §2 |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 disclaimed property or false friend. | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a pattern. | §11a |
| `DEPENDENCY-NON-FINDING` | Vulnerable transitive dependency outside the Dependabot exceptions; verify actual usage first. | §3 / Dependabot wiki |
| `PUNT-UPSTREAM` | The flaw is in Jackrabbit Oak / Tika / Felix / another upstream; coordinate there. (Cf. CVE-2025-66516 → Oak/Tika.) | §3, §9 |
| `MODEL-GAP` | Cannot be routed to the above; triggers a §12 revision. | (none — by definition) |

---

## §14 Open questions for the maintainers

Grouped in waves (skill §3.2). Each carries a **proposed answer** for the PMC to confirm, correct, or strike. Every *(inferred)* tag in the body routes to a question here; meta-questions (M1–M3) have no body claim behind them.

### Wave 1 — Scope and deployment shape *(highest priority — reshapes §2/§3/§5a/§8/§9)*

- **W1Q1 — Unit of supported product.** *Proposed:* the individual OSGi bundle; Starter/Sling CMS are example assemblies and their defaults are dev-convenience, not Sling properties. → §1, §2, §3, §5a, §9, §13.
- **W1Q2 / Q3 — Default Web Console / JCR `admin` credentials.** *Proposed:* `OUT-OF-MODEL: non-default-build`. → §5a, §10, §11a.
- **W1Q4 — `mcp-server` posture.** *Proposed:* opt-in, not in Starter, same posture as Web Console. → §5a, §10.
- **W1Q5 / Q5a / Q5b — Scripting escape contracts.** *Proposed:* only HTL has a §8 auto-escape contract; JSP and the others require explicit `XSSAPI`. → §5a, §8 #4/#5, §9.
- **W1Q7 — Multi-tenant isolation.** *Proposed:* out of scope; single-tenant per JVM. → §3, §9.
- **W1Q8 — Role decomposition.** *Proposed:* the §2 role table is correct and complete. → §2, §7.

### Wave 2 — Trust boundaries, reachability, environment

- **W2Q8 — Installer reachability.** *Proposed:* in-model only for a low-priv principal; "admin installed a bad bundle" is by design. → §4, §8 #3.
- **W2Q9 — Distribution payload integrity.** *Proposed:* peer-authenticated only; no payload signature/MAC/replay nonce; `Digest` is a checksum. → §4, §7, §8 #12, §9, §10.
- **W2Q10 — repoinit trusted input.** *Proposed:* parser input is operator-controlled; adversarial-input crashes are not Sling bugs. → §4, §6, §9.
- **W2Q11 — Content-package / cpconverter trust.** *Proposed:* `feature-cpconverter` build-time (§3); runtime package factories accept packages without authorship verification. → §4, §9, §10.
- **W2Q13 / Q13a — Negative environment claims & Java baseline.** *Proposed:* core does not spawn processes / install signal handlers; the env-var read and four global-state writes in §5 are the only exceptions; current Java baseline is 8 min / 11 LTS (confirm whether the 2019 wiki figure is still accurate). → §5, §11a.

### Wave 3 — Properties (§8/§9) ratification

- **W3Q14 — PostServlet/Oak split.** *Proposed:* SlingPostServlet claims *correct session use*, not Oak's ACL semantics; "Oak permitted X" routes to Jackrabbit. → §8 #6, §9, §11a.
- **W3Q15 — False-friends list.** *Proposed:* adopt the §9 list (getValidHref, MIME, Digest, commons-crypto, JCR session). → §9.
- **W3Q16 — Constant-time policy.** *Proposed:* keep generic — assume non-constant-time everywhere unless a bundle's docs say otherwise. → §9, §11a.
- **W3Q17 — Super-linear severity threshold.** *Proposed:* CVE-class only for small, easily-reachable, single-shot TB1 inputs; otherwise `VALID-HARDENING`. → §8 #9, §11a.
- **W3Q18 — Script-source confidentiality.** *Proposed:* scripts are content; readability is per assembly ACLs; secrets-in-scripts is misuse. → §9, §11a.

### Open meta-questions for the PMC

- **M1 — §13 label ratification.** Adopt the proposed closed set, or align to existing practice? In practice the team applies *de facto* categories (dependency-non-finding, code-deploy/admin-scope, app-vs-framework, opt-in-not-default hardening, unsupported-when-assigned for retired modules) but has no written label vocabulary. *Proposed:* adopt as drafted; rename if a house style emerges. → §13.
- **M2 — Three-way coexistence and publication venue.** There are now three artifacts: `security.html` (process + 2 non-findings), the CWiki **Threat model** stub (one Web Console paragraph), and this document. *Proposed:* this document becomes canonical; the CWiki stub is replaced by a link to it; `security.html` stays as the reporting-process front door and links here. Publish at `site/.../project-information/threat-model.md`. Who owns drift detection going forward? → §1, §12.
- **M3 — Security-team-practice `(documented)` tags.** Several claims are tagged *(documented — Sling security-team practice)* on the strength of how past security reports were triaged, summarised here as policy without reproducing any private discussion. *Proposed:* the PMC confirms these positions still reflect current policy and are acceptable to state in a public document. → throughout.

---

## §15 Machine-readable companion

Deferred until §14 is resolved. A `docs/threat-model.yaml` sidecar would encode, for automated/AI triage: the §2 component families (in/out + reason), the §5a knobs (default + stance), the §6 endpoints (route → parameter trust), the §8 properties (id, statement, conditions, violation symptom, severity, provenance), the §9 disclaimed/false-friend list, the §11a non-findings, and the §13 dispositions. The prose remains canonical; regenerate the sidecar whenever it changes.

---

## Appendix A — Back-map from `security.html` (coverage proof)

| `security.html` statement | This model | Tag |
|---|---|---|
| "we don't consider vulnerable dependencies of our bundles as security issues" | §3, §13 `DEPENDENCY-NON-FINDING` | *(documented)* |
| "Sling depends on the lowest possible version of a library/API" | §3 | *(documented)* |
| Dependabot exceptions (embedded / Maven-plugin / Starter / Node / oldest-secure) | §3 exception list | *(documented)* |
| "avoid any attacks which can cause the server to consume resources in a non-linear relationship to the size of inputs" | §3, §8 #9 threshold | *(documented)* |
| "configuration questions … to our public users mailing list" | §1 reporting cross-reference | *(documented)* |
| reporting address `security@sling.apache.org` | §1 | *(documented)* |

## Appendix B — CVE history → model claim (pattern back-map)

Per the skill, a CVE list is not the threat model, but a *pattern across CVEs* is a model claim. Every issued Sling CVE maps to a §8 property (or a §9 disclaimer / §11a non-finding), confirming the model covers the realised bug classes.

| CVE | Class | Bundle(s) | Maps to |
|---|---|---|---|
| CVE-2017-15700 | Open redirect / credential hijack (`AuthUtil.isRedirectValid`) | `auth-core` | §8 #8 (redirect validation), §9 open-redirect class |
| CVE-2019-0198 | XSS in `HtmlResponse` — **CVE withdrawn** (POST + CSRF) | api/engine | §11a (KNOWN-NON-FINDING), §8 #13 (CSRF) |
| CVE-2022-32549 | Log injection (CRLF) | `commons-log` + engine logger | §8 #11 (log forging) |
| CVE-2022-45064 | Include-based XSS (content-type confusion); opt-in fix | `engine` | §8 #4/#5, §5a (opt-in knob), §13 `VALID-HARDENING` pattern |
| CVE-2022-47937 | DoS — unbounded parser recursion; **no fix, retired** | `commons-json` | §8 #9, §10 #20, §11a |
| CVE-2023-22849 | Stored/reflected XSS in CMS UI | `app-cms` (assembly) | §2 (Sling CMS), §8 #4/#5 |
| CVE-2023-25621 | JNDI/RMI injection; endpoint removed | `jcr-base` | §8 #2/#3, §9 (deserialization/lookup class) |
| CVE-2023-26513 | DoS — high CPU via crafted merge path | `resourcemerger` | §8 #9 (super-linear) |
| CVE-2024-23673 | RCE via path-traversal resolution bypass | `servlets-resolver` + `api` | §8 #1 (search-path integrity) |

*Discussed as dependency/upstream issues only (not Sling-issued):* CVE-2018-20433 (plexus-utils, test-scope — rejected), CVE-2024-12801 (logback — dependency challenge), CVE-2025-66516 (Tika via Oak — `PUNT-UPSTREAM` + `jaxp-configurator` mitigation), CVE-2021-44228 (Log4Shell — discussed).

---

*This is a draft (v1). No claim is authoritative for triage until the PMC has answered the §14 waves and the inferred tags are promoted to (maintainer). Until then, use it as input to a conversation, not as triage policy. Claims attributed to "Sling security-team practice" reflect the project's historical triage of security reports; the PMC should confirm they remain current policy (§14 M3).*
