package dockertest

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"os/exec"
	"strings"
	"testing"
	"time"
)

type Network struct {
	Name string
}

type Container struct {
	ID string
}

type WaitStrategy struct {
	typ       string
	port      string
	httpPath  string
	logSubstr string
	timeout   time.Duration
}

type ContainerFile struct {
	Reader        io.Reader
	ContainerPath string
}

type ContainerRequest struct {
	Image          string
	Platform       string
	Cmd            []string
	Env            map[string]string
	ExposedPorts   []string
	ExtraHosts     []string
	Network        string
	NetworkAliases []string
	Files          []ContainerFile
	PostStart      [][]string
	WaitFor        *WaitStrategy
}

func WaitForHTTP(path, port string, timeout time.Duration) *WaitStrategy {
	return &WaitStrategy{typ: "http", port: port, httpPath: path, timeout: timeout}
}

func WaitForPort(port string, timeout time.Duration) *WaitStrategy {
	return &WaitStrategy{typ: "tcp", port: port, timeout: timeout}
}

func WaitForLog(substr string, timeout time.Duration) *WaitStrategy {
	return &WaitStrategy{typ: "log", logSubstr: substr, timeout: timeout}
}

func CreateNetwork(t *testing.T) *Network {
	t.Helper()
	name := fmt.Sprintf("test-%d", time.Now().UnixNano())
	run(t, "docker", "network", "create", name)
	t.Cleanup(func() { runQuiet("docker", "network", "rm", name) })
	return &Network{Name: name}
}

func StartContainer(t *testing.T, req ContainerRequest) *Container {
	t.Helper()

	args := []string{"run", "-d"}
	if req.Platform != "" {
		args = append(args, "--platform", req.Platform)
	}
	for _, port := range req.ExposedPorts {
		args = append(args, "-p", "0:"+normalizePort(port))
	}
	for k, v := range req.Env {
		args = append(args, "-e", k+"="+v)
	}
	for _, h := range req.ExtraHosts {
		args = append(args, "--add-host", h)
	}
	if req.Network != "" {
		args = append(args, "--network", req.Network)
		for _, alias := range req.NetworkAliases {
			args = append(args, "--network-alias", alias)
		}
	}
	args = append(args, req.Image)
	args = append(args, req.Cmd...)

	id := strings.TrimSpace(run(t, "docker", args...))
	c := &Container{ID: id}
	t.Cleanup(func() {
		if t.Failed() {
			if out, err := exec.Command("docker", "inspect", c.ID, "--format",
				"ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} Running={{.State.Running}}").CombinedOutput(); err == nil {
				t.Logf("dockertest: container %s state: %s", c.ID[:12], out)
			}
			if logs, err := exec.Command("docker", "logs", "--tail", "50", c.ID).CombinedOutput(); err == nil {
				t.Logf("dockertest: container %s logs:\n%s", c.ID[:12], logs)
			}
		}
		runQuiet("docker", "rm", "-f", c.ID)
	})

	for _, f := range req.Files {
		copyFile(t, c.ID, f)
	}
	for _, cmd := range req.PostStart {
		c.Exec(t, cmd)
	}
	if req.WaitFor != nil {
		waitReady(t, c, req.WaitFor)
	}
	return c
}

func (c *Container) MappedPort(t *testing.T, containerPort string) string {
	t.Helper()
	output := run(t, "docker", "port", c.ID, normalizePort(containerPort))
	line := strings.TrimSpace(strings.Split(output, "\n")[0])
	_, hostPort, err := net.SplitHostPort(line)
	if err != nil {
		idx := strings.LastIndex(line, ":")
		if idx < 0 {
			t.Fatalf("dockertest: unexpected docker port output: %q", line)
		}
		hostPort = line[idx+1:]
	}
	return hostPort
}

func (c *Container) HostPort(t *testing.T, containerPort string) string {
	t.Helper()
	return "localhost:" + c.MappedPort(t, containerPort)
}

func (c *Container) Exec(t *testing.T, cmd []string) string {
	t.Helper()
	args := append([]string{"exec", c.ID}, cmd...)
	return run(t, "docker", args...)
}

