package co.uk.vturbo.sbus.model;

import java.util.HashMap;
import java.util.Map;


public class MapBuilder {

    public static Builder with(String key, Object value) {
        return new Builder().with(key, value);
    }

    public static class Builder {

        private final Map<String, Object> map = new HashMap<>();

        public Builder with(String key, Object value) {
            map.put(key, value);
            return this;
        }

        public Builder withAll(Map<String, ?> values) {
            map.putAll(values);
            return this;
        }

        public Map<String, Object> get() {
            return map;
        }
    }
}
