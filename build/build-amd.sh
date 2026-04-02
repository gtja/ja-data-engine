#!/bin/bash
set -e

CURRENT_DATE=$(date +'%y%m%d')
VERSION_PREFIX=${VERSION_PREFIX:-1.0.0.}
IMAGE_NAME=${IMAGE_NAME:-ja-uav-data-engine}
REGISTRY_JINGAN=${REGISTRY_JINGAN:-registry.jingan.com:32008/ja}
FULL_VERSION="${VERSION_PREFIX}${CURRENT_DATE}"

docker buildx build \
  --platform=linux/amd64 \
  -t ${REGISTRY_JINGAN}/${IMAGE_NAME}:v${FULL_VERSION} \
  -f Dockerfile \
  ../

docker push ${REGISTRY_JINGAN}/${IMAGE_NAME}:v${FULL_VERSION}
