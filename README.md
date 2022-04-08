# Pyroscope Java agent

The Java profiling agent for Pyroscope.io. Based on [async-profiler](https://github.com/jvm-profiling-tools/async-profiler).

## Distribution

The agent is distributed as a single JAR file `pyroscope.jar`. It contains native async-profiler libraries for:
- Linux on x64;
- Linux on ARM64;
- MacOS on x64.
- MacOS on ARM64.

## Downloads

Visit [releases](https://github.com/pyroscope-io/pyroscope-java/releases) page to download the latest version of `pyroscope.jar`

## Usage

To run a Java application with the agent:
```shell
java -javaagent:pyroscope.jar -jar app.jar
```

On the startup, the agent deploys the native corresponding library into `/tmp/${username}-pyroscope/`.

## Configuration

The agent is configured using environment variables.

- `PYROSCOPE_APPLICATION_NAME`: The application name used when uploading profiling data. Generated if not provided.
- `PYROSCOPE_PROFILING_INTERVAL`: Sets the profiling interval in nanoseconds or in other units, if N is followed by `ms` (for milliseconds), `us` (for microseconds), or `s` (for seconds). See [async-profiler documentation](https://github.com/jvm-profiling-tools/async-profiler) for details. The default is `10ms`.
- `PYROSCOPE_UPLOAD_INTERVAL`: Sets the profiling interval for uploading snapshots. The default is `10s`.
- `PYROSCOPE_PROFILER_EVENT`: Sets the profiling event. See [async-profiler documentation](https://github.com/jvm-profiling-tools/async-profiler) for details. The supported values are `cpu`, `alloc`, `lock`, `wall`, and `itimer`. The defaults is `itimer`.
- `PYROSCOPE_LOG_LEVEL`: The log level: `debug`, `info`, `warn`, `error`. The default is `info`.
- `PYROSCOPE_SERVER_ADDRESS`: The address of the Pyroscope server. The default is `http://localhost:4040`.
- `PYROSCOPE_AUTH_TOKEN`: The authorization token used to upload profiling data.

### JFR format and multiple event support

Starting with version v0.5.0 JFR format is (partially) supported to be able to support multiple events (JFR is the only output format that supports [multiple events in `async-profiler`](https://github.com/jvm-profiling-tools/async-profiler#multiple-events)).
It currently supports the following events:
- jdk.ExecutionSample (supported in `pyroscope-java >= 0.5.0` and `pyroscope >= 0.13.0`), used for CPU sampling events (`itimer`, `cpu`, `wall`).
- jdk.ObjectAllocationInNewTLAB (supported in `pyroscope-java >= 0.5.0` and `pyroscope >= 0.14.0`), used for alloc sampling.
- jdk.ObjectAllocationOutsideTLAB (supported in `pyroscope-java >= 0.5.0` and `pyroscope >= 0.14.0`), used for alloc sampling.
- jdk.JavaMonitorEnter (supported in `pyroscope-java >= 0.6.0` and `pyroscope >= 0.15.0`), used for lock profiling.
- jdk.ThreadPark (supported in `pyroscope-java >= 0.6.0` and `pyroscope >= 0.15.0`), used for lock profiling.

There are several environment variables that define how multiple event configuration works:

- `PYROSCOPE_FORMAT` sets the profiler output format. The default is `collapsed`, but in order to support multiple formats it must be set to `jfr`.
- `PYROSCOPE_PROFILER_EVENT` sets the profiler event. With JFR format enabled, this event refers to one of the possible CPU profiling events: `itimer`, `cpu`, `wall`. The default is `itimer`.
- `PYROSCOPE_PROFILER_ALLOC` sets the allocation threshold to register the events, in bytes (equivalent to `--alloc=` in `async-profiler`. The default value is 0, which means that allocation profiling is disabled. Setting it to `1` will register all the events.
- `PYROSCOPE_PROFILER_LOCK` sets the lock threshold to register the events, in nanoseconds (equivalent to `--lock=` in `async-profiler`. The default value is -1, which means that allocation profiling is disabled. Setting it to `0` will register all the events.

## Building

If you want to build the agent JAR yourself, from this repo run:

```shell
./gradlew shadowJar
```

The file will be in `agent/build/libs/pyroscope.jar`.
