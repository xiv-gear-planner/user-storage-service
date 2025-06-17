#!/usr/bin/env bash

docker run \
  --name=kvlite \
  --hostname=kvlite \
  --env KV_PROXY_PORT=8080 \
  -p 9050:8080 \
  --rm \
  --name nosql \
  -d \
  ghcr.io/oracle/nosql:latest-ce


trap "docker stop nosql" SIGINT

docker wait nosql