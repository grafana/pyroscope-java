package dockertest

import (
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestNormalizePort(t *testing.T) {
	tests := []struct {
		input, want string
	}{
		{"8080/tcp", "8080"},
		{"8080/udp", "8080"},
		{"8080", "8080"},
		{"443/tcp", "443"},
	}
	for _, tt := range tests {
		if got := normalizePort(tt.input); got != tt.want {
			t.Errorf("normalizePort(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestWaitForHTTPConstructor(t *testing.T) {
	ws := WaitForHTTP("/ready", "4040/tcp", 30*time.Second)
	if ws.typ != "http" || ws.httpPath != "/ready" || ws.port != "4040/tcp" || ws.timeout != 30*time.Second {
		t.Errorf("unexpected WaitStrategy: %+v", ws)
	}
}

func TestWaitForPortConstructor(t *testing.T) {
	ws := WaitForPort("5000/tcp", 60*time.Second)
	if ws.typ != "tcp" || ws.port != "5000/tcp" || ws.timeout != 60*time.Second {
		t.Errorf("unexpected WaitStrategy: %+v", ws)
	}
}

func TestTryHTTP(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthy", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	mux.HandleFunc("/error", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	})
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	srv := &http.Server{Handler: mux}
	go func() { _ = srv.Serve(ln) }()
	t.Cleanup(func() { _ = srv.Close() })

	addr := ln.Addr().String()

	if !tryHTTP(addr, "/healthy") {
		t.Error("tryHTTP returned false for 200 endpoint")
	}
	if tryHTTP(addr, "/error") {
		t.Error("tryHTTP returned true for 500 endpoint")
	}
	if tryHTTP("127.0.0.1:1", "/nope") {
		t.Error("tryHTTP returned true for unreachable address")
	}
}

func TestTryTCP(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = ln.Close() })

	if !tryTCP(ln.Addr().String()) {
		t.Error("tryTCP returned false for listening port")
	}
	if tryTCP("127.0.0.1:1") {
		t.Error("tryTCP returned true for closed port")
	}
}

// Tests below require Docker.

func TestCreateNetwork(t *testing.T) {
	net := CreateNetwork(t)
	if net.Name == "" {
		t.Fatal("network name is empty")
	}
	if !strings.HasPrefix(net.Name, "test-") {
		t.Errorf("network name %q does not start with test-", net.Name)
	}
}

func TestStartContainerAndMappedPort(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image:        "busybox:latest",
		Cmd:          []string{"sh", "-c", "nc -l -p 8080 & sleep 60"},
		ExposedPorts: []string{"8080/tcp"},
		WaitFor:      WaitForPort("8080/tcp", 30*time.Second),
	})
	if c.ID == "" {
		t.Fatal("container ID is empty")
	}

	port := c.MappedPort(t, "8080/tcp")
	if port == "" {
		t.Fatal("mapped port is empty")
	}

	hp := c.HostPort(t, "8080/tcp")
	if !strings.HasPrefix(hp, "localhost:") {
		t.Errorf("HostPort = %q, want localhost:* prefix", hp)
	}
}

func TestContainerExec(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image: "busybox:latest",
		Cmd:   []string{"sleep", "60"},
	})
	out := c.Exec(t, []string{"echo", "hello"})
	if strings.TrimSpace(out) != "hello" {
		t.Errorf("Exec output = %q, want %q", strings.TrimSpace(out), "hello")
	}
}

func TestContainerFileInjection(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image: "busybox:latest",
		Cmd:   []string{"sleep", "60"},
		Files: []ContainerFile{
			{
				Reader:        strings.NewReader("test-content-123"),
				ContainerPath: "/tmp/testfile.txt",
			},
		},
	})
	out := c.Exec(t, []string{"cat", "/tmp/testfile.txt"})
	if strings.TrimSpace(out) != "test-content-123" {
		t.Errorf("file content = %q, want %q", strings.TrimSpace(out), "test-content-123")
	}
}

