package itest

import (
	"fmt"
	"testing"
	"time"

	"pyroscope-java-itest/dockertest"
	"pyroscope-java-itest/require"
)

// Matches demo/src/main/java/LabelsApp.java.
const (
	labelsStaticLabel      = "static_label"
	labelsStaticLabelValue = "static_value"
	labelsWorkloadLabel    = "workload"
)

var labelsWorkloads = map[string]string{
	"alpha": "fibAlpha",
	"beta":  "fibBeta",
	"gamma": "fibGamma",
}

// workloadNeedle is the collapsed stack fragment only the given workload can produce. Two frames so
// that it also asserts the recursion, and not just a single method name appearing somewhere.
func workloadNeedle(workload string) string {
	method := "LabelsApp." + labelsWorkloads[workload]
	return fmt.Sprintf(";%s;%s;", method, method)
}

// TestQueryProfileLabels covers the labels pipeline end to end: the encoded LabelsSnapshot uploaded
// as the multipart "labels" part, and the static labels folded into the ingest URL. LabelsApp runs
// three busy threads, each under a different "workload" label, so a mix-up between contexts, or
// between the label strings, shows up as a workload's stacks landing under the wrong label.
func TestQueryProfileLabels(t *testing.T) {
	net := dockertest.CreateNetwork(t)

	pyroscopeURL := startPyroscope(t, net)
	t.Logf("pyroscope URL: %s", pyroscopeURL)

	dockerfile, imageVersion, javaVersion := envDockerfile(), envImageVersion(), envJavaVersion()
	image := buildAppImage(t, dockerfile, imageVersion, javaVersion)
	serviceName := serviceNameFromDockerfile(dockerfile, imageVersion, javaVersion) + "-labels"

	startApp(t, net, image, map[string]string{
		"PYROSCOPE_APPLICATION_NAME": serviceName,
		// LabelsApp starts the agent itself, so that Pyroscope.setStaticLabels lands before the
		// exporter is built. See the comment in LabelsApp.main.
		"PYROSCOPE_AGENT_ENABLED": "false",
	},
		"java",
		"-javaagent:/app/agent/build/libs/pyroscope.jar",
		"-cp", "/app/agent/build/libs/pyroscope.jar:demo/src/main/java/",
		"LabelsApp")

	// Static and dynamic labels get separate selectors: a static label reaches the server as a
	// series label on the ingest URL, a dynamic one as a sample label in the snapshot, and the two
	// are merged at different stages of the write path. Keeping them apart keeps a failure
	// attributable to one of them.
	expectations := []profileExpectation{{
		labelSelector: fmt.Sprintf(`{service_name="%s",%s="%s"}`,
			serviceName, labelsStaticLabel, labelsStaticLabelValue),
		contains: workloadNeedle("alpha"),
	}}
	for workload := range labelsWorkloads {
		expectations = append(expectations, profileExpectation{
			labelSelector: fmt.Sprintf(`{service_name="%s",%s="%s"}`,
				serviceName, labelsWorkloadLabel, workload),
			contains: workloadNeedle(workload),
			absent:   otherWorkloadNeedles(workload),
		})
	}
	testProfiles(t, pyroscopeURL, serviceName, cpuNanosecondsProfileType, expectations)

	// The checks above would also pass if the server ignored the workload label altogether and
	// merged everything into one series, so make sure an unknown value selects nothing.
	requireNoProfile(t, pyroscopeURL, serviceName,
		fmt.Sprintf(`{service_name="%s",%s="nonexistent"}`, serviceName, labelsWorkloadLabel))
}

func otherWorkloadNeedles(workload string) []string {
	var needles []string
	for other := range labelsWorkloads {
		if other != workload {
			needles = append(needles, workloadNeedle(other))
		}
	}
	return needles
}

func requireNoProfile(t *testing.T, pyroscopeURL string, targetName string, labelSelector string) {
	t.Helper()
	require.Never(t, func() bool {
		collapsed, err := queryProfile(t, pyroscopeURL, cpuNanosecondsProfileType, labelSelector)
		return err == nil && collapsed != ""
	}, 20*time.Second, 5*time.Second,
		"[%s] selector %s should not match any profile", targetName, labelSelector)
}
