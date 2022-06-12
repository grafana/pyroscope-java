package io.pyroscope.otel;

import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;

class PyroscopeTracerProvider implements TracerProvider {
    final TracerProvider tp;
    final PyroscopeTelemetry.Config config;

    public PyroscopeTracerProvider(TracerProvider tp, PyroscopeTelemetry.Config config) {
        this.tp = tp;
        this.config = config;
    }

    @Override
    public io.opentelemetry.api.trace.Tracer get(String instrumentationScopeName) {
        return new Tracer(tp.get(instrumentationScopeName), config);
    }

    @Override
    public io.opentelemetry.api.trace.Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
        return new Tracer(tp.get(instrumentationScopeName, instrumentationScopeVersion), config);
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return TracerProvider.super.tracerBuilder(instrumentationScopeName);
    }
}
