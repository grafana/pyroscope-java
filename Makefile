GRAFANA_PYROSCOPE_VERSION := 4.4.0.0
GENUINE_ASYNC_PROFILER_VERSION := 4.4

.PHONY: download-async-profiler
download-async-profiler:
	./scripts/download-async-profiler.sh -v $(GRAFANA_PYROSCOPE_VERSION) -r https://github.com/grafana/async-profiler -d async-profiler-grafana-fork-dist
	./scripts/download-async-profiler.sh -v $(GENUINE_ASYNC_PROFILER_VERSION) -r https://github.com/async-profiler/async-profiler -d async-profiler-genuine-dist
	rm -f async-profiler-genuine-dist/lib/async-profiler.jar

.PHONY: clean
clean:
	rm -rf agent/build

.PHONY: build
build:
	./gradlew shadowJar

.PHONY: publish
publish:
	@echo "./gradlew clean :agent:shadowJar publishToSonatype closeAndReleaseSonatypeStagingRepository"
	@./gradlew clean :agent:shadowJar publishToSonatype closeAndReleaseSonatypeStagingRepository \
		-PsonatypeUsername="$(NEXUS_USERNAME)" \
		-PsonatypePassword="$(NEXUS_PASSWORD)" \
		-Psigning.secretKeyRingFile="$(NEXUS_GPG_SECRING_FILE)" \
		-Psigning.password="$(NEXUS_GPG_PASSWORD)" \
		-Psigning.keyId="$(NEXUS_GPG_KEY_ID)"
	@echo "https://central.sonatype.org/publish/release/#locate-and-examine-your-staging-repository"

.PHONY: test
test:
	./gradlew test

.PHONY: docker-example-base
docker-example-base: build
	cp agent/build/libs/pyroscope.jar examples
	docker compose -f examples/docker-compose-base.yml build
	docker compose -f examples/docker-compose-base.yml up

.PHONY: docker-example-expt
docker-example-expt: build
	cp agent/build/libs/pyroscope.jar examples
	docker compose -f examples/docker-compose-expt.yml build
	docker compose -f examples/docker-compose-expt.yml up

.PHONY: ci-matrix
ci-matrix:
	@cd itest && go test -list . -json . | jq -sc '[.[] | select(.Action=="output") | .Output | rtrimstr("\n") | select(startswith("Test"))]'

.PHONY: itest
itest:
	cd itest && go test -v -timeout 15m -count=1 ./...

.PHONY: itest/%
itest/%:
	cd itest && go test -v -timeout 15m -count=1 -run '^$*$$' ./...
