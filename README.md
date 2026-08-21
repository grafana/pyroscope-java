# Pyroscope Java agent

The Java profiling agent for Pyroscope.io. Based
on [async-profiler](https://github.com/jvm-profiling-tools/async-profiler).

## Distribution

The agent is distributed as a single JAR file `pyroscope.jar`. It contains native async-profiler libraries for:

- Linux on x64;
- Linux on ARM64;
- MacOS on x64.
- MacOS on ARM64.

## Windows OS support

It also contains support for Windows OS, through JFR profiler. In order to use JFR as profiler in place of
async-profiler,
you need to configure profiler type, either through configuration file or environment variable.

By setting `PYROSCOPE_PROFILER_TYPE` configuration variable to `JFR`, agent will use JVM built-in profiler.

## Downloads

Visit [releases](https://github.com/pyroscope-io/pyroscope-java/releases) page to download the latest version
of `pyroscope.jar`

## Usage

Visit [docs](https://pyroscope.io/docs/java/) page for usage and configuration documentation.

## Building

If you want to build the agent JAR yourself, from this repo run:

```shell
./gradlew shadowJar
```

The file will be in `agent/build/libs/pyroscope.jar`.

## Maintainers

This package is maintained by [@grafana/pyroscope-java](https://github.com/orgs/grafana/teams/pyroscope-java).
Mention this team on issues or PRs for feedback.
