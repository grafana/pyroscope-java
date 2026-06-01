package require

import (
	"testing"
)

func True(t *testing.T, value bool, msgAndArgs ...interface{}) {
	t.Helper()
	if value {
		return
	}
	Fail(t, "Expected true", msgAndArgs...)
}

func False(t *testing.T, value bool, msgAndArgs ...interface{}) {
	t.Helper()
	if !value {
		return
	}
	Fail(t, "Expected false", msgAndArgs...)
}
