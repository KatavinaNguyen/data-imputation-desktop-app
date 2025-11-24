package com.data.imputation.model;

import java.time.Instant;
import java.util.List;

public class DataRow {
    private final Instant timestamp;
    private final List<String> values; // columns 1..N as strings

    public DataRow(Instant timestamp, List<String> values) {
        this.timestamp = timestamp;
        this.values = values;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<String> getValues() {
        return values;
    }
}
