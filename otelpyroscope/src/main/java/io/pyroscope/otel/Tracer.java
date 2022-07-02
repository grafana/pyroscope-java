package io.pyroscope.otel;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.TracerBuilder;

class Tracer implements io.opentelemetry.api.trace.Tracer {
    final PyroscopeTelemetry.Config config;

    final io.opentelemetry.api.trace.Tracer tracer;

    Tracer(io.opentelemetry.api.trace.Tracer tracer, PyroscopeTelemetry.Config config) {
        this.tracer = tracer;
        this.config = config;
    }

    @Override
    public SpanBuilder spanBuilder(String spanName) {
        return new SpanBuilderWrapper(tracer.spanBuilder(spanName), config);
    }
}
