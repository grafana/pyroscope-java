package require

import (
	"testing"
)

func NoError(t *testing.T, err error) {
	t.Helper()
	if err == nil {
		return
	}
	t.Fatalf("%s", err.Error())
}
