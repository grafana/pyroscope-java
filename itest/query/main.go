package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strings"
	"sync"
	"time"

	"connectrpc.com/connect"
	profilev1 "github.com/grafana/pyroscope/api/gen/proto/go/google/v1"
	querierv1 "github.com/grafana/pyroscope/api/gen/proto/go/querier/v1"

	"github.com/grafana/pyroscope/api/gen/proto/go/querier/v1/querierv1connect"
)

func main() {
	type result struct {
		target string
		err    error
	}
    targets := os.Args[1:]
    if len(targets) == 0 {
        targets = []string{
            "alpine-3.16-8",
            "alpine-3.16-11",
            "alpine-3.16-17",
            "alpine-3.17-8",
            "alpine-3.17-11",
            "alpine-3.17-17",
            "alpine-3.18-8",
            "alpine-3.18-11",
            "alpine-3.18-18",
            "alpine-3.19-8",
            "alpine-3.19-11",
            "alpine-3.19-17",
            "ubuntu-18.04-8",
            "ubuntu-18.04-11",
            "ubuntu-18.04-17",
            "ubuntu-20.04-8",
            "ubuntu-20.04-11",
            "ubuntu-20.04-17",
            "ubuntu-20.04-21",
            "ubuntu-22.04-8",
            "ubuntu-22.04-11",
            "ubuntu-22.04-17",
            "ubuntu-22.04-21",
        }
    }
	url := "http://localhost:4040"
	qc := querierv1connect.NewQuerierServiceClient(
		http.DefaultClient,
		url,
	)
	wg := sync.WaitGroup{}
	ctx, _ := context.WithDeadline(context.Background(), time.Now().Add(time.Minute))
	results := make(chan result, len(targets))
	for _, target := range targets {
		wg.Add(1)
		go func(target string) {
			defer wg.Done()
			err := testTarget(ctx, qc, target)
			results <- result{target: target, err: err}
		}(target)
	}
	wg.Wait()
	close(results)

	failed := false
	for r := range results {
		if r.err != nil {
			fmt.Printf("[%s] %s\n", r.target, r.err.Error())
			failed = true
		} else {
			fmt.Printf("[%s] OK\n", r.target)
		}
	}
	if failed {
		os.Exit(1)
	}
}

func testTarget(ctx context.Context, qc querierv1connect.QuerierServiceClient, target string) error {
	needle := "Fib$$Lambda$_.run;Fib.lambda$appLogic$0;Fib.fib;Fib.fib;Fib.fib;Fib.fib;"
	ticker := time.NewTicker(time.Second * 5)
	n := 0
	for {
		select {
		case <-ctx.Done():
			return fmt.Errorf("timed out waiting for target %s. tried %d times", target, n)
		case <-ticker.C:
			n += 1
			to := time.Now()
			from := to.Add(-time.Minute * 1)

			resp, err := qc.SelectMergeProfile(context.Background(), connect.NewRequest(&querierv1.SelectMergeProfileRequest{
				ProfileTypeID: "process_cpu:cpu:nanoseconds:cpu:nanoseconds",
				Start:         from.UnixMilli(),
				End:           to.UnixMilli(),
				LabelSelector: fmt.Sprintf("{service_name=\"%s\"}", target),
			}))
			if err != nil {
				fmt.Printf("[%s] %d %s\n", target, n, err.Error())
				continue
			}

			ss := stackCollapseProto(resp.Msg, false)
			if !strings.Contains(ss, needle) {
				fmt.Printf("[%s] %d not found yet\n%s\n", target, n, ss)
				continue
			}
			fmt.Printf("[%s] %d OK\n", target, n)
			return nil
		}
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
