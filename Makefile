.PHONY: clean
clean:
	rm -rf agent/build

.PHONY: build
build:
	./gradlew shadowJar

.PHONY: test
test:
	./gradlew test

.PHONY: docker-example
docker-example: build
	cp agent/build/libs/pyroscope.jar example/
	docker-compose -f example/docker-compose.yml build
	docker-compose -f example/docker-compose.yml up
