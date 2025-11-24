package com.data.imputation.model;

import java.util.List;

public class CsvTable {
    private final List<String> headers;
    private final List<DataRow> rows;

    public CsvTable(List<String> headers, List<DataRow> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<DataRow> getRows() {
        return rows;
    }
}
