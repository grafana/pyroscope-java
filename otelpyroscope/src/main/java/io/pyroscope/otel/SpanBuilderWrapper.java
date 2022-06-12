package io.pyroscope.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.pyroscope.labels.LabelsSet;
import io.pyroscope.labels.ScopedContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class SpanBuilderWrapper implements SpanBuilder {
    public static final String LABEL_PROFILE_ID = "profile_id";
    public static final String LABEL_SPAN_NAME = "span_name";
    final SpanBuilder sb;
    private final PyroscopeTelemetry.Config config;

    public SpanBuilderWrapper(SpanBuilder sb, PyroscopeTelemetry.Config config) {
        this.sb = sb;
        this.config = config;
    }

    @Override
    public SpanBuilder setParent(Context context) {
        sb.setParent(context);
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        sb.setNoParent();
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        sb.addLink(spanContext);
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        sb.addLink(spanContext, attributes);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, String value) {
        sb.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
        sb.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
        sb.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
        sb.setAttribute(key, value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value) {
        sb.setAttribute(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAllAttributes(Attributes attributes) {
        sb.setAllAttributes(attributes);
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        sb.setSpanKind(spanKind);
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        sb.setStartTimestamp(startTimestamp, unit);
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(Instant startTimestamp) {
        sb.setStartTimestamp(startTimestamp);
        return this;
    }

    @Override
    public Span startSpan() {
        Span span = sb.startSpan();
        if (!(span instanceof ReadableSpan)) {
            return span;
        }
        ReadableSpan rspan = (ReadableSpan) span;
        if (config.rootSpanOnly && !isRootSpan(rspan)) {
            return span;
        }
        Map<String, String> labels = new HashMap<>();
        String profileId = span.getSpanContext().getSpanId();
        labels.put(LABEL_PROFILE_ID, profileId);
        if (config.addSpanName) {
            labels.put(LABEL_SPAN_NAME, ((ReadableSpan) span).getName());
        }
        return new SpanWrapper(span, config, new ScopedContext(new LabelsSet(labels)), profileId);
    }

    public static boolean isRootSpan(ReadableSpan span) {
        boolean remote = span.getSpanContext().isRemote();
        boolean noParent = span.getParentSpanContext() == SpanContext.getInvalid();
        return remote || noParent;
    }
}
