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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.*;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

public class Async {

  public static final ThreadFactory DEFAULT_THREAD_FACTORY = Thread.ofVirtual().name("vert.x-virtual-thread-", 0).factory();

  private static WorkerExecutor unwrapWorkerExecutor() {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      ctx = ctx.unwrap();
      Executor executor = ctx.executor();
      if (executor instanceof WorkerExecutor) {
        return (WorkerExecutor) executor;
      }
    }
    throw new IllegalStateException("Not running on a Vert.x virtual thread");
  }

  private final VertxInternal vertx;

  public Async(Vertx vertx) {
    this.vertx = (VertxInternal) vertx;
  }

  /**
   * Run a task on a virtual thread
   */
  public void run(Handler<Void> task) {
    ContextInternal context = create();
    context.runOnContext(task);
  }

  private ContextInternal create() {
    VertxImpl _vertx = (VertxImpl) vertx;
    ContextInternal current = vertx.getOrCreateContext();
    if (current.isEventLoopContext()) {
      WorkerPool workerPool = new WorkerPool(Executors.newThreadPerTaskExecutor(Async.DEFAULT_THREAD_FACTORY), null);
      return _vertx.createWorkerContext(current.nettyEventLoop(), workerPool, current.classLoader());
    } else {
      throw new IllegalStateException("Must be called from an event-loop");
    }
  }

  public static <T> T await(Future<T> future) {
    return await(future.toCompletionStage().toCompletableFuture());
  }

  public static void lock(Lock lock) {
    lock(unwrapWorkerExecutor(), lock);
  }

  private static void lock(WorkerExecutor executor, Lock lock) {
    Consumer<Runnable> cont = executor.unschedule();
    CompletableFuture<Void> latch = new CompletableFuture<>();
    try {
      lock.lock();
      cont.accept(() -> latch.complete(null));
    } catch(RuntimeException e) {
      cont.accept(() -> latch.completeExceptionally(e));
    }
    try {
      latch.get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
    }
  }

  public static <T> T await(CompletionStage<T> future) {
    return await(unwrapWorkerExecutor(), future);
  }

  private static <T> T await(WorkerExecutor executor, CompletionStage<T> fut) {
    Consumer<Runnable> cont = executor.unschedule();
    CompletableFuture<T> latch = new CompletableFuture<>();
    fut.whenComplete((v, err) -> {
      cont.accept(() -> {
        doComplete(v, err, latch);
      });
    });
    try {
      return latch.get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
      return null;
    }
  }

  private static <T> void doComplete(T val, Throwable err, CompletableFuture<T> fut) {
    if (err == null) {
      fut.complete(val);
    } else {
      fut.completeExceptionally(err);
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
