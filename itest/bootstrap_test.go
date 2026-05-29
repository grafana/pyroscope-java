package itest

import (
	"path/filepath"
	"strings"
	"testing"
	"time"

	"pyroscope-java-itest/dockertest"
	"pyroscope-java-itest/require"
)

func TestBootstrapClassloader(t *testing.T) {
	// This test does not depend on the OS/Java matrix axes. The CI matrix is
	// derived from `go test -list`, so this name is crossed with every
	// os/java combination; run the actual check only for the default combo to
	// avoid redundant image builds.
	if envDockerfile() != "ubuntu-test.Dockerfile" || envImageVersion() != "22.04" || envJavaVersion() != "17" {
		t.Skipf("skipping bootstrap check for non-default matrix combo %s %s java %s",
			envDockerfile(), envImageVersion(), envJavaVersion())
	}

	tag := dockertest.BuildImage(t, dockertest.BuildRequest{
		Context:    repoRoot(),
		Dockerfile: filepath.Join(repoRoot(), "itest/bootstrap/Dockerfile"),
		Tag:        "java-bootstrap-check:latest",
	})

	c := dockertest.StartContainer(t, dockertest.ContainerRequest{
		Image:   tag,
		WaitFor: dockertest.WaitForLog("BOOTSTRAP_CHECK", 10*time.Minute),
	})

	logs := c.Logs(t)

	require.False(t, strings.Contains(logs, "FAIL"),
		"Bootstrap classloader injection failed. ProfilerApi/ProfilerApiHolder were loaded "+
			"from the system classloader before BootstrapApiInjector.inject() ran.\n"+
			"Container output:\n%s", logs)
	require.True(t, strings.Contains(logs, "BOOTSTRAP_CHECK io.pyroscope.javaagent.api.ProfilerApiHolder: PASS"),
		"Missing ProfilerApiHolder check in output.\nContainer output:\n%s", logs)
	require.True(t, strings.Contains(logs, "BOOTSTRAP_CHECK io.pyroscope.javaagent.api.ProfilerApi: PASS"),
		"Missing ProfilerApi check in output.\nContainer output:\n%s", logs)
}
