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

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.test.core.VertxTestBase;
import io.vertx.virtualthreads.await.Async;
import org.junit.Before;
import org.junit.Test;

import static io.vertx.virtualthreads.await.Async.await;

public class EventBusTest extends VertxTestBase {

  Async async;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    async = new Async(vertx);
  }

  @Test
  public void testEventBus() throws Exception {
    EventBus eb = vertx.eventBus();
    eb.consumer("test-addr", msg -> {
      msg.reply(msg.body());
    });
    async.run(v -> {
      Message<String> ret = Async.await(eb.request("test-addr", "test"));
      assertEquals("test", ret.body());
      testComplete();
    });
    await();
  }
}
