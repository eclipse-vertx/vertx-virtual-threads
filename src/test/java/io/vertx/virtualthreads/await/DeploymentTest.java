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

import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.test.core.VertxTestBase;
import io.vertx.virtualthreads.await.Async;
import io.vertx.virtualthreads.await.VirtualThreadOptions;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DeploymentTest extends VertxTestBase {

  @Test
  public void testDeploy() {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        assertTrue(Thread.currentThread().isVirtual());
        Future<Void> fut = Future.future(p -> vertx.setTimer(500, id -> p.complete()));
        Async.await(fut);
        testComplete();
      }
    }, new DeploymentOptions()
      .setWorker(true)
      .setWorkerOptions(new VirtualThreadOptions()));
    await();
  }

  @Test
  public void testExecuteBlocking() {
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        Future<String> fut = vertx.executeBlocking(() -> {
          assertTrue(Thread.currentThread().isVirtual());
          return Thread.currentThread().getName();
        });
        String res = Async.await(fut);
        assertNotSame(Thread.currentThread().getName(), res);
        testComplete();
      }
    }, new DeploymentOptions()
      .setWorker(true)
      .setWorkerOptions(new VirtualThreadOptions()));
    await();
  }

  @Test
  public void testDeployHTTPServer() throws Exception {
    AtomicInteger inflight = new AtomicInteger();
    AtomicBoolean processing = new AtomicBoolean();
    AtomicInteger max = new AtomicInteger();
    awaitFuture(vertx.deployVerticle(new AbstractVerticle() {
      HttpServer server;
      @Override
      public void start() {
        server = vertx.createHttpServer().requestHandler(req -> {
          assertFalse(processing.getAndSet(true));
          int val = inflight.incrementAndGet();
          max.set(Math.max(val, max.get()));
          Future<Void> fut = Future.future(p -> vertx.setTimer(50, id -> p.complete()));
          processing.set(false);
          Async.await(fut);
          assertFalse(processing.getAndSet(true));
          req.response().end();
          inflight.decrementAndGet();
          processing.set(false);
        });
        Async.await(server.listen(8080, "localhost"));
      }
    }, new DeploymentOptions()
      .setWorker(true)
      .setWorkerOptions(new VirtualThreadOptions())));
    HttpClient client = vertx.createHttpClient();
    int numReq = 10;
    waitFor(numReq);
    for (int i = 0;i < numReq;i++) {
      Future<Buffer> resp = client.request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req.send()
          .compose(HttpClientResponse::body));
      resp.onComplete(onSuccess(v -> complete()));
    }
    await();
    Assert.assertEquals(5, max.get());
  }
}
