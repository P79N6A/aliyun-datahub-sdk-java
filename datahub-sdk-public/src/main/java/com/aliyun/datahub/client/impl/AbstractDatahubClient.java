package com.aliyun.datahub.client.impl;

import com.aliyun.datahub.client.DatahubClient;
import com.aliyun.datahub.client.auth.Account;
import com.aliyun.datahub.client.exception.*;
import com.aliyun.datahub.client.http.HttpClient;
import com.aliyun.datahub.client.http.HttpConfig;
import com.aliyun.datahub.client.http.HttpInterceptor;
import com.aliyun.datahub.client.http.HttpRequest;
import com.aliyun.datahub.client.metircs.ClientMetrics;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static com.aliyun.datahub.client.common.ErrorCode.*;

public abstract class AbstractDatahubClient implements DatahubClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatahubClient.class);

    protected static final int MAX_FETCH_SIZE = 1000;
    protected static final int MIN_FETCH_SIZE = 1;
    protected static final String MAX_SHARD_ID = String.valueOf(0xffffffffL); // "4294967295"
    protected static final int MAX_WAITING_TIME_IN_MS = 30000;

    // metrics
    protected Meter PUT_QPS_METER = ClientMetrics.getMeter(ClientMetrics.MetricType.PUT_QPS);
    protected Meter PUT_RPS_METER = ClientMetrics.getMeter(ClientMetrics.MetricType.PUT_RPS);
    protected Timer PUT_LATENCY_TIMER = ClientMetrics.getTimer(ClientMetrics.MetricType.PUT_LATENCY);

    protected Meter GET_QPS_METER = ClientMetrics.getMeter(ClientMetrics.MetricType.GET_QPS);
    protected Meter GET_RPS_METER = ClientMetrics.getMeter(ClientMetrics.MetricType.GET_RPS);
    protected Timer GET_LATENCY_TIMER = ClientMetrics.getTimer(ClientMetrics.MetricType.GET_LATENCY);

    private String endpoint;
    private HttpConfig httpConfig;
    private HttpInterceptor interceptor;

    public AbstractDatahubClient(String endpoint, Account account, HttpConfig httpConfig) {
        this.endpoint = endpoint;
        this.httpConfig = httpConfig;
        this.interceptor = new DatahubInterceptorHandler(account);
    }

    @Override
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void setUserAgent(String userAgent) {
        this.interceptor.setUserAgent(userAgent);
    }

    public final HttpRequest createRequest() {
        return HttpClient.createRequest(endpoint, httpConfig, interceptor);
    }

    final protected <T> T callWrapper(Callable<T> callable) {
        try {
            return callable.call();
        } catch (DatahubClientException ex) {
            LOGGER.warn("Request fail. error: {}", ex.toString());
            checkAndThrow(ex);
        } catch (Exception ex) {
            LOGGER.warn("Request fail. error: {}", ex.getMessage());
            throw new DatahubClientException(ex.getMessage() == null ?
                    getExceptionStack(ex) : ex.getMessage());
        }

        // should never go here
        return null;
    }

    private void checkAndThrow(DatahubClientException ex) {
        String errCode = ex.getErrorCode();
        if (INVALID_PARAMETER.equalsIgnoreCase(errCode) ||
                INVALID_CURSOR.equalsIgnoreCase(errCode)) {
            throw new InvalidParameterException(ex);
        } else if (RESOURCE_NOT_FOUND.equalsIgnoreCase(errCode) ||
                NO_SUCH_PROJECT.equalsIgnoreCase(errCode) ||
                NO_SUCH_TOPIC.equalsIgnoreCase(errCode) ||
                NO_SUCH_CONNECTOR.equalsIgnoreCase(errCode) ||
                NO_SUCH_SHARD.equalsIgnoreCase(errCode) ||
                NO_SUCH_SUBSCRIPTION.equalsIgnoreCase(errCode) ||
                NO_SUCH_CONSUMER.equalsIgnoreCase(errCode) ||
                NO_SUCH_METER_INFO.equalsIgnoreCase(errCode)) {
            throw new ResourceNotFoundException(ex);
        } else if (RESOURCE_ALREADY_EXIST.equalsIgnoreCase(errCode) ||
                PROJECT_ALREADY_EXIST.equalsIgnoreCase(errCode) ||
                TOPIC_ALREADY_EXIST.equalsIgnoreCase(errCode) ||
                CONNECTOR_ALREADY_EXIST.equalsIgnoreCase(errCode)) {
            throw new ResourceAlreadyExistException(ex);
        } else if (UN_AUTHORIZED.equalsIgnoreCase(errCode)) {
            throw new AuthorizationFailureException(ex);
        } else if (NO_PERMISSION.equalsIgnoreCase(errCode) ||
                OPERATOR_DENIED.equalsIgnoreCase(errCode)) {
            throw new NoPermissionException(ex);
        } else if (INVALID_SHARD_OPERATION.equalsIgnoreCase(errCode)) {
            throw new ShardSealedException(ex);
        } else if (LIMIT_EXCEED.equalsIgnoreCase(errCode)) {
            throw new LimitExceededException(ex);
        } else if (SUBSCRIPTION_OFFLINE.equalsIgnoreCase(errCode)) {
            throw new SubscriptionOfflineException(ex);
        } else if (OFFSET_SESSION_CHANGED.equalsIgnoreCase(errCode) ||
                OFFSET_SESSION_CLOSED.equalsIgnoreCase(errCode)) {
            throw new SubscriptionSessionInvalidException(ex);
        } else if (OFFSET_RESETED.equalsIgnoreCase(errCode)) {
            throw new SubscriptionOffsetResetException(ex);
        } else if (MALFORMED_RECORD.equalsIgnoreCase(errCode)) {
            throw new MalformedRecordException(ex);
        } else if (CONSUMER_GROUP_IN_PROCESS.equalsIgnoreCase(errCode)) {
            throw new ServiceInProcessException(ex);
        } else {
            throw new DatahubClientException(ex);
        }
    }

    private String getExceptionStack(Exception ex) {
        if (ex.getMessage() != null) {
            return ex.getMessage();
        }

        StackTraceElement element = ex.getStackTrace()[0];
        StringBuilder sb = new StringBuilder()
                .append("Exception:").append(ex.getClass().getName()).append("|")
                .append("ClassName:").append(element.getClassName()).append("|")
                .append("File:").append(element.getFileName()).append("|")
                .append("Line:").append(element.getLineNumber()).append("|")
                .append("Method:").append(element.getMethodName());
        return sb.toString();
    }

    public HttpInterceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(HttpInterceptor interceptor) {
        this.interceptor = interceptor;
    }
}
