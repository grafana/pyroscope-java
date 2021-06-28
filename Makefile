agent/build/libs/pyroscope.jar:
	./gradlew shadowJar

.PHONY: docker-example
docker-example: agent/build/libs/pyroscope.jar
	cp agent/build/libs/pyroscope.jar example/
	docker-compose -f example/docker-compose.yml build
	docker-compose -f example/docker-compose.yml up