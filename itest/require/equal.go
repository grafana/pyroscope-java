package require

import (
	"testing"
)

func Equal[T comparable](t *testing.T, expected T, actual T) {
	t.Helper()

	if expected == actual {
		return
	}
	t.Fatalf("expected %+v\nactual %+v", expected, actual)
}
