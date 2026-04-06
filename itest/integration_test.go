package itest

import (
	"context"
	"fmt"
	"net/http"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	profilev1 "github.com/grafana/pyroscope/api/gen/proto/go/google/v1"
	querierv1 "github.com/grafana/pyroscope/api/gen/proto/go/querier/v1"
	"github.com/grafana/pyroscope/api/gen/proto/go/querier/v1/querierv1connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"
)

const pyroscopeImage = "grafana/pyroscope:1.18.0@sha256:e7edae4fd99dbb8695a1e03d7db96ab247630cf83842407908922b2f66aafc6a"
const needle = "run;Fib.lambda$appLogic$0;Fib.fib;Fib.fib;Fib.fib;Fib.fib;"

func repoRoot() string {
	_, filename, _, _ := runtime.Caller(0)
	return filepath.Dir(filepath.Dir(filename))
}

func strPtr(s string) *string { return &s }

func startPyroscope(t *testing.T, ctx context.Context, net *testcontainers.DockerNetwork) testcontainers.Container {
	t.Helper()
	t.Log("starting pyroscope...")
	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        pyroscopeImage,
			ExposedPorts: []string{"4040/tcp"},
			WaitingFor:   wait.ForHTTP("/ready").WithPort("4040/tcp").WithStartupTimeout(60 * time.Second),
		},
		Started: true,
	}
	require.NoError(t, network.WithNetwork([]string{"pyroscope"}, net)(&req))
	c, err := testcontainers.GenericContainer(ctx, req)
	require.NoError(t, err, "failed to start pyroscope container")
	return c
}

func startApp(t *testing.T, ctx context.Context, root string, dockerfile string, imageVersion string, javaVersion string, net *testcontainers.DockerNetwork) testcontainers.Container {
	t.Helper()
	t.Logf("starting app %s IMAGE_VERSION=%s JAVA_VERSION=%s ...", dockerfile, imageVersion, javaVersion)
	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			FromDockerfile: testcontainers.FromDockerfile{
				Context:    root,
				Dockerfile: dockerfile,
				BuildArgs: map[string]*string{
					"IMAGE_VERSION": strPtr(imageVersion),
					"JAVA_VERSION":  strPtr(javaVersion),
				},
				KeepImage: true,
			},
		},
		Started: true,
	}
	require.NoError(t, network.WithNetwork(nil, net)(&req))
	c, err := testcontainers.GenericContainer(ctx, req)
	require.NoError(t, err, "failed to start app container for %s %s-%s", dockerfile, imageVersion, javaVersion)
	return c
}

func getPyroscopeURL(t *testing.T, ctx context.Context, c testcontainers.Container) string {
	t.Helper()
	host, err := c.Host(ctx)
	require.NoError(t, err)
	mappedPort, err := c.MappedPort(ctx, "4040/tcp")
	require.NoError(t, err)
	return fmt.Sprintf("http://%s:%s", host, mappedPort.Port())
}

func runVariant(t *testing.T, dockerfile string, imageVersion string, javaVersion string) {
	ctx := context.Background()
	root := repoRoot()

	net, err := network.New(ctx)
	require.NoError(t, err)
	t.Cleanup(func() { _ = net.Remove(ctx) })

	pyroscopeC := startPyroscope(t, ctx, net)
	t.Cleanup(func() { _ = pyroscopeC.Terminate(ctx) })
	pyroscopeURL := getPyroscopeURL(t, ctx, pyroscopeC)
	t.Logf("pyroscope URL: %s", pyroscopeURL)

	appC := startApp(t, ctx, root, dockerfile, imageVersion, javaVersion, net)
	t.Cleanup(func() { _ = appC.Terminate(ctx) })

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
	qc := querierv1connect.NewQuerierServiceClient(http.DefaultClient, pyroscopeURL)

	var lastCollapsed string
	ok := assert.Eventually(t, func() bool {
		to := time.Now()
		from := to.Add(-time.Minute)
		resp, err := qc.SelectMergeProfile(context.Background(), connect.NewRequest(&querierv1.SelectMergeProfileRequest{
			ProfileTypeID: "process_cpu:cpu:nanoseconds:cpu:nanoseconds",
			Start:         from.UnixMilli(),
			End:           to.UnixMilli(),
			LabelSelector: fmt.Sprintf(`{service_name="%s"}`, serviceName),
		}))
		if err != nil {
			t.Logf("[%s] query error: %s", serviceName, err)
			return false
		}
		lastCollapsed = stackCollapseProto(resp.Msg, false)
		if !strings.Contains(lastCollapsed, needle) {
			t.Logf("[%s] needle not found yet", serviceName)
			return false
		}
		return true
	}, 3*time.Minute, 5*time.Second)

	if !ok {
		t.Logf("[%s] last collapsed profile:\n%s", serviceName, lastCollapsed)
		t.FailNow()
	}
}

