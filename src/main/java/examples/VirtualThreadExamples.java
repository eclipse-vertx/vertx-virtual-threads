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
package examples;
import io.vertx.virtualthreads.await.Async;
import io.vertx.virtualthreads.await.VirtualThreadOptions;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.docgen.Source;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;

@Source
public class VirtualThreadExamples {

  public void gettingStarted(Vertx vertx) {

    AbstractVerticle verticle = new AbstractVerticle() {
      @Override
      public void start() {
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest req = Async.await(client.request(
          HttpMethod.GET,
          8080,
          "localhost",
          "/"));
        HttpClientResponse resp = Async.await(req.send());
        int status = resp.statusCode();
        Buffer body = Async.await(resp.body());
      }
    };

    // Run the verticle a on virtual thread
    vertx.deployVerticle(verticle, new DeploymentOptions()
      .setWorker(true)
      .setInstances(1)
      .setWorkerOptions(new VirtualThreadOptions()));
  }

  private int counter;

  public void fieldVisibility1() {
    int value = counter;
    value += Async.await(getRemoteValue());
    // the counter value might have changed
    counter = value;
  }

  public void fieldVisibility2() {
    counter += Async.await(getRemoteValue());
  }

  private Future<Buffer> callRemoteService() {
    return null;
  }

  private Future<Integer> getRemoteValue() {
    return null;
  }

  public void deployVerticle(Vertx vertx, int port) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpServer server;
      @Override
      public void start() {
        server = vertx
          .createHttpServer()
          .requestHandler(req -> {
            Buffer res;
            try {
              res = Async.await(callRemoteService());
            } catch (Exception e) {
              req.response().setStatusCode(500).end();
              return;
            }
            req.response().end(res);
          });
        Async.await(server.listen(port));
      }
    }, new DeploymentOptions()
      .setWorker(true)
      .setWorkerOptions(new VirtualThreadOptions()));
  }

  public void awaitingFutures1(HttpClientResponse response) {
    Buffer body = Async.await(response.body());
  }

  public void awaitingFutures2(HttpClientResponse response) {
    Buffer body = Async.await(response.body().toCompletionStage());
  }

  public void awaitingLocks1(Lock theLock) {
    Async.lock(theLock);
    try {
      //
    } finally {
      theLock.unlock();
    }
  }

  public void threadLocalSupport1(String userId, HttpClient client) {
    ThreadLocal<String> local = new ThreadLocal();
    local.set(userId);
    HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8080, "localhost", "/"));
    HttpClientResponse resp = Async.await(req.send());
    // Thread local remains the same since it's the same virtual thread
  }
}
