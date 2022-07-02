package io.pyroscope.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.pyroscope.labels.ScopedContext;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.pyroscope.otel.SpanBuilderWrapper.LABEL_PROFILE_ID;

class SpanWrapper implements Span {

    private static final String PROFILE_ID_SPAN_ATTRIBUTE_KEY = ("pyroscope.profile.id");
    private static final String PROFILE_URL_SPAN_ATTRIBUTE_KEY = ("pyroscope.profile.url");
    private static final String PROFILE_BASELINE_URL_SPAN_ATTRIBUTE_KEY = ("pyroscope.profile.baseline.url");
    private static final String PROFILE_DIFF_URL_SPAN_ATTRIBUTE_KEY = ("pyroscope.profile.diff.url");

    final Span span;
    final PyroscopeTelemetry.Config config;
    final ScopedContext labels;
    final String profileId;
    final long startTimeSeconds;

    public SpanWrapper(Span span, PyroscopeTelemetry.Config config, ScopedContext labels, String profileId) {
        this.span = span;
        this.config = config;
        this.labels = labels;
        this.profileId = profileId;
        startTimeSeconds = now();
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, T value) {
        span.setAttribute(key, value);
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
        span.addEvent(name, attributes);
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
        span.addEvent(name, attributes, timestamp, unit);
        return this;
    }

    @Override
    public Span setStatus(StatusCode statusCode, String description) {
        span.setStatus(statusCode, description);
        return this;
    }

    @Override
    public Span recordException(Throwable exception, Attributes additionalAttributes) {
        span.recordException(exception, additionalAttributes);
        return this;
    }

    @Override
    public Span updateName(String name) {
        span.updateName(name);
        return this;
    }

    @Override
    public void end() {
        onSpanEnded();
        span.end();
    }

    @Override
    public void end(long timestamp, TimeUnit unit) {
        onSpanEnded();
        span.end(timestamp, unit);
    }

    @Override
    public SpanContext getSpanContext() {
        return span.getSpanContext();
    }

    @Override
    public boolean isRecording() {
        return span.isRecording();
    }


    private void onSpanEnded() {
        span.setAttribute(PROFILE_ID_SPAN_ATTRIBUTE_KEY, profileId);
        if (config.addProfileURL) {
            span.setAttribute(PROFILE_URL_SPAN_ATTRIBUTE_KEY, buildUrl(config, profileId));
        }
        if (config.addProfileBaselineURLs) {
            addBaselineURLs();
        }
        labels.close();
    }

    private void addBaselineURLs() {
        StringBuilder qb = new StringBuilder();
        labels.forEach((k, v) -> {
            if (k.equals(LABEL_PROFILE_ID)) {
                return;
            }
            if (config.profileBaselineLabels.containsKey(k)) {
                return;
            }
            writeLabel(qb, k, v);
        });
        for (Map.Entry<String, String> it : config.profileBaselineLabels.entrySet()) {
            writeLabel(qb, it.getKey(), it.getValue());
        }
        StringBuilder query = new StringBuilder();
        String from = Long.toString(startTimeSeconds - 3600);
        String until = Long.toString(now());
        String baseLineQuery = String.format("%s{%s}", config.appName, qb.toString());
        query.append("query=").append(urlEncode(baseLineQuery));
        query.append("&from=").append(from);
        query.append("&until=").append(until);

        query.append("&leftQuery=").append(urlEncode(baseLineQuery));
        query.append("&leftFrom=").append(from);
        query.append("&leftUntil=").append(until);

        query.append("&rightQuery=").append(urlEncode(String.format("%s{%s=\"%s\"}", config.appName, LABEL_PROFILE_ID, profileId)));
        query.append("&rightFrom=").append(from);
        query.append("&rightUntil=").append(until);

        String strQuery = query.toString();
        span.setAttribute(PROFILE_BASELINE_URL_SPAN_ATTRIBUTE_KEY, config.pyroscopeEndpoint + "/comparison?" + strQuery);
        span.setAttribute(PROFILE_DIFF_URL_SPAN_ATTRIBUTE_KEY, config.pyroscopeEndpoint + "/comparison-diff?" + strQuery);
    }

    private void writeLabel(StringBuilder sb, String k, String v) {
        if (sb.length() != 0) {
            sb.append(",");
        }
        sb.append(k).append("=\"").append(v).append("\"");
    }

    public static String buildUrl(PyroscopeTelemetry.Config cfg, String profileId) {
        String query = String.format("%s{%s=\"%s\"}", cfg.appName, LABEL_PROFILE_ID, profileId);
        return String.format("%s?query=%s", cfg.pyroscopeEndpoint, urlEncode(query));
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private static String urlEncode(String query) {
        try {
            return URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
