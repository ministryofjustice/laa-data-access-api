IMAGE := ghcr.io/ministryofjustice/tech-docs-github-pages-publisher:v6.0.1
MOUNTS := -v $$(pwd)/config:/tech-docs-github-pages-publisher/config \
          -v $$(pwd)/source:/tech-docs-github-pages-publisher/source

# Use this to run a local instance of the documentation site, while editing
.PHONY: preview check

preview:
	docker run --rm $(MOUNTS) -p 4567:4567 -it $(IMAGE) /usr/local/bin/preview

check:
	docker run --rm $(MOUNTS) -it $(IMAGE) /usr/local/bin/package
