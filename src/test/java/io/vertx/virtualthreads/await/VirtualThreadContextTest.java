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
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.WorkerExecutor;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.virtualthreads.await.Async;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class VirtualThreadContextTest extends VertxTestBase {

  Async async;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    async = new Async(vertx);
  }

  @Test
  public void testContext() {
    async.run(v -> {
      Thread thread = Thread.currentThread();
      assertTrue(thread.isVirtual());
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Executor executor = context.executor();
      assertTrue(executor instanceof WorkerExecutor);
      context.runOnContext(v2 -> {
        // assertSame(thread, Thread.currentThread());
        assertSame(context, vertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAwaitFutureSuccess() {
    Object result = new Object();
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame(result, Async.await(promise.future()));
      testComplete();
    });
    await();
  }

  @Test
  public void testAwaitFutureFailure() {
    Object result = new Object();
    Exception failure = new Exception();
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.fail(failure);
      }).start();
      try {
        Async.await(promise.future());
      } catch (Exception e) {
        assertSame(failure, e);
        testComplete();
        return;
      }
      fail();
    });
    await();
  }

  @Test
  public void testAwaitCompoundFuture() {
    Object result = new Object();
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame("HELLO", Async.await(promise.future().map(res -> "HELLO")));
      testComplete();
    });
    await();
  }

  @Test
  public void testDuplicateUseSameThread() {
    int num = 1000;
    waitFor(num);
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Thread th = Thread.currentThread();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          // assertSame(th, Thread.currentThread());
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testDuplicateConcurrentAwait() {
    int num = 1000;
    waitFor(num);
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Object lock = new Object();
      List<Promise<Void>> list = new ArrayList<>();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          Promise<Void> promise = duplicate.promise();
          boolean complete;
          synchronized (lock) {
            list.add(promise);
            complete = list.size() == num;
          }
          if (complete) {
            context.runOnContext(v3 -> {
              synchronized (lock) {
                list.forEach(p -> p.complete(null));
              }
            });
          }
          Future<Void> f = promise.future();
          Async.await(f);
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testTimer() {
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<String> promise = context.promise();
      vertx.setTimer(100, id -> {
        promise.complete("foo");
      });
      String res = Async.await(promise);
      assertEquals("foo", res);
      testComplete();
    });
    await();
  }

  @Test
  public void testInThread() {
    async.run(v1 -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      assertTrue(context.inThread());
      new Thread(() -> {
        boolean wasNotInThread = !context.inThread();
        context.runOnContext(v2 -> {
          assertTrue(wasNotInThread);
          assertTrue(context.inThread());
          testComplete();
        });
      }).start();
    });
    await();
  }

  @Test
  public void testAcquireLock() throws Exception {
    ReentrantLock lock = new ReentrantLock();
    Thread t = new Thread(() -> {
      lock.lock();
      try {
        while (!lock.hasQueuedThreads()) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      } catch (Exception e) {
        fail(e);
      } finally {
        lock.unlock();
      }
    });
    t.start();
    while (!lock.isLocked()) {
      Thread.sleep(10);
    }
    async.run(v1 -> {
      Async.lock(lock);
      assertTrue(lock.isHeldByCurrentThread());
      testComplete();
    });
    await();
  }

  private void sleep(AtomicInteger inflight) {
    assertEquals(0, inflight.getAndIncrement());
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      inflight.decrementAndGet();
    }
  }

  @Test
  public void testSerializeBlocking() throws Exception {
    AtomicInteger inflight = new AtomicInteger();
    async.run(v1 -> {
      Context ctx = vertx.getOrCreateContext();
      for (int i = 0;i < 10;i++) {
        ctx.runOnContext(v2 -> sleep(inflight));
      }
      ctx.runOnContext(v -> testComplete());
    });
    await();
  }
}
