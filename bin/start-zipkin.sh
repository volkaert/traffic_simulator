#!/bin/bash
xdg-open http://localhost:9411
docker run \
  --rm \
  -p 9411:9411 \
  --name=zipkin \
  openzipkin/zipkin

