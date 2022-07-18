package io.pyroscope.javaagent.config;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AppName {
    final String name;
    final Map<String, String> labels;

    public AppName(String name, Map<String, String> labels) {
        this.name = name;
        this.labels = Collections.unmodifiableMap(new TreeMap<>(labels));
    }

    @Override
    public String toString() {
        if (labels.isEmpty()) {
            return name;
        }
        StringJoiner joinedLabels = new StringJoiner(",");
        for (Map.Entry<String, String> e : this.labels.entrySet()) {
            joinedLabels.add((e.getKey().trim()) + "=" + (e.getValue().trim()));
        }
        return String.format("%s{%s}", name, joinedLabels);
    }

    public Builder newBuilder() {
        return new Builder(name, labels);
    }

    public static class Builder {
        private String name;
        private Map<String, String> labels;

        public Builder(String name) {
            this.name = name;
            this.labels = new TreeMap<>();
        }

        public Builder(String name, Map<String, String> labels) {
            this.name = name;
            this.labels = new TreeMap<>(labels);
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder addLabel(String k, String v) {
            if (isValidLabel(k) || isValidLabel(v)) {
                this.labels.put(k, v);
            }
            return this;
        }

        public Builder addLabels(Map<String, String> labels) {
            for (Map.Entry<String, String> it : labels.entrySet()) {
                addLabel(it.getKey(), it.getValue());
            }
            return this;
        }

        public AppName build() {
            return new AppName(name, labels);
        }
    }

    public static AppName parse(String appName) {
        int l = appName.indexOf('{');
        int r = appName.indexOf('}');
        if (l != -1 && r != -1 && l < r) {
            String name = appName.substring(0, l);
            String strLabels = appName.substring(l + 1, r);
            Map<String, String> labelsMap = parseLabels(strLabels);
            return new AppName(name, labelsMap);
        } else {
            return new AppName(appName, Collections.emptyMap());
        }
    }

    @NotNull
    public static Map<String, String> parseLabels(String strLabels) {
        String[] labels = strLabels.split(",");
        Map<String, String> labelMap = new HashMap<>();
        for (String label : labels) {
            String[] kv = label.split("=");
            if (kv.length != 2) {
                continue;
            }
            kv[0] = kv[0].trim();
            kv[1] = kv[1].trim();
            if (!isValidLabel(kv[0]) || !isValidLabel(kv[1])) {
                continue;
            }
            labelMap.put(kv[0], kv[1]);
        }
        return labelMap;
    }

    public static boolean isValidLabel(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            int c = s.codePointAt(i);
            if (c == '{' || c == '}' || c == ',' || c == '=' || c == ' ') {
                return false;
            }
        }
        return true;
    }
}