func (c *Container) Logs(t *testing.T) string {
	t.Helper()
	out, err := exec.Command("docker", "logs", c.ID).CombinedOutput()
	if err != nil {
		t.Fatalf("dockertest: docker logs %s failed: %v\n%s", c.ID[:12], err, out)
	}
	return string(out)
}

type BuildRequest struct {
	Context    string
	Dockerfile string
	BuildArgs  map[string]string
	Platform   string
	Tag        string
}

func BuildImage(t *testing.T, req BuildRequest) string {
	t.Helper()
	args := []string{"build"}
	if req.Platform != "" {
		args = append(args, "--platform", req.Platform)
	}
	if req.Tag != "" {
		args = append(args, "-t", req.Tag)
	}
	if req.Dockerfile != "" {
		args = append(args, "-f", req.Dockerfile)
	}
	for k, v := range req.BuildArgs {
		args = append(args, "--build-arg", k+"="+v)
	}
	args = append(args, req.Context)
	run(t, "docker", args...)
	return req.Tag
}

func BridgeGatewayIP(t *testing.T) string {
	t.Helper()
	output := run(t, "docker", "network", "inspect", "bridge",
		"--format", `{{(index .IPAM.Config 0).Gateway}}`)
	ip := strings.TrimSpace(output)
	if ip == "" {
		t.Fatal("dockertest: could not determine Docker bridge gateway IP")
	}
	return ip
}

func normalizePort(port string) string {
	port = strings.TrimSuffix(port, "/tcp")
	port = strings.TrimSuffix(port, "/udp")
	return port
}

func run(t *testing.T, name string, args ...string) string {
	t.Helper()
	cmd := exec.Command(name, args...)
	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		t.Fatalf("dockertest: %s %s failed: %v\nstdout: %s\nstderr: %s",
			name, strings.Join(args, " "), err, stdout.String(), stderr.String())
	}
	return stdout.String()
}

func runQuiet(name string, args ...string) {
	cmd := exec.Command(name, args...)
	_ = cmd.Run()
}

func copyFile(t *testing.T, containerID string, f ContainerFile) {
	t.Helper()
	content, err := io.ReadAll(f.Reader)
	if err != nil {
		t.Fatalf("dockertest: reading file for %s: %v", f.ContainerPath, err)
	}
	cmd := exec.Command("docker", "exec", "-i", containerID,
		"sh", "-c", fmt.Sprintf("cat > '%s'", f.ContainerPath))
	cmd.Stdin = strings.NewReader(string(content))
	var stderr strings.Builder
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		t.Fatalf("dockertest: copy to %s:%s: %v\n%s", containerID[:12], f.ContainerPath, err, stderr.String())
	}
}

func waitReady(t *testing.T, c *Container, ws *WaitStrategy) {
	t.Helper()
	deadline := time.Now().Add(ws.timeout)

	for time.Now().Before(deadline) {
		switch ws.typ {
		case "http":
			hostPort := c.MappedPort(t, ws.port)
			addr := "localhost:" + hostPort
			if tryHTTP(addr, ws.httpPath) {
				return
			}
		case "tcp":
			hostPort := c.MappedPort(t, ws.port)
			addr := "localhost:" + hostPort
			if tryTCP(addr) {
				return
			}
		case "log":
			out, err := exec.Command("docker", "logs", c.ID).CombinedOutput()
			if err == nil && strings.Contains(string(out), ws.logSubstr) {
				return
			}
		}
		time.Sleep(time.Second)
	}
	t.Fatalf("dockertest: container %s not ready after %v (strategy %s)",
		c.ID[:12], ws.timeout, ws.typ)
}

func tryHTTP(addr, path string) bool {
	resp, err := (&http.Client{Timeout: 2 * time.Second}).Get(fmt.Sprintf("http://%s%s", addr, path))
	if err != nil {
		return false
	}
	resp.Body.Close()
	return resp.StatusCode >= 200 && resp.StatusCode < 400
}

func tryTCP(addr string) bool {
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		return false
	}
	conn.Close()
	return true
}
