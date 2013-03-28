/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.client.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientWithFutureTest {

    private LocalTestServer localServer;
    private String uri;
    private HttpAsyncClientWithFuture httpAsyncClientWithFuture;
    private CloseableHttpClient httpClient;
    private ExecutorService executorService;

    private final AtomicBoolean blocked = new AtomicBoolean(false);

    @Before
    public void before() throws Exception {
            this.localServer = new LocalTestServer(null, null);
            this.localServer.register("/wait", new HttpRequestHandler() {

                public void handle(
                        final HttpRequest request, final HttpResponse response,
                        final HttpContext context) throws HttpException, IOException {
                    try {
                        while(blocked.get()) {
                            Thread.sleep(10);
                        }
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    response.setStatusCode(200);
                }
            });
            this.localServer.start();
            final InetSocketAddress address = localServer.getServiceAddress();
            uri = "http://" + address.getHostName() + ":" + address.getPort() + "/wait";

            httpClient = HttpClientBuilder.create().setMaxConnPerRoute(5).setMaxConnTotal(5).build();
            executorService = Executors.newFixedThreadPool(5);
            httpAsyncClientWithFuture = new HttpAsyncClientWithFuture(httpClient, executorService);
    }

    @After
    public void after() throws Exception {
        blocked.set(false); // any remaining requests should unblock
        this.localServer.stop();
        httpClient.close();
        executorService.shutdownNow();
    }

    @Test
    public void shouldExecuteSingleCall() throws InterruptedException, ExecutionException {
        final HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), new OkidokiHandler());
        Assert.assertTrue("request should have returned OK", task.get().booleanValue());
    }

    @Test(expected=CancellationException.class)
    public void shouldCancel() throws InterruptedException, ExecutionException {
        final HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), new OkidokiHandler());
        task.cancel(true);
        task.get();
    }

    @Test(expected=TimeoutException.class)
    public void shouldTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        blocked.set(true);
        final HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(
            new HttpGet(uri), new OkidokiHandler());
        task.get(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shouldExecuteMultipleCalls() throws InterruptedException, ExecutionException {
        final HttpGet[] requests= new HttpGet[100];
        for(int i=0;i<100;i++) {
            requests[i]=new HttpGet(uri);
        }
        final List<Future<Boolean>> tasks = httpAsyncClientWithFuture.executeMultiple(
            new OkidokiHandler(), requests);
        for (final Future<Boolean> task : tasks) {
            Assert.assertTrue("request should have returned OK", task.get().booleanValue());
        }
    }

    @Test
    public void shouldExecuteMultipleCallsAndCallback() throws InterruptedException {
        final HttpGet[] requests= new HttpGet[100];
        for(int i=0;i<100;i++) {
            requests[i]=new HttpGet(uri);
        }
        final CountingCallback callback = new CountingCallback();
        httpAsyncClientWithFuture.executeMultiple(null,
            new OkidokiHandler(), callback , 10000, TimeUnit.MILLISECONDS, requests);
        Assert.assertEquals(100, callback.completed);
        Assert.assertEquals(0, callback.cancelled);
        Assert.assertEquals(0, callback.failed);
    }


    private final class CountingCallback implements FutureCallback<Boolean> {
        int failed=0;
        int cancelled=0;
        int completed=0;

        public void failed(final Exception ex) {
            failed++;
        }

        public void completed(final Boolean result) {
            completed++;
        }

        public void cancelled() {
            cancelled++;
        }
    }


    private final class OkidokiHandler implements ResponseHandler<Boolean> {
        public Boolean handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
            return response.getStatusLine().getStatusCode() == 200;
        }
    }

}
