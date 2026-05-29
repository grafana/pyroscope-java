package itest

import (
	"os"
	"path/filepath"
	"runtime"
)

func envOrDefault(key, defaultValue string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultValue
}

func envDockerfile() string  { return envOrDefault("DOCKERFILE", "ubuntu-test.Dockerfile") }
func envImageVersion() string { return envOrDefault("IMAGE_VERSION", "22.04") }
func envJavaVersion() string  { return envOrDefault("JAVA_VERSION", "17") }

func repoRoot() string {
	_, filename, _, _ := runtime.Caller(0)
	return filepath.Dir(filepath.Dir(filename))
}
