.DEFAULT_GOAL := build

IMAGE ?= docker.io/agilestacks/jenkins-operator
TAG ?= $(shell git rev-parse HEAD | colrm 7)
NAMESPACE ?= jenkins

kubectl := kubectl

jenkins-operator-cache:
	docker build --target=$@ --tag $@ .
.PHONY: jenkins-operator-cache

build:
	docker build --cache-from=jenkins-operator-cache -t $(IMAGE):latest -t $(IMAGE):$(TAG) .
.PHONY: build

push:
	docker push $(IMAGE):latest
	docker push $(IMAGE):$(TAG)
.PHONY: push

push-stable:
	docker tag $(IMAGE):$(TAG) $(IMAGE):stable
	docker push $(IMAGE):stable
.PHONY: push-stable

push-experimental:
	docker tag $(IMAGE):$(TAG) $(IMAGE):experimental
	docker push $(IMAGE):experimental
.PHONY: push-experimental

deploy: build push

.PHONY: deploy
	- $(kubectl) create namespace $(NAMESPACE)
undeploy:

.PHONY: undeploy
