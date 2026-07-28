<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Security Policy and Security Model

## Reporting a Vulnerability

**Please do not report security issues through GitHub, the mailing lists, or any
other public channel.**

Report suspected vulnerabilities privately to the Apache Security Team at
[security@apache.org](mailto:security@apache.org). The team will forward the
report to the Apache OpenNLP PMC and coordinate the response with you. See the
[ASF security process](https://www.apache.org/security/) for what to expect.

When reporting, it helps a great deal if you can tell us which of the trust
boundaries described below your finding crosses, and what an attacker controls in
your scenario.

## Supported Versions

Security fixes are made to the most recent release of the 2.x line and to the
current 3.x line. Apache OpenNLP 1.x is end of life and receives no fixes; users
still on 1.x should upgrade.

## Security Model

### What Apache OpenNLP is

Apache OpenNLP is a **library** and a set of **command-line tools** for natural
language processing. It is not a server. It opens no listening sockets, runs no
daemons, has no user accounts, no authentication, no authorization, and no web
interface. It executes entirely inside the process and with the privileges of the
application that embeds it, or of the user who invokes the CLI.

Consequently, this security model is almost entirely about **input trust
boundaries**: which inputs OpenNLP is designed to survive when they are hostile,
and which it is not.

### The short version

- **Text you analyze is untrusted.** OpenNLP is designed to process hostile text
  safely. Crashes, hangs, and unbounded memory growth caused by crafted *text*
  are vulnerabilities. Report them.
- **Models, dictionaries, and training data are trusted.** They are closer to
  configuration or code than to data. Loading one you did not vet is comparable to
  loading a JAR you did not vet. We nevertheless harden these readers, and we
  still want to hear about weaknesses in them — see
  [Model artifacts](#model-artifacts-and-dictionaries) for exactly what that means.
- **Configuration is trusted.** System properties, file paths, and OpenNLP API
  parameters are set by the operator, not by an attacker.

### Input categories

#### Text under analysis

**Trust level: untrusted.**

The text passed to a tokenizer, sentence detector, name finder, POS tagger,
lemmatizer, parser, document categorizer, normalizer, spell checker, or any other
analysis component is expected to be arbitrary and possibly hostile. This is the
core of what OpenNLP does, and it is the boundary we take most seriously.

In scope as vulnerabilities:

- Crashes, uncaught errors, or infinite loops triggered by crafted input text.
- Catastrophic backtracking (ReDoS) in a built-in regular expression applied to
  input text.
- Memory consumption grossly disproportionate to input size — a small input that
  causes a large allocation.
- Any escape from "compute a result over this string" into file, process, or
  network access.

Not in scope: analysis being slow, or memory use being proportionate to input, for
genuinely large input. Feed OpenNLP a gigabyte of text and it will use a lot of
memory. That is arithmetic, not a vulnerability.

#### Model artifacts and dictionaries

**Trust level: trusted, but hardened as defense in depth.**

Model files (`.bin` model archives, serialized `BaseModel` artifacts, dictionary
XML, SymSpell dictionaries, ONNX models), custom feature-generator descriptors,
and training-data corpora are treated as **trusted input**. They configure and
parameterize the behavior of the library; a model can legitimately specify feature
generators and serializer classes to instantiate. Loading a model from a source
you do not trust is comparable to putting an untrusted JAR on your classpath, and
no amount of input validation makes that safe in general.

That is our position on guarantees. It is not our position on effort. We recognize
that models are shipped, downloaded, cached, and passed between systems, and that
in real deployments they do not always come from where an operator assumes. So:

- We **do not guarantee** that OpenNLP can safely load a maliciously crafted model
  file. Do not build a system whose security depends on that.
- We **do strive for it**, and we harden these paths continuously.
- We **do welcome reports** about them. Findings in this area are treated as
  security hardening improvements, and where the impact warrants it we have
  requested CVEs and will continue to — CVE-2026-42440 (unbounded allocation in
  the binary model reader) and CVE-2026-43825 (unsafe Java deserialization in the
  LIBSVM document categorizer model) were both handled this way.

Existing hardening in this area includes:

- **Bounded count fields.** `AbstractModelReader` validates outcome, predicate,
  and pattern counts against an upper bound before allocating, defaulting to
  10,000,000 and configurable at JVM startup via `-DOPENNLP_MAX_ENTRIES=<n>`.
- **Filtered Java deserialization.** `BaseModel` and `SvmDoccatModel` install an
  `ObjectInputFilter` allowlist, with limits on graph depth, reference count, and
  array length, so foreign payloads are rejected before they are materialized.
- **Hardened XML parsing.** `XmlUtil` enables secure processing and disables
  DOCTYPE declarations and external DTD and schema access, so dictionary and
  descriptor XML cannot pull in external entities.
- **Fail-fast format checks.** Binary formats validate magic numbers and version
  fields before consuming the body.

What we will generally *not* treat as a vulnerability here: a model that is simply
large and consumes proportionate memory; a model that fails to load with a clear
exception; behavior that requires the reporter to also control the classpath, the
JVM arguments, or the `OPENNLP_MAX_ENTRIES` setting.

#### Downloaded models

**Trust level: trusted, integrity-checked in transit.**

`DownloadUtil` fetches pretrained models over HTTPS from the Apache distribution
CDN (`https://dlcdn.apache.org/opennlp/`) and verifies each download against the
published SHA-512 checksum before use. A mismatch fails the load.

The base URL can be overridden with the `OPENNLP_DOWNLOAD_BASE_URL` system
property. That property is **operator configuration**. Pointing it at a host you
do not control, and then receiving a malicious model, is not a vulnerability in
OpenNLP — see [Configuration](#configuration-and-the-runtime-environment). Report
weaknesses in the download or verification logic itself: a checksum that is not
actually checked, a redirect that bypasses verification, a path traversal in how a
downloaded artifact is named or cached.

#### Training data

**Trust level: trusted.**

Training is an offline activity performed deliberately by an operator on a corpus
they have chosen. Corpus readers in `opennlp-formats` (CoNLL, AD, Brat, and the
rest) are not designed to be safe against adversarial corpora. Malformed input
should produce a clear error rather than a hang or an unbounded allocation, and we
will fix cases where it does not, but we do not consider corpus files an attacker-
controlled input class.

#### Configuration and the runtime environment

**Trust level: fully trusted.**

System properties, environment variables, file paths, `TrainingParameters`,
feature-generator descriptors, and all OpenNLP API arguments are supplied by the
operator or the embedding application. Anyone who can set these can already
influence the JVM directly. Reports whose precondition is "the attacker can set a
system property", "the attacker can replace a file on the local disk", or "the
attacker can pass arbitrary arguments to the CLI" describe an already-compromised
host and are out of scope.

#### Command-line tools

**Trust level: as trusted as the user invoking them.**

The CLI tools read the files they are told to read and write the files they are
told to write, with the invoking user's privileges. They perform no privilege
separation and are not intended to be exposed to untrusted callers, wrapped in a
web endpoint, or invoked with attacker-supplied arguments. Doing so is a
deployment decision, and its consequences are the operator's.

### Third-party dependencies

Vulnerabilities in libraries OpenNLP depends on should be reported to those
projects. We track dependency advisories and update accordingly, so please do tell
us if we are shipping a version with a known issue and have not moved.

OpenNLP's deep-learning components (`opennlp-dl`, `opennlp-dl-gpu`) delegate model
execution to ONNX Runtime, which is native code. ONNX model files are subject to
whatever trust boundary ONNX Runtime provides; OpenNLP adds no sandbox of its own.
Treat ONNX models exactly as you would treat native libraries.

### Logging

OpenNLP logs at the levels its embedding application configures. Log output can
include fragments of the text being processed — for example in warnings about
malformed input. If the text you process is sensitive, treat your logs as
sensitive too. OpenNLP does not handle credentials and does not log any.

## Known non-findings

The following are reported often enough to state in advance. These are working as
designed:

1. **Loading a model file the reporter crafted.** See
   [Model artifacts](#model-artifacts-and-dictionaries). We harden this and accept
   hardening reports, but "I crafted a model and it broke" is not on its own a
   vulnerability, and unbounded resource consumption from a model file is not one
   once the documented bounds are in place.
2. **Java deserialization reachable only through a model artifact.** Filtered as
   defense in depth; still not a supported way to accept untrusted input.
3. **Resource consumption proportionate to input size.** Large input uses
   commensurate memory and time.
4. **Anything requiring local file, classpath, or JVM-argument control.** That is
   an already-compromised host.
5. **`OPENNLP_DOWNLOAD_BASE_URL` pointed at an attacker-controlled host.**
   Operator configuration.
6. **The CLI tools reading and writing the files they were told to.** That is
   their purpose.
7. **Version disclosure.** OpenNLP's version is in the JAR metadata and is not a
   secret.

## Hardening reports are welcome

If your finding falls outside the guarantees above but you believe it makes
OpenNLP meaningfully more robust, send it anyway. We would rather triage a report
that turns out to be out of model than miss one that was not. Reports of this kind
are typically fixed in a normal release and credited in the release notes rather
than through a CVE, and we will tell you which track we are on and why.
