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
	@echo "Now you go https://s01.oss.sonatype.org, close the temporarly created staging repository and release it"
	@echo "https://central.sonatype.org/publish/release/#locate-and-examine-your-staging-repository"

.PHONY: test
test:
	./gradlew test

.PHONY: docker-example
docker-example: build
	cp agent/build/libs/pyroscope.jar examples/base
	docker-compose -f examples/base/docker-compose.yml build
	docker-compose -f examples/base/docker-compose.yml up

.PHONY: docker-example
docker-example-expt: build
	cp agent/build/libs/pyroscope.jar examples/expt
	docker-compose -f examples/expt/docker-compose.yml build
	docker-compose -f examples/expt/docker-compose.yml up
