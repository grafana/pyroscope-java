# bootstrap-api

This module contains the shared API interfaces used for cross-classloader communication
between the Pyroscope profiling agent and the
[OpenTelemetry Java agent extension](https://github.com/grafana/otel-profiling-java).

Published to Maven Central as `io.pyroscope:bootstrap-api`.

## Problem

The OTel Java agent uses a hierarchical classloader layout. The exact hierarchy
depends on how the application is launched:

**Standard `-classpath` application** (no nested classloaders):

```
Bootstrap CL
  ├── AgentClassLoader
  │     └── ExtensionClassLoader   ← OTel extension
  └── System/App CL               ← application classes + pyroscope.jar
```

**Spring Boot** (nested classloader for the fat jar):

```
Bootstrap CL
  ├── AgentClassLoader
  │     └── ExtensionClassLoader   ← OTel extension
  └── System/App CL
        └── LaunchedURLClassLoader ← Spring Boot fat jar classes + pyroscope.jar
```

**Application server** (e.g. Tomcat, with per-webapp classloaders):

```
Bootstrap CL
  ├── AgentClassLoader
  │     └── ExtensionClassLoader   ← OTel extension
  └── System/App CL
        └── WebappClassLoader      ← webapp classes + pyroscope.jar
```

In all cases, the `ExtensionClassLoader` and the classloader that loads pyroscope.jar
have **no parent-child relationship** — they are in separate branches of the tree.
Even if both classloaders contain a class with the same fully-qualified name
(e.g. `io.pyroscope.javaagent.api.ProfilerApi`), the JVM treats them as
**different types** — a cross-classloader cast will fail with `ClassCastException`.

However, all classloaders delegate to the **bootstrap classloader** first. This is
the one classloader visible to every branch of the hierarchy.

For span-profile correlation, the extension needs to call `setTracingContext()` on
the profiling agent's `ProfilerSdk` instance. This requires a shared type that both
classloaders agree on.

## Solution

The **bootstrap classloader** is the only classloader visible to both the extension
and the application. At startup, the OTel extension injects this jar into the bootstrap
classloader via
[`Instrumentation.appendToBootstrapClassLoaderSearch()`](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/Instrumentation.html#appendToBootstrapClassLoaderSearch-java.util.jar.JarFile-).

This makes `ProfilerApiHolder.INSTANCE` a single, JVM-wide rendezvous point:

```
Bootstrap CL
  ├── ProfilerApi (interface)           ← from bootstrap-api jar
  ├── ProfilerApiHolder (AtomicReference) ← from bootstrap-api jar
  └── ProfilerScopedContext (interface) ← from bootstrap-api jar

ExtensionClassLoader
  └── PyroscopeOtelSpanProcessor       ← reads ProfilerApiHolder.INSTANCE

App CL
  └── ProfilerSdk implements ProfilerApi ← sets ProfilerApiHolder.INSTANCE
```

The extension's ByteBuddy instrumentation hooks `PyroscopeAgent.start()` in the
application classloader to capture the `ProfilerSdk` instance and store it in
`ProfilerApiHolder.INSTANCE`. The span processor then reads it to call
`setTracingContext()` on every span start/end.

## Contents

| Class | Role |
|---|---|
| `ProfilerApi` | Interface with methods like `setTracingContext()`, `startProfiling()`, `registerConstant()` |
| `ProfilerApiHolder` | Static `AtomicReference<ProfilerApi>` — the cross-classloader rendezvous point |
| `ProfilerScopedContext` | Companion interface for scoped label management (deprecated) |

## Usage

### As a dependency

The Pyroscope agent (`io.pyroscope:agent`) depends on this module at compile time.
The OTel extension (`io.pyroscope:otel-profiling-java`) embeds this jar as a resource
and injects it into the bootstrap classloader at startup.

```groovy
// In the agent (compile-time only, not bundled in shadow jar)
compileOnly 'io.pyroscope:bootstrap-api:VERSION'

// In the OTel extension (embedded as resource, injected at runtime)
compileOnly 'io.pyroscope:bootstrap-api:VERSION'
```

### Versioning

This module is published alongside the Pyroscope agent with the **same version**.
Any change to these interfaces is a **breaking change** requiring a coordinated
release of both `pyroscope-java` and `otel-profiling-java`.

## How the OTel extension works

The bootstrap classloader injection approach used here is inspired by the
[opentelemetry-java-instrumentation distro example](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/examples/distro),
which demonstrates the same pattern for cross-classloader communication in OTel extensions.

The flow at runtime:

1. The OTel Java agent starts and loads extensions from `ExtensionClassLoader`.
2. `PyroscopeOtelAutoConfigurationCustomizerProvider` runs:
   - `BootstrapApiInjector.ensureInjected()` extracts the embedded `pyroscope-otel-bootstrap.jar.bin`
     resource to a temp file and calls `Instrumentation.appendToBootstrapClassLoaderSearch()`.
   - Seeds `ProfilerApiHolder.INSTANCE` with a fallback `ProfilerSdk` from the extension classloader.
   - Registers `PyroscopeOtelSpanProcessor`.
3. When the application calls `PyroscopeAgent.start()`, ByteBuddy advice
   (`ProfilerSdkInstrumentation`) intercepts it and stores the app-classloader
   `ProfilerSdk` into `ProfilerApiHolder.INSTANCE`, replacing the fallback.
4. On every span start, `PyroscopeOtelSpanProcessor` reads `ProfilerApiHolder.INSTANCE`
   and calls `setTracingContext(spanId, spanName)` to correlate profiles with traces.

## Agent loading order

The recommended configuration is to load the OTel Java agent first, with pyroscope.jar
either on the classpath or started programmatically by the application:

```
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=pyroscope-otel-javaagent-extension.jar \
     -jar app.jar
```

However, loading pyroscope.jar as a `-javaagent` **before** the OTel agent also works:

```
java -javaagent:pyroscope.jar \
     -javaagent:opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=pyroscope-otel-javaagent-extension.jar \
     -jar app.jar
```

In this case the startup sequence is:

1. `PyroscopeAgent.premain()` runs first — starts profiling immediately.
2. The OTel agent starts second and loads the pyroscope-otel extension.
3. `BootstrapApiInjector` injects bootstrap-api into the bootstrap classloader.
4. `tryLoadFromSystemClassLoader()` loads `ProfilerSdk` from the system classloader
   and sets `ProfilerApiHolder.INSTANCE`. The cast to `ProfilerApi` succeeds because
   bootstrap-api is already on the bootstrap classloader from step 3.
5. The extension calls `startProfiling()` which logs a harmless "already started" error.
6. The ByteBuddy hook on `PyroscopeAgent.start()` never fires (it was already called
   in premain), but this is fine because step 4 already set `ProfilerApiHolder.INSTANCE`.

Span-profile correlation works correctly in both configurations. The only difference is
a harmless `"Failed to start profiling - already started"` log message, and the fact that
`OTEL_PYROSCOPE_START_PROFILING=false` has no effect (premain starts profiling
unconditionally before the extension loads).
