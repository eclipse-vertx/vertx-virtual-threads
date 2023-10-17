## Vert.x Virtual Threads

[![Build Status](https://github.com/eclipse-vertx/vertx-virtual-threads/workflows/CI/badge.svg?branch=main)](https://github.com/eclipse-vertx/vertx-virtual-threads/actions?query=workflow%3ACI)

Use virtual threads to write Vert.x code that looks like it is synchronous.

- await Vert.x futures
- more meaningful stack traces

You still write the traditional Vert.x code with events, but you have the opportunity to write synchronous code for complex
workflows and use thread locals in such workflows.

