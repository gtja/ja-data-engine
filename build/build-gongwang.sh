#!/bin/bash
set -e

CURRENT_DATE=$(date +'%y%m%d')
VERSION_PREFIX=${VERSION_PREFIX:-1.0.0.}
IMAGE_NAME=${IMAGE_NAME:-ja-data-engine}
REGISTRY_ALIYUN=${REGISTRY_ALIYUN:-registry.cn-hangzhou.aliyuncs.com/jingansi}
FULL_VERSION="${VERSION_PREFIX}${CURRENT_DATE}"

docker buildx build \
  --platform=linux/amd64 \
  -t ${REGISTRY_ALIYUN}/${IMAGE_NAME}:v${FULL_VERSION} \
  -f Dockerfile \
  --load \
  ../

