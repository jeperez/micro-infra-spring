package com.ofg.infrastructure.web.resttemplate.fluent.common.response.executor

import com.google.common.base.Throwables
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.UncheckedTimeoutException
import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.nurkiewicz.asyncretry.RetryExecutor
import com.nurkiewicz.asyncretry.SyncRetryExecutor
import com.ofg.infrastructure.correlationid.CorrelationIdHolder
import com.ofg.infrastructure.correlationid.CorrelationIdUpdater
import com.ofg.infrastructure.hystrix.CorrelatedCommand
import groovy.transform.TypeChecked
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestOperations

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

import static com.ofg.infrastructure.web.resttemplate.fluent.common.response.executor.UrlParsingUtils.appendPathToHost

/**
 * Utility class that extracts {@link HttpEntity} from the provided map of passed parameters
 *
 * Note that since in this class we are using multiple dispatch please do not annotate this class
 * with @CompileStatic
 */
@TypeChecked
final class RestExecutor<T> {
    private final RestOperations restOperations
    private final RetryExecutor retryExecutor

    RestExecutor(RestOperations restOperations, RetryExecutor retryExecutor) {
        this.restOperations = restOperations
        this.retryExecutor = retryExecutor
    }

    ResponseEntity<T> exchange(HttpMethod httpMethod, Map params, Class<T> responseType) {
        try {
            return exchangeInternal(params, httpMethod, responseType).get()
        } catch (ExecutionException e) {
            propagate(e.cause)
        }
    }

    private void propagate(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            throw new UncheckedTimeoutException(throwable)
        }
        Throwables.propagate(throwable)
    }

    ListenableFuture<ResponseEntity<T>> exchangeAsync(HttpMethod httpMethod, Map params, Class<T> responseType) {
        throwIfAsyncWithoutExecutor()
        return exchangeInternal(params, httpMethod, responseType)
    }

    private ListenableFuture<ResponseEntity<T>> exchangeInternal(Map params, HttpMethod httpMethod, Class<T> responseType) {
        if (params.url) {
            return urlExchange(httpMethod, params, responseType)
        } else if (params.urlTemplate) {
            return urlTemplateExchange(httpMethod, params, responseType)
        }
        throw new InvalidHttpMethodParametersException(params)
    }

    private void throwIfAsyncWithoutExecutor() {
        if (retryExecutor == SyncRetryExecutor.INSTANCE)
            throw new IllegalStateException("Async execution is only enabled with retrying executor. Try .retryUsing(executor.dontRetry()) ")
    }

    private ListenableFuture<ResponseEntity<T>> urlTemplateExchange(HttpMethod httpMethod, Map params, Class<T> responseType) {
        return withRetry(params.hystrix as HystrixCommand.Setter, params.hystrixFallback as Closure<T>) {
            restOperations.exchange(
                    appendPathToHost(params.host as String, params.urlTemplate as String),
                    httpMethod,
                    getHttpEntityFrom(params),
                    responseType,
                    params.urlVariablesArray as Object[] ?: params.urlVariablesMap as Map<String, ?>)
        }
    }

    private ListenableFuture<ResponseEntity<T>> urlExchange(HttpMethod httpMethod, Map params, Class<T> responseType) {
        return withRetry(params.hystrix as HystrixCommand.Setter, params.hystrixFallback as Closure<T>) {
            return restOperations.exchange(
                    new URI(appendPathToHost(params.host as String, params.url as URI)),
                    httpMethod,
                    getHttpEntityFrom(params),
                    responseType)
        }
    }

    private ListenableFuture<ResponseEntity<T>> withRetry(HystrixCommand.Setter hystrix, Closure<T> hystrixFallback, Closure<ResponseEntity<T>> httpInvocation) {
        String correlationId = CorrelationIdHolder.get()
        return retryExecutor.getWithRetry {
            return CorrelationIdUpdater.withId(correlationId) {
                return callHttp(hystrix, hystrixFallback, httpInvocation)
            }
        }
    }

    private ResponseEntity<T> callHttp(HystrixCommand.Setter hystrix, Closure<T> hystrixFallback, Closure<ResponseEntity<T>> httpInvocation) {
        if(hystrix) {
            return runInsideHystrixCommand(hystrix, hystrixFallback, httpInvocation)
        } else {
            return httpInvocation.call()
        }
    }

    private ResponseEntity<T> runInsideHystrixCommand(HystrixCommand.Setter hystrix, Closure<T> hystrixFallback, Closure<ResponseEntity<T>> httpInvocation) {
        try {
            if (hystrixFallback) {
                return new CorrelatedCommand<ResponseEntity<T>>(hystrix) {
                    @Override
                    ResponseEntity<T> doRun() throws Exception {
                        return httpInvocation.call()
                    }

                    @Override
                    protected ResponseEntity<T> getFallback() {
                        return wrapWithResponseEntity(hystrixFallback.call())
                    }
                }.execute()
            }
            return new CorrelatedCommand<ResponseEntity<T>>(hystrix) {
                @Override
                ResponseEntity<T> doRun() throws Exception {
                    return httpInvocation.call()
                }
            }.execute()
        } catch(HystrixRuntimeException e) {
            throw e.getCause()
        }
    }

    private ResponseEntity<T> wrapWithResponseEntity(ResponseEntity<T> responseEntity) {
        return responseEntity
    }

    private ResponseEntity<T> wrapWithResponseEntity(T responseBody) {
        return ResponseEntity.ok(responseBody)
    }

    static HttpEntity<Object> getHttpEntityFrom(Map params) {
        if (params.httpEntity) {
            return params.httpEntity as HttpEntity
        }
        HttpHeaders headers = params.headers as HttpHeaders
        HttpEntity<?> httpEntity = new HttpEntity<Object>(params.request, headers)
        return httpEntity
    }
}
