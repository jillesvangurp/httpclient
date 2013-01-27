/**
 * Copyright (c) 2012, Jilles van Gurp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import org.apache.http.client.methods.HttpUriRequest;
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

                public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                    try {
                        while(blocked.get()) {
                            Thread.sleep(10);
                        }
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    response.setStatusCode(200);
                }
            });
            this.localServer.start();
            InetSocketAddress address = localServer.getServiceAddress();
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
        HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(new HttpGet(uri), new OkidokiHandler());
        Assert.assertTrue("request should have returned OK", task.get());
    }

    @Test(expected=CancellationException.class)
    public void shouldCancel() throws InterruptedException, ExecutionException {
        HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(new HttpGet(uri), new OkidokiHandler());
        task.cancel(true);
        task.get();
    }

    @Test(expected=TimeoutException.class)
    public void shouldTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        blocked.set(true);
        HttpAsyncClientFutureTask<Boolean> task = httpAsyncClientWithFuture.execute(new HttpGet(uri), new OkidokiHandler());
        task.get(10, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shouldExecuteMultipleCalls() throws InterruptedException, ExecutionException {
        HttpGet[] requests= new HttpGet[100];
        for(int i=0;i<100;i++) {
            requests[i]=new HttpGet(uri);
        }
        List<Future<Boolean>> tasks = httpAsyncClientWithFuture.executeMultiple(new OkidokiHandler(), requests);
        for (Future<Boolean> task : tasks) {
            Assert.assertTrue("request should have returned OK", task.get());
        }
    }

    @Test
    public void shouldExecuteMultipleCallsAndCallback() throws InterruptedException, ExecutionException {
        HttpGet[] requests= new HttpGet[100];
        for(int i=0;i<100;i++) {
            requests[i]=new HttpGet(uri);
        }
        CountingCallback callback = new CountingCallback();
        httpAsyncClientWithFuture.executeMultiple(null, new OkidokiHandler(), callback , 1000, TimeUnit.MILLISECONDS, requests);
        Assert.assertEquals(100, callback.scheduled);
        Assert.assertEquals(100, callback.started);
        Assert.assertEquals(100, callback.completed);
        Assert.assertEquals(0, callback.cancelled);
        Assert.assertEquals(0, callback.failed);
    }


    private final class CountingCallback implements HttpAsyncClientCallback<Boolean> {
        int scheduled=0;
        int started=0;
        int failed=0;
        int cancelled=0;
        int completed=0;

        public void started(HttpUriRequest request) {
            started++;
        }

        public void scheduled(HttpUriRequest request) {
            scheduled++;
        }

        public void failed(HttpUriRequest request, Exception ex) {
            failed++;
        }

        public void completed(HttpUriRequest request, Boolean result) {
            completed++;
        }

        public void cancelled(HttpUriRequest request) {
            cancelled++;
        }
    }


    private final class OkidokiHandler implements ResponseHandler<Boolean> {
        public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
            return response.getStatusLine().getStatusCode() == 200;
        }
    }
}
