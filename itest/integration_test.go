package itest

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"pyroscope-java-itest/api/querier"
	"pyroscope-java-itest/dockertest"
	"pyroscope-java-itest/pyroscope/model"
	"pyroscope-java-itest/require"
)

const pyroscopeImage = "grafana/pyroscope:2.1.0@sha256:73b23dbb99f154a0803c136abafdff825475f415dd4d4587538d014c672b5a55"
const needle = "run;Fib.lambda$appLogic$0;Fib.fib;Fib.fib;Fib.fib;Fib.fib;"
const otelScopeNameLabel = "otel.scope.name"
const expectedOtelScopeName = "com.grafana.pyroscope/java"

func startPyroscope(t *testing.T, net *dockertest.Network) string {
	t.Helper()
	t.Log("starting pyroscope...")
	c := dockertest.StartContainer(t, dockertest.ContainerRequest{
		Image:          pyroscopeImage,
		Cmd:            []string{"-architecture.storage=v2", "-validation.disable-label-sanitization=true", "-segment-writer.min-ready-duration=0s", "-ingester.min-ready-duration=0s", "-metastore.min-ready-duration=0s"},
		ExposedPorts:   []string{"4040/tcp"},
		Network:        net.Name,
		NetworkAliases: []string{"pyroscope"},
		WaitFor:        dockertest.WaitForHTTP("/ready", "4040/tcp", 60*time.Second),
	})
	return fmt.Sprintf("http://%s", c.HostPort(t, "4040/tcp"))
}

func buildAppImage(t *testing.T, dockerfile string, imageVersion string, javaVersion string) string {
	t.Helper()
	tag := fmt.Sprintf("java-itest-%s-%s-%s:latest", appImagePrefix(dockerfile), imageVersion, javaVersion)
	t.Logf("building app image %s ...", tag)
	dockertest.BuildImage(t, dockertest.BuildRequest{
		Context:    repoRoot(),
		Dockerfile: filepath.Join(repoRoot(), dockerfile),
		Tag:        tag,
		BuildArgs: map[string]string{
			"IMAGE_VERSION": imageVersion,
			"JAVA_VERSION":  javaVersion,
		},
	})
	return tag
}

func appImagePrefix(dockerfile string) string {
	switch dockerfile {
	case "alpine-test.Dockerfile":
		return "alpine"
	case "ubuntu-test.Dockerfile":
		return "ubuntu"
	default:
		return "unknown"
	}
}

func startApp(t *testing.T, net *dockertest.Network, image string, env map[string]string) {
	t.Helper()
	t.Logf("starting app %s (env %v) ...", image, env)
	dockertest.StartContainer(t, dockertest.ContainerRequest{
		Image:   image,
		Network: net.Name,
		Env:     env,
	})
}

func queryProfile(t *testing.T, pyroscopeURL string, labelSelector string) (string, error) {
	t.Helper()
	qc := querier.NewClient(http.DefaultClient, pyroscopeURL)

	to := time.Now()
	from := to.Add(-time.Minute)
	maxNodes := int64(65536)
	resp, err := qc.SelectMergeStacktraces(context.Background(),
		&querier.SelectMergeStacktracesRequest{
			ProfileTypeID: "process_cpu:cpu:nanoseconds:cpu:nanoseconds",
			Start:         from.UnixMilli(),
			End:           to.UnixMilli(),
			LabelSelector: labelSelector,
			MaxNodes:      &maxNodes,
			Format:        querier.ProfileFormat_PROFILE_FORMAT_TREE,
		})
	if err != nil {
		return "", err
	}
	if len(resp.Tree) == 0 {
		return "", nil
	}
	tt, err := model.UnmarshalTree(resp.Tree)
	if err != nil {
		return "", err
	}
	buf := bytes.NewBuffer(nil)
	tt.WriteCollapsed(buf)
	return buf.String(), nil
}

func runQueryProfileTest(t *testing.T, dockerfile string, imageVersion string, javaVersion string, extraEnv map[string]string) {
	net := dockertest.CreateNetwork(t)

	pyroscopeURL := startPyroscope(t, net)
	t.Logf("pyroscope URL: %s", pyroscopeURL)

	image := buildAppImage(t, dockerfile, imageVersion, javaVersion)
	startApp(t, net, image, extraEnv)

	serviceName := serviceNameFromDockerfile(dockerfile, imageVersion, javaVersion)
	testTarget(t, pyroscopeURL, serviceName)
}

func serviceNameFromDockerfile(dockerfile string, imageVersion string, javaVersion string) string {
	// Matches PYROSCOPE_APPLICATION_NAME in the Dockerfiles:
	//   alpine-test.Dockerfile: alpine-${IMAGE_VERSION}-${JAVA_VERSION}
	//   ubuntu-test.Dockerfile: ubuntu-${IMAGE_VERSION}-${JAVA_VERSION}
	switch dockerfile {
	case "alpine-test.Dockerfile":
		return fmt.Sprintf("alpine-%s-%s", imageVersion, javaVersion)
	case "ubuntu-test.Dockerfile":
		return fmt.Sprintf("ubuntu-%s-%s", imageVersion, javaVersion)
	default:
		return fmt.Sprintf("unknown-%s-%s", imageVersion, javaVersion)
	}
}

func testTarget(t *testing.T, pyroscopeURL string, serviceName string) {
	t.Helper()
	labelSelector := fmt.Sprintf(`{service_name="%s","%s"="%s"}`, serviceName, otelScopeNameLabel, expectedOtelScopeName)
	var lastCollapsed string
	var lastErr error
	ok := require.Eventually(t, func() bool {
		lastCollapsed, lastErr = queryProfile(t, pyroscopeURL, labelSelector)
		if lastErr != nil {
			t.Logf("[%s] query %s error: %s", serviceName, labelSelector, lastErr)
			return false
		}
		if lastCollapsed == "" {
			t.Logf("[%s] empty profile for %s", serviceName, labelSelector)
			return false
		}
		if !strings.Contains(lastCollapsed, needle) {
			t.Logf("[%s] needle not found yet for %s", serviceName, labelSelector)
			return false
		}
		return true
	}, 3*time.Minute, 5*time.Second)

	if !ok {
		if lastErr != nil {
			t.Logf("[%s] last error for %s: %s", serviceName, labelSelector, lastErr)
		}
		t.Logf("[%s] last collapsed profile for %s:\n%s", serviceName, labelSelector, lastCollapsed)
		t.FailNow()
	}
}

func TestQueryProfile(t *testing.T) {
	runQueryProfileTest(t, envDockerfile(), envImageVersion(), envJavaVersion(), nil)
}

// TestQueryProfileGenuine runs the app with the bundled genuine (upstream)
// async-profiler distribution instead of the default Grafana fork.
func TestQueryProfileGenuine(t *testing.T) {
	runQueryProfileTest(t, envDockerfile(), envImageVersion(), envJavaVersion(), map[string]string{
		"PYROSCOPE_AP_DISTRIBUTION": "genuine",
	})
}

// TestQueryProfileExternalLibrary runs the app with a non-bundled libasyncProfiler,
// loaded from a path provided by the user (a genuine build baked into the test image).
func TestQueryProfileExternalLibrary(t *testing.T) {
	runQueryProfileTest(t, envDockerfile(), envImageVersion(), envJavaVersion(), map[string]string{
		"PYROSCOPE_AP_LIBRARY_PATH": "/app/libasyncProfiler.so",
	})
}
