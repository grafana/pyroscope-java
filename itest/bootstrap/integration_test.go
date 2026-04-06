package itest

import (
	"context"
	"io"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

func repoRoot() string {
	_, filename, _, _ := runtime.Caller(0)
	return filepath.Dir(filepath.Dir(filepath.Dir(filename)))
}

func TestBootstrapClassloader(t *testing.T) {
	ctx := context.Background()
	root := repoRoot()

	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			FromDockerfile: testcontainers.FromDockerfile{
				Context:    root,
				Dockerfile: "itest/bootstrap/Dockerfile",
				KeepImage:  true,
			},
			WaitingFor: wait.ForLog("BOOTSTRAP_CHECK").WithStartupTimeout(10 * time.Minute),
		},
		Started: true,
	}

	c, err := testcontainers.GenericContainer(ctx, req)
	require.NoError(t, err, "failed to start bootstrap-check container")
	defer func() {
		_ = c.Terminate(ctx)
	}()

	reader, err := c.Logs(ctx)
	require.NoError(t, err)
	defer reader.Close()

	data, err := io.ReadAll(reader)
	require.NoError(t, err)
	logs := string(data)

	require.False(t, strings.Contains(logs, "FAIL"),
		"Bootstrap classloader injection failed. ProfilerApi/ProfilerApiHolder were loaded "+
			"from the system classloader before BootstrapApiInjector.inject() ran.\n"+
			"Container output:\n%s", logs)
	require.True(t, strings.Contains(logs, "BOOTSTRAP_CHECK ProfilerApiHolder: PASS"),
		"Missing ProfilerApiHolder check in output.\nContainer output:\n%s", logs)
	require.True(t, strings.Contains(logs, "BOOTSTRAP_CHECK ProfilerApi: PASS"),
		"Missing ProfilerApi check in output.\nContainer output:\n%s", logs)
}
