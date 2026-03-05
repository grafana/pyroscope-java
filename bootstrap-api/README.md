# bootstrap-api

This module contains the shared API interfaces used for cross-classloader communication
between the Pyroscope profiling agent and the
[OpenTelemetry Java agent extension](https://github.com/grafana/otel-profiling-java).

Published to Maven Central as `io.pyroscope:bootstrap-api`.

## Problem

The OTel Java agent uses a hierarchical classloader layout:

```
Bootstrap CL
  └── AgentClassLoader
        └── ExtensionClassLoader   ← OTel extension (pyroscope-otel-javaagent-extension.jar)

App CL (e.g. Spring Boot LaunchedURLClassLoader)
  └── Pyroscope agent (pyroscope.jar)
```

The OTel extension and the Pyroscope agent run in **different classloaders** that have
no parent-child relationship. Even if both classloaders contain a class with the same
fully-qualified name (e.g. `io.pyroscope.javaagent.api.ProfilerApi`), the JVM treats
them as **different types** — a cross-classloader cast will fail with `ClassCastException`.

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

For a complete example of building a custom OTel Java agent extension, see the
[opentelemetry-java-instrumentation distro example](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/examples/distro).

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
