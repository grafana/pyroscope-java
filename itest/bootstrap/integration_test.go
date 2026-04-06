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
	// integration_test.go is at itest/bootstrap/integration_test.go
	// repo root is two levels up
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
			WaitingFor: wait.ForLog("BOOTSTRAP_CHECK:").WithStartupTimeout(10 * time.Minute),
		},
		Started: true,
	}

	c, err := testcontainers.GenericContainer(ctx, req)
	require.NoError(t, err, "failed to start bootstrap-check container")
	defer func() {
		require.NoError(t, c.Terminate(ctx))
	}()

	reader, err := c.Logs(ctx)
	require.NoError(t, err)
	defer reader.Close()

	data, err := io.ReadAll(reader)
	require.NoError(t, err)
	logs := string(data)

	require.True(t, strings.Contains(logs, "BOOTSTRAP_CHECK: PASS"),
		"ProfilerApiHolder was NOT loaded by the bootstrap classloader.\n"+
			"This indicates BootstrapApiInjector.inject() did not run before "+
			"ProfilerApiHolder was first resolved.\nContainer logs:\n%s", logs)
}
