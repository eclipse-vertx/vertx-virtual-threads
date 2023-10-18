## Vert.x Virtual Threads

[![Build Status (5.x)](https://github.com/eclipse-vertx/vertx-virtual-threads/actions/workflows/ci-5.x.yml/badge.svg)](https://github.com/eclipse-vertx/vertx-virtual-threads/actions/workflows/ci-5.x.yml)
[![Build Status (4.x)](https://github.com/eclipse-vertx/vertx-virtual-threads/actions/workflows/ci-4.x.yml/badge.svg)](https://github.com/eclipse-vertx/vertx-virtual-threads/actions/workflows/ci-4.x.yml)

Use virtual threads to write Vert.x code that looks like it is synchronous.

- await Vert.x futures
- more meaningful stack traces

You still write the traditional Vert.x code with events, but you have the opportunity to write synchronous code for complex
workflows and use thread locals in such workflows.

