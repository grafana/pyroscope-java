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
