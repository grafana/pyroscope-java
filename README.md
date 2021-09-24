# Pyroscope Java agent

The Java profiling agent for Pyroscope.io. Based on [async-profiler](https://github.com/jvm-profiling-tools/async-profiler).

## Distribution

The agent is distributed as a single JAR file `pyroscope.jar`. It contains native async-profiler libraries for:
- Linux on x64;
- Linux on x86;
- Linux on AArch64;
- Linux on ARM;
- MacOS on x64.

## Downloads

Visit [releases](/releases) page to download the latest version of `pyroscope.jar`

## Usage

To run a Java application with the agent:
```shell
java -javaagent:pyroscope.jar -jar app.jar
```

On the startup, the agent deploys the native corresponding library into `/tmp/${username}-pyroscope/`.

## Configuration

The agent is configured using environment variables.

### `PYROSCOPE_APPLICATION_NAME`
The application name used when uploading profiling data. Generated if not provided.

### `PYROSCOPE_PROFILING_INTERVAL`
Sets the profiling interval in nanoseconds or in other units, if N is followed by `ms` (for milliseconds), `us` (for microseconds), or `s` (for seconds). See [async-profiler documentation](https://github.com/jvm-profiling-tools/async-profiler) for details. The default is `10ms`.

### `PYROSCOPE_UPLOAD_INTERVAL`
Sets the profiling interval for uploading snapshots. The default is `10s`.

### `PYROSCOPE_PROFILER_EVENT`
Sets the profiling event. See [async-profiler documentation](https://github.com/jvm-profiling-tools/async-profiler) for details. The supported values are `cpu`, `alloc`, `lock`, `wall`, and `itimer`. The defaults is `itimer`.

### `PYROSCOPE_LOG_LEVEL`
The log level: `debug`, `info`, `warn`, `error`. The default is `info`.

### `PYROSCOPE_SERVER_ADDRESS`
The address of the Pyroscope server. The default is `http://localhost:4040`.

### `PYROSCOPE_AUTH_TOKEN`
The authorization token used to upload profiling data.

## Building

If you want to build the agent JAR yourself, from this repo run:

```shell
./gradlew shadowJar
```

The file will be in `agent/build/libs/pyroscope.jar`.
