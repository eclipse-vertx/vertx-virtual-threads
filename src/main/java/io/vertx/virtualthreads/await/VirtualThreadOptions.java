/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.virtualthreads.await;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Options to use when deploying a virtual threads powered worker verticle.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VirtualThreadOptions implements WorkerOptions {

  @Override
  public ExecutorService createExecutor(Vertx vertx) {
    return Executors.newThreadPerTaskExecutor(Async.DEFAULT_THREAD_FACTORY);
  }

  @Override
  public VirtualThreadOptions copy() {
    return this;
  }
}
