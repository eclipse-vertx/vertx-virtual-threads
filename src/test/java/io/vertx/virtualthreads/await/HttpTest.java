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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import io.vertx.virtualthreads.await.Async;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class HttpTest extends VertxTestBase {

  Async async;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    async = new Async(vertx);
  }

  @Ignore
  @Test
  public void testDuplicate() throws Exception {
    int num = 1000;
    CountDownLatch latch = new CountDownLatch(1);
    async.run(v -> {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(num)));
      CyclicBarrier barrier = new CyclicBarrier(num);
      server.requestHandler(req -> {
        try {
          barrier.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          fail(e);
        } catch (BrokenBarrierException e) {
          fail(e);
        }
        req.response().end("Hello World");
      });
      server.listen(8080, "localhost").onComplete(onSuccess(v2 -> {
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false)
    );
    waitFor(num);
    for (int i = 0;i < num;i++) {
      client
        .request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          complete();
      }));
    }
    await();
  }

  @Test
  public void testHttpClient1() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    async.run(v -> {
      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < 100; ++i) {
        HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
        HttpClientResponse resp = Async.await(req.send());
        Buffer body = Async.await(resp.body());
        String bodyString = body.toString(StandardCharsets.UTF_8);
        assertEquals("Hello World", body.toString());
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testHttpClient2() throws Exception {
    waitFor(100);
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    async.run(v -> {
      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < 100; ++i) {
        HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
        HttpClientResponse resp = Async.await(req.send());
        StringBuffer body = new StringBuffer();
        resp.handler(buff -> {
          body.append(buff.toString());
        });
        resp.endHandler(v2 -> {
          assertEquals("Hello World", body.toString());
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testHttpClientTimeout() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    async.run(v -> {
      HttpClient client = vertx.createHttpClient();
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
      PromiseInternal<HttpClientResponse> promise = ctx.promise();
      req.send().onComplete(promise);
      Exception failure = new Exception("Too late");
      vertx.setTimer(500, id -> promise.tryFail(failure));
      try {
        HttpClientResponse resp = Async.await(promise.future());
      } catch (Exception e) {
        assertSame(failure, e);
        testComplete();
      }
    });
    await();
  }
}
