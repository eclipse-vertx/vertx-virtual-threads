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

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.*;
import io.vertx.core.impl.future.PromiseInternal;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Provide async/await programming model for Vert.x
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
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
   * Run a task on a virtual thread, this must be called from an event-loop thread.
   */
  public void run(Handler<Void> task) {
    ContextInternal current = vertx.getOrCreateContext();
    ContextInternal context = create(current);
    context.runOnContext(task);
  }

  /**
   * Call a task on a virtual thread, this must be called from an event-loop thread.
   */
  public <T> Future<T> call(Callable<T> task) {
    ContextInternal current = vertx.getOrCreateContext();
    PromiseInternal<T> promise = current.promise();
    ContextInternal context = create(current);
    context.runOnContext(v -> {
      T res;
      try {
        res = task.call();
      } catch (Exception e) {
        promise.fail(e);
        return;
      }
      promise.complete(res);
    });
    return promise.future();
  }

  /**
   * @return a Vert.x context running tasks on a virtual thread
   */
  public Context context() {
    return create(vertx.getOrCreateContext());
  }

  private ContextInternal create(ContextInternal current) {
    if (current.isEventLoopContext()) {
      WorkerPool workerPool = new WorkerPool(Executors.newThreadPerTaskExecutor(Async.DEFAULT_THREAD_FACTORY), null);
      return vertx.createWorkerContext(current.nettyEventLoop(), workerPool, current.classLoader());
    } else {
      throw new IllegalStateException("Must be called from an event-loop");
    }
  }

  /**
   * Park the current thread until the {@code future} is completed, when the future
   * is completed the thread is un-parked and
   *
   * <ul>
   *   <li>the result value is returned when the future was completed with a result</li>
   *   <li>otherwise, the failure is thrown</li>
   * </ul>
   *
   * @param future the future to await
   * @return the result
   */
  public static <T> T await(Future<T> future) {
    WorkerExecutor executor = unwrapWorkerExecutor();
    WorkerExecutor.TaskController cont = executor.current();
    future.onComplete(ar -> cont.resume());
    try {
      cont.suspendAndAwaitResume();
    } catch (InterruptedException e) {
      throwAsUnchecked(e.getCause());
      return null;
    }
    if (future.succeeded()) {
      return future.result();
    } else {
      throwAsUnchecked(future.cause());
      return null;
    }
  }

  /**
   * Acquire the {@code lock}.
   */
  public static void lock(Lock lock) {
    lock(unwrapWorkerExecutor(), lock);
  }

  private static void lock(WorkerExecutor executor, Lock lock) {
    WorkerExecutor.TaskController cont = executor.current();
    CountDownLatch latch = cont.suspend();
    Exception err = null;
    try {
      lock.lock();
    } catch(RuntimeException e) {
      err = e;
    }
    cont.resume();
    try {
      latch.await();
    } catch (InterruptedException e) {
      throwAsUnchecked(err);
    }
    if (err != null) {
      throwAsUnchecked(err);
    }
  }

  /**
   * Like {@link #await(Future)} but with a {@link CompletionStage}.
   */
  public static <T> T await(CompletionStage<T> fut) {
    WorkerExecutor executor = unwrapWorkerExecutor();
    WorkerExecutor.TaskController cont = executor.current();
    fut.whenComplete((v, err) -> {
      cont.resume();
    });
    try {
      cont.suspendAndAwaitResume();
      return fut.toCompletableFuture().get();
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
      return null;
    } catch (InterruptedException e) {
      throwAsUnchecked(e);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
