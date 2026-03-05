# JVM Classloading with -javaagent

## Which classloader loads the agent?

The system (app) classloader — `ClassLoaders.AppClassLoader`. The JVM appends the agent JAR to its internal `URLClassPath` by calling `AppClassLoader.appendToClassPathForInstrumentation()`.

## Is the agent JAR appended or prepended?

Appended. It goes after all `-classpath` entries.

## Is `java.class.path` updated?

No. The system property is a static snapshot from `-classpath` only. The agent JAR is added directly to the classloader's internal `URLClassPath`, bypassing the property.

## What if the same class exists in both `-classpath` and `-javaagent` JARs?

The `-classpath` version wins. The classloader searches in order:
1. `-classpath` entries (in declaration order)
2. `-javaagent` JARs (appended in declaration order)

First match wins. The agent's `premain` is still called (determined by the agent JAR's `Premain-Class` manifest), but the actual class bytecode comes from whichever JAR the classloader finds first.

## How to verify at runtime

- `MyClass.class.getProtectionDomain().getCodeSource().getLocation()` — which JAR the class was loaded from
- `MyClass.class.getClassLoader()` — which classloader loaded it
- Reflect into `BuiltinClassLoader.ucp` (`URLClassPath`) and call `getURLs()` to see the actual search path including appended agent JARs

## VM init levels (jdk.internal.misc.VM.initLevel)

| Level | Phase |
|-------|-------|
| 0 | Pre-init |
| 1 | `initPhase1` — core lang classes (Thread, String, System) |
| 2 | `initPhase2` — module system bootstrapped |
| 3 | `initPhase3` — system classloader being instantiated |
| 4 | Fully booted — `premain` runs here |

## System classloader vs built-in app classloader

They are the same object unless `-Djava.system.class.loader=com.example.MyLoader` is set. If set, the JVM loads `MyLoader` using the built-in `AppClassLoader` as parent, and `getSystemClassLoader()` returns the custom one.
