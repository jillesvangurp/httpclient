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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;

/**
 * HttpAsyncClientWithFuture wraps calls to execute with a {@link HttpAsyncClientFutureTask} and schedules them using
 * the provided executor service. Scheduled calls may be cancelled. Similar to the nio HttpAsyncClient, a callback
 * handler api is provided.
 *
 * @param <T>
 *            the type of the value returned by the response handler you use
 */
@ThreadSafe
public class HttpAsyncClientWithFuture {
    final HttpClient httpclient;

    private final ExecutorService executorService;

    private final ConnectionMetrics metrics = new ConnectionMetrics();

    /**
     * Create a new HttpAsyncClientWithFuture.
     *
     * @param httpclient
     *            you should tune your httpclient instance to match your needs. You should align the max number of
     *            connections in the pool and the number of threads in the executor; it doesn't make sense to have more
     *            threads than connections and if you have less connections than threads, the threads will just end up
     *            blocking on getting a connection from the pool.
     * @param executorService
     *            any executorService will do here. E.g. Executors.newFixedThreadPool(numberOfThreads)
     */
    public HttpAsyncClientWithFuture(HttpClient httpclient, ExecutorService executorService) {
        this.httpclient = httpclient;
        this.executorService = executorService;
    }

    /**
     * Schedule a request for execution.
     *
     * @param <T>
     *
     * @param request
     *            request to execute
     * @param responseHandler
     *            handler that will process the response.
     * @return HttpAsyncClientFutureTask for the scheduled request.
     * @throws InterruptedException
     */
    public <T> HttpAsyncClientFutureTask<T> execute(final HttpUriRequest request, ResponseHandler<T> responseHandler) throws InterruptedException {
        return execute(request, null, responseHandler, null);
    }

    /**
     * Schedule a request for execution.
     *
     * @param <T>
     *
     * @param request
     *            request to execute
     * @param context
     *            optional context; use null if not needed.
     * @param responseHandler
     *            handler that will process the response.
     * @param callback
     *            callback handler that will be called when the request is scheduled, started, completed, failed, or
     *            cancelled.
     * @return HttpAsyncClientFutureTask for the scheduled request.
     * @throws InterruptedException
     */
    public <T> HttpAsyncClientFutureTask<T> execute(HttpUriRequest request, HttpContext context, ResponseHandler<T> responseHandler,
            FutureCallback<T> callback) throws InterruptedException {
        metrics.scheduledConnections.incrementAndGet();
        HttpAsyncClientCallable<T> callable = new HttpAsyncClientCallable<T>(httpclient, request, context, responseHandler, callback, metrics);
        HttpAsyncClientFutureTask<T> httpRequestFutureTask = new HttpAsyncClientFutureTask<T>(request, callable);
        executorService.execute(httpRequestFutureTask);

        return httpRequestFutureTask;
    }

    /**
     * Schedule multiple requests for execution.
     *
     * @param <T>
     *
     * @param responseHandler
     *            handler that will process the responses.
     * @param requests
     *            one or more requests.
     * @return a list of HttpAsyncClientFutureTask for the scheduled requests.
     * @throws InterruptedException
     */
    public <T> List<Future<T>> executeMultiple(ResponseHandler<T> responseHandler, HttpUriRequest... requests) throws InterruptedException {
        return executeMultiple(null, responseHandler, null, -1, null, requests);
    }

    /**
     * Schedule multiple requests for execution with a timeout.
     *
     * @param <T>
     *
     * @param context
     *            optional context; use null if not needed.
     * @param responseHandler
     *            handler that will process the responses.
     * @param callback
     *            callback handler that will be called when requests are scheduled, started, completed, failed, or
     *            cancelled.
     * @param timeout
     * @param timeUnit
     * @param requests
     *            one or more requests.
     * @return a list of HttpAsyncClientFutureTask for the scheduled requests.
     * @throws InterruptedException
     */
    public <T> List<Future<T>> executeMultiple(HttpContext context, ResponseHandler<T> responseHandler, FutureCallback<T> callback, long timeout,
            TimeUnit timeUnit, HttpUriRequest... requests) throws InterruptedException {
        metrics.scheduledConnections.incrementAndGet();
        List<Callable<T>> callables = new ArrayList<Callable<T>>();
        for (HttpUriRequest request : requests) {
            HttpAsyncClientCallable<T> callable = new HttpAsyncClientCallable<T>(httpclient, request, context, responseHandler, callback, metrics);
            callables.add(callable);
        }
        if (timeout > 0) {
            return executorService.invokeAll(callables, timeout, timeUnit);
        } else {
            return executorService.invokeAll(callables);
        }
    }

    /**
     * @return metrics gathered for this instance.
     * @see ConnectionMetrics.
     */
    public ConnectionMetrics metrics() {
        return metrics;
    }
}
