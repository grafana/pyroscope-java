package io.pyroscope.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

import java.util.Collections;
import java.util.Map;

public class PyroscopeTelemetry implements OpenTelemetry {
    final OpenTelemetry delegate;
    final Config config;
    final TracerProvider tracerProvider;

    public PyroscopeTelemetry(OpenTelemetry delegate, Config config) {
        this.delegate = delegate;
        this.config = config;
        this.tracerProvider = new PyroscopeTracerProvider(delegate.getTracerProvider(), config);
    }

    @Override
    public TracerProvider getTracerProvider() {
        return tracerProvider;
    }

    @Override
    public ContextPropagators getPropagators() {
        return delegate.getPropagators();
    }

    public static class Config {
        final String appName;
        final String pyroscopeEndpoint;
        final boolean rootSpanOnly;
        final boolean addSpanName;
        final boolean addProfileURL;
        final boolean addProfileBaselineURLs;
        final Map<String, String> profileBaselineLabels;

        private Config(Builder builder) {
            if (builder.appName == null) {
                throw new IllegalArgumentException("appName is required");
            }
            if (builder.pyroscopeEndpoint == null) {
                throw new IllegalArgumentException("pyroscopeEndpoint is required");
            }
            if (builder.profileBaselineLabels == null) {
                throw new IllegalArgumentException("profileBaselineLabels is required");
            }
            //todo warn if appname does not end with cpu, itimer, wall
            this.appName = builder.appName;
            this.pyroscopeEndpoint = builder.pyroscopeEndpoint;
            this.rootSpanOnly = builder.rootSpanOnly;
            this.addSpanName = builder.addSpanName;
            this.addProfileURL = builder.addProfileURL;
            this.addProfileBaselineURLs = builder.addProfileBaselineURLs;
            this.profileBaselineLabels = builder.profileBaselineLabels;
        }

        public static class Builder {
            String appName;
            String pyroscopeEndpoint;
            boolean rootSpanOnly = true;
            boolean addSpanName = false;
            boolean addProfileURL = false;
            boolean addProfileBaselineURLs = false;
            Map<String, String> profileBaselineLabels = Collections.emptyMap();

            public Builder() {
            }

            public Builder setAppName(String appName) {
                this.appName = appName;
                return this;
            }

            public Builder setPyroscopeEndpoint(String pyroscopeEndpoint) {
                this.pyroscopeEndpoint = pyroscopeEndpoint;
                return this;
            }

            public Builder setRootSpanOnly(boolean rootSpanOnly) {
                this.rootSpanOnly = rootSpanOnly;
                return this;
            }

            public Builder setAddSpanName(boolean addSpanName) {
                this.addSpanName = addSpanName;
                return this;
            }

            public Builder setAddProfileURL(boolean addProfileURL) {
                this.addProfileURL = addProfileURL;
                return this;
            }

            public Builder setAddProfileBaselineURLs(boolean addProfileBaselineURLs) {
                this.addProfileBaselineURLs = addProfileBaselineURLs;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }
}