func TestContainerWithNetwork(t *testing.T) {
	net := CreateNetwork(t)
	c := StartContainer(t, ContainerRequest{
		Image:          "busybox:latest",
		Cmd:            []string{"sleep", "60"},
		Network:        net.Name,
		NetworkAliases: []string{"testhost"},
	})
	if c.ID == "" {
		t.Fatal("container ID is empty")
	}
}

func TestContainerHTTPWait(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image:        "busybox:latest",
		Cmd:          []string{"sh", "-c", "while true; do echo -e 'HTTP/1.1 200 OK\\r\\n\\r\\nok' | nc -l -p 8080; done"},
		ExposedPorts: []string{"8080/tcp"},
		WaitFor:      WaitForHTTP("/", "8080/tcp", 30*time.Second),
	})
	if c.ID == "" {
		t.Fatal("container ID is empty")
	}
}

func TestContainerEnvVars(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image: "busybox:latest",
		Cmd:   []string{"sleep", "60"},
		Env: map[string]string{
			"MY_VAR": "hello-world",
		},
	})
	out := c.Exec(t, []string{"sh", "-c", "echo $MY_VAR"})
	if strings.TrimSpace(out) != "hello-world" {
		t.Errorf("env var = %q, want %q", strings.TrimSpace(out), "hello-world")
	}
}

func TestContainerPostStart(t *testing.T) {
	c := StartContainer(t, ContainerRequest{
		Image: "busybox:latest",
		Cmd:   []string{"sleep", "60"},
		PostStart: [][]string{
			{"sh", "-c", "echo post-start-ran > /tmp/hook.txt"},
		},
	})
	out := c.Exec(t, []string{"cat", "/tmp/hook.txt"})
	if strings.TrimSpace(out) != "post-start-ran" {
		t.Errorf("PostStart hook output = %q, want %q", strings.TrimSpace(out), "post-start-ran")
	}
}

func TestBridgeGatewayIP(t *testing.T) {
	ip := BridgeGatewayIP(t)
	parsed := net.ParseIP(ip)
	if parsed == nil {
		t.Errorf("BridgeGatewayIP returned %q which is not a valid IP", ip)
	}
}

func TestBuildImage(t *testing.T) {
	dir := t.TempDir()
	err := os.WriteFile(filepath.Join(dir, "Dockerfile"), []byte("FROM busybox:latest\nRUN echo built\n"), 0644)
	if err != nil {
		t.Fatal(err)
	}
	tag := fmt.Sprintf("dockertest-build-%d:latest", time.Now().UnixNano())
	t.Cleanup(func() { runQuiet("docker", "rmi", tag) })

	got := BuildImage(t, BuildRequest{
		Context:    dir,
		Dockerfile: filepath.Join(dir, "Dockerfile"),
		Tag:        tag,
	})
	if got != tag {
		t.Errorf("BuildImage returned %q, want %q", got, tag)
	}
}

func TestBuildImageWithArgs(t *testing.T) {
	dir := t.TempDir()
	err := os.WriteFile(filepath.Join(dir, "Dockerfile"), []byte("FROM busybox:latest\nARG TEST_ARG=default\nRUN echo $TEST_ARG > /tmp/arg.txt\n"), 0644)
	if err != nil {
		t.Fatal(err)
	}
	tag := fmt.Sprintf("dockertest-buildargs-%d:latest", time.Now().UnixNano())
	t.Cleanup(func() { runQuiet("docker", "rmi", tag) })

	BuildImage(t, BuildRequest{
		Context:    dir,
		Dockerfile: filepath.Join(dir, "Dockerfile"),
		Tag:        tag,
		BuildArgs:  map[string]string{"TEST_ARG": "custom-value"},
	})

	c := StartContainer(t, ContainerRequest{
		Image: tag,
		Cmd:   []string{"cat", "/tmp/arg.txt"},
	})
	_ = c // container runs cat and exits; if it didn't fail, the build worked
}
