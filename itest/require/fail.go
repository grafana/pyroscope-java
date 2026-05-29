package require

import (
	"fmt"
	"testing"
)

func Fail(t *testing.T, failureMessage string, msgAndArgs ...interface{}) bool {
	t.Helper()
	t.Fatalf("\n%s\n%s", failureMessage, messageFromMsgAndArgs(msgAndArgs...))
	return false
}

func messageFromMsgAndArgs(msgAndArgs ...interface{}) string {
	if len(msgAndArgs) == 0 || msgAndArgs == nil {
		return ""
	}
	if len(msgAndArgs) == 1 {
		msg := msgAndArgs[0]
		if msgAsStr, ok := msg.(string); ok {
			return msgAsStr
		}
		return fmt.Sprintf("%+v", msg)
	}
	if len(msgAndArgs) > 1 {
		return fmt.Sprintf(msgAndArgs[0].(string), msgAndArgs[1:]...)
	}
	return ""
}
