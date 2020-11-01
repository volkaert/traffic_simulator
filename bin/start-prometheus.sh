#!/bin/bash
xdg-open http://localhost:9090
docker run \
  --rm \
  -p 9090:9090 \
  -v `pwd`/prometheus.yml:/etc/prometheus/prometheus.yml \
  --name=prometheus \
  prom/prometheus
