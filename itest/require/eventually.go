package require

import (
	"testing"
	"time"
)

func Eventually(t *testing.T, condition func() bool, waitFor time.Duration, tick time.Duration, msgAndArgs ...interface{}) bool {
	t.Helper()

	ch := make(chan bool, 1)
	checkCond := func() { ch <- condition() }

	timer := time.NewTimer(waitFor)
	defer timer.Stop()

	ticker := time.NewTicker(tick)
	defer ticker.Stop()

	var tickC <-chan time.Time

	// Check the condition once first on the initial call.
	go checkCond()

	for {
		select {
		case <-timer.C:
			return Fail(t, "Condition never satisfied", msgAndArgs...)
		case <-tickC:
			tickC = nil
			go checkCond()
		case v := <-ch:
			if v {
				return true
			}
			tickC = ticker.C
		}
	}
}
