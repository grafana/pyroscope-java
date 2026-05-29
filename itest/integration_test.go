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

const pyroscopeImage = "grafana/pyroscope:1.18.0@sha256:e7edae4fd99dbb8695a1e03d7db96ab247630cf83842407908922b2f66aafc6a"
const needle = "run;Fib.lambda$appLogic$0;Fib.fib;Fib.fib;Fib.fib;Fib.fib;"

func startPyroscope(t *testing.T, net *dockertest.Network) string {
	t.Helper()
	t.Log("starting pyroscope...")
	c := dockertest.StartContainer(t, dockertest.ContainerRequest{
		Image:          pyroscopeImage,
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

func startApp(t *testing.T, net *dockertest.Network, image string) {
	t.Helper()
	t.Logf("starting app %s ...", image)
	dockertest.StartContainer(t, dockertest.ContainerRequest{
		Image:   image,
		Network: net.Name,
	})
}

func queryProfile(t *testing.T, pyroscopeURL string, serviceName string) (string, error) {
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
			LabelSelector: fmt.Sprintf(`{service_name="%s"}`, serviceName),
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

func runQueryProfileTest(t *testing.T, dockerfile string, imageVersion string, javaVersion string) {
	net := dockertest.CreateNetwork(t)

	pyroscopeURL := startPyroscope(t, net)
	t.Logf("pyroscope URL: %s", pyroscopeURL)

	image := buildAppImage(t, dockerfile, imageVersion, javaVersion)
	startApp(t, net, image)

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
	var lastCollapsed string
	var lastErr error
	ok := require.Eventually(t, func() bool {
		lastCollapsed, lastErr = queryProfile(t, pyroscopeURL, serviceName)
		if lastErr != nil {
			t.Logf("[%s] query error: %s", serviceName, lastErr)
			return false
		}
		if lastCollapsed == "" {
			t.Logf("[%s] empty profile", serviceName)
			return false
		}
		if !strings.Contains(lastCollapsed, needle) {
			t.Logf("[%s] needle not found yet", serviceName)
			return false
		}
		return true
	}, 3*time.Minute, 5*time.Second)

	if !ok {
		if lastErr != nil {
			t.Logf("[%s] last error: %s", serviceName, lastErr)
		}
		t.Logf("[%s] last collapsed profile:\n%s", serviceName, lastCollapsed)
		t.FailNow()
	}
}

func TestQueryProfile(t *testing.T) {
	runQueryProfileTest(t, envDockerfile(), envImageVersion(), envJavaVersion())
}
