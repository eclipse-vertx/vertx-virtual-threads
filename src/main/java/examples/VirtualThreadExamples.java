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
    Async async = new Async(vertx);
    async.run(v -> {
      // Run on a Vert.x a virtual thread
      HttpClient client = vertx.createHttpClient();
      HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8080, "localhost", "/"));
      HttpClientResponse resp = Async.await(req.send());
      int status = resp.statusCode();
      Buffer body = Async.await(resp.body());
    });
  }

  public void whatYouGet1(HttpClientRequest request) {
    request
      .send()
      .onSuccess(response -> {
      // Set the buffer for handlers
      response.handler(buffers -> {

      });
    });
  }

  public void whatYouGet2(HttpClientRequest request)  {
    Thread.startVirtualThread(() -> {
      CompletableFuture<HttpClientResponse> fut = new CompletableFuture<>();
      request.send().onComplete(ar -> {
        if (ar.succeeded()) {
          fut.complete(ar.result());
        } else {
          fut.completeExceptionally(ar.cause());
        }
      });;
      try {
        HttpClientResponse response = fut.get();
        // As we get the response the virtual thread, there is a window of time where the event-loop thread has already sent buffers and we lost these events
        response.handler(buffer -> {

        });
      } catch (Exception e) {
        // Ooops
      }
    });
  }

  public void whatYouGet3(HttpClientRequest request) {
    Future<HttpClientResponse> fut = request.send();
    HttpClientResponse response = Async.await(fut);
    // Buffer events might be in the queue and if they are, they will be dispatched next
    response.handler(buffer -> {

    });
  }

  public void whatYouGet4(Vertx vertx, HttpClientRequest request) {
    Promise<HttpClientResponse> promise = Promise.promise();
    vertx.setTimer(100, id -> promise.tryFail("Too late"));
    request.send().onComplete(promise);
    try {
      HttpClientResponse response = Async.await(promise.future());
    } catch (Exception timeout) {
      // Too late
    }
  }

  private Future<Buffer> callRemoteService() {
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

  public void threadLocalSupport1(Lock theLock, String userId, HttpClient client) {
    ThreadLocal<String> local = new ThreadLocal();
    local.set(userId);
    HttpClientRequest req = Async.await(client.request(HttpMethod.GET, 8080, "localhost", "/"));
    HttpClientResponse resp = Async.await(req.send());
    // Thread local remains the same since it's the same virtual thread
  }
}
