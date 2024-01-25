#!/usr/bin/zsh
set -ex

DOCKERFILE=$1
IMAGE_VERSION=$2
PORT=$3

function usage() {
  echo "Usage: ./itest.sh <dockerfile> <image_version>"
  exit 1
}
if [ -z "${DOCKERFILE}" ]; then
  usage
fi

if [ -z "${IMAGE_VERSION}" ]; then
  usage
fi

TEST_NAME="pyroscope-java-dockertest-$(basename ${DOCKERFILE} | cut -f 1 -d .)-${IMAGE_VERSION}"
SERVER_CONTAINER_NAME="${TEST_NAME}-server"
TEST_CONTAINER_NAME="${TEST_NAME}-test"

docker kill ${SERVER_CONTAINER_NAME} || true
docker kill ${TEST_CONTAINER_NAME} || true


docker pull grafana/pyroscope:latest
docker run --rm -tid -p${PORT}:4040 --name=${SERVER_CONTAINER_NAME} grafana/pyroscope:latest

docker build -t ${TEST_CONTAINER_NAME} --build-arg="IMAGE_VERSION=${IMAGE_VERSION}" -f ${DOCKERFILE} .
docker run --rm --name=${TEST_CONTAINER_NAME} ${TEST_CONTAINER_NAME}