func stackCollapseProto(p *profilev1.Profile, lineNumbers bool) string {
	allZeros := func(a []int64) bool {
		for _, v := range a {
			if v != 0 {
				return false
			}
		}
		return true
	}
	addValues := func(a, b []int64) {
		for i := range a {
			a[i] += b[i]
		}
	}

	type stack struct {
		funcs string
		value []int64
	}
	locMap := make(map[int64]*profilev1.Location)
	funcMap := make(map[int64]*profilev1.Function)
	for _, l := range p.Location {
		locMap[int64(l.Id)] = l
	}
	for _, f := range p.Function {
		funcMap[int64(f.Id)] = f
	}

	var ret []stack
	for _, s := range p.Sample {
		var funcs []string
		for i := range s.LocationId {
			locID := s.LocationId[len(s.LocationId)-1-i]
			loc := locMap[int64(locID)]
			for _, line := range loc.Line {
				f := funcMap[int64(line.FunctionId)]
				fname := p.StringTable[f.Name]
				if lineNumbers {
					fname = fmt.Sprintf("%s:%d", fname, line.Line)
				}
				funcs = append(funcs, fname)
			}
		}

		vv := make([]int64, len(s.Value))
		copy(vv, s.Value)
		ret = append(ret, stack{
			funcs: strings.Join(funcs, ";"),
			value: vv,
		})
	}
	slices.SortFunc(ret, func(i, j stack) int {
		return strings.Compare(i.funcs, j.funcs)
	})
	var unique []stack
	for _, s := range ret {
		if allZeros(s.value) {
			continue
		}
		if len(unique) == 0 {
			unique = append(unique, s)
			continue
		}

		if unique[len(unique)-1].funcs == s.funcs {
			addValues(unique[len(unique)-1].value, s.value)
			continue
		}
		unique = append(unique, s)
	}

	res := make([]string, 0, len(unique))
	for _, s := range unique {
		res = append(res, fmt.Sprintf("%s %v", s.funcs, s.value))
	}
	return strings.Join(res, "\n")
}

// Alpine variants

func TestAlpine_3_16_Java8(t *testing.T)  { runVariant(t, "alpine-test.Dockerfile", "3.16", "8") }
func TestAlpine_3_16_Java11(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.16", "11") }
func TestAlpine_3_16_Java17(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.16", "17") }

func TestAlpine_3_17_Java8(t *testing.T)  { runVariant(t, "alpine-test.Dockerfile", "3.17", "8") }
func TestAlpine_3_17_Java11(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.17", "11") }
func TestAlpine_3_17_Java17(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.17", "17") }

func TestAlpine_3_18_Java8(t *testing.T)  { runVariant(t, "alpine-test.Dockerfile", "3.18", "8") }
func TestAlpine_3_18_Java11(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.18", "11") }
func TestAlpine_3_18_Java17(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.18", "17") }
func TestAlpine_3_18_Java21(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.18", "21") }

func TestAlpine_3_19_Java8(t *testing.T)  { runVariant(t, "alpine-test.Dockerfile", "3.19", "8") }
func TestAlpine_3_19_Java11(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.19", "11") }
func TestAlpine_3_19_Java17(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.19", "17") }
func TestAlpine_3_19_Java21(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.19", "21") }
func TestAlpine_3_19_Java25(t *testing.T) { runVariant(t, "alpine-test.Dockerfile", "3.19", "25") }

// Ubuntu variants

func TestUbuntu_18_04_Java8(t *testing.T)  { runVariant(t, "ubuntu-test.Dockerfile", "18.04", "8") }
func TestUbuntu_18_04_Java11(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "18.04", "11") }
func TestUbuntu_18_04_Java17(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "18.04", "17") }

func TestUbuntu_20_04_Java8(t *testing.T)  { runVariant(t, "ubuntu-test.Dockerfile", "20.04", "8") }
func TestUbuntu_20_04_Java11(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "20.04", "11") }
func TestUbuntu_20_04_Java17(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "20.04", "17") }
func TestUbuntu_20_04_Java21(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "20.04", "21") }
func TestUbuntu_20_04_Java25(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "20.04", "25") }

func TestUbuntu_22_04_Java8(t *testing.T)  { runVariant(t, "ubuntu-test.Dockerfile", "22.04", "8") }
func TestUbuntu_22_04_Java11(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "22.04", "11") }
func TestUbuntu_22_04_Java17(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "22.04", "17") }
func TestUbuntu_22_04_Java21(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "22.04", "21") }
func TestUbuntu_22_04_Java25(t *testing.T) { runVariant(t, "ubuntu-test.Dockerfile", "22.04", "25") }
