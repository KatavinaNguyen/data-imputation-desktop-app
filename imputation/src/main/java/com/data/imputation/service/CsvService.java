package com.data.imputation.service;

import com.data.imputation.model.CsvTable;
import com.data.imputation.model.DataRow;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CsvService {

    public CsvTable readCsv(Path path) throws IOException {
        List<String> headers;
        List<DataRow> rows = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty: " + path);
            }

            headers = new ArrayList<>(Arrays.asList(headerLine.split(",")));
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("CSV header is empty: " + path);
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",", -1); // keep trailing blanks
                if (parts.length == 0) continue;

                Instant timestamp = Instant.parse(parts[0].trim());

                List<String> values = new ArrayList<>();
                int nonTimestampColumns = headers.size() - 1;
                for (int i = 0; i < nonTimestampColumns; i++) {
                    int idx = i + 1;
                    String cell = (idx < parts.length) ? parts[idx].trim() : "";
                    values.add(cell);
                }

                rows.add(new DataRow(timestamp, values));
            }
        }

        return new CsvTable(headers, rows);
    }

    public void writeCsv(Path path, CsvTable table) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(path)) {

            List<String> headers = table.getHeaders();
            List<DataRow> rows = table.getRows();
            int colCount = headers.size();         // includes timestamp
            
            // --------------------------
            // 1. WRITE ORIGINAL DATA ROWS
            // --------------------------
            bw.write(String.join(",", headers));
            bw.newLine();

            for (DataRow row : rows) {
                StringBuilder sb = new StringBuilder();
                sb.append(row.getTimestamp().toString());

                for (String v : row.getValues()) {
                    sb.append(",");
                    sb.append(v == null ? "" : v);
                }

                bw.write(sb.toString());
                bw.newLine();
            }

            // ----------------------------------
            // 2. COLLECT NUMERIC VALUES PER COLUMN
            // ----------------------------------
            List<List<Double>> nums = new ArrayList<>();
            boolean[] nonnum = new boolean[colCount];

            for (int c = 0; c < colCount; c++) {
                nums.add(new ArrayList<>());
                nonnum[c] = false;
            }

            // skip timestamp column (c = 0)
            for (DataRow row : rows) {
                for (int c = 1; c < colCount; c++) {
                    String cell = row.getValues().get(c - 1);

                    if (cell == null || cell.isBlank()) continue;

                    try {
                        nums.get(c).add(Double.parseDouble(cell));
                    } catch (Exception e) {
                        // non-numerical (keyword, text, blocked interpolation)
                        nonnum[c] = true;
                    }
                }
            }

            // ----------------------------------
            // 3. STATISTIC HELPERS
            // ----------------------------------
            java.util.function.Function<List<Double>, Double> avg = list ->
                    list.stream().mapToDouble(v -> v).average().orElse(Double.NaN);

            java.util.function.Function<List<Double>, Double> minF = list ->
                    list.stream().mapToDouble(v -> v).min().orElse(Double.NaN);

            java.util.function.Function<List<Double>, Double> maxF = list ->
                    list.stream().mapToDouble(v -> v).max().orElse(Double.NaN);

            java.util.function.Function<List<Double>, Double> median = list -> {
                if (list.isEmpty()) return Double.NaN;
                List<Double> sorted = new ArrayList<>(list);
                java.util.Collections.sort(sorted);
                int n = sorted.size();
                return (n % 2 == 1)
                        ? sorted.get(n / 2)
                        : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            };

            java.util.function.Function<List<Double>, Double> mode = list -> {
                if (list.isEmpty()) return Double.NaN;
                Map<Double, Integer> freq = new HashMap<>();
                for (double d : list) freq.put(d, freq.getOrDefault(d, 0) + 1);
                return java.util.Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
            };

            java.util.function.BiConsumer<String, java.util.function.Function<List<Double>, Double>> statRow =
                    (label, func) -> {
                        try {
                            bw.write(label);
                            bw.write(",");
                            for (int c = 1; c < colCount; c++) {
                                bw.write(Double.toString(func.apply(nums.get(c))));
                                if (c < colCount - 1) bw.write(",");
                            }
                            bw.newLine();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    };

            // ----------------------------------
            // 4. WRITE STAT ROWS
            // ----------------------------------

            statRow.accept("Average", avg);
            statRow.accept("Median", median);
            statRow.accept("Minimum", minF);
            statRow.accept("Maximum", maxF);
            statRow.accept("Mode", mode);

            // NonNumericalDetected
            bw.write("NonNumericalDetected,");
            for (int c = 1; c < colCount; c++) {
                bw.write(nonnum[c] ? "1" : "0");
                if (c < colCount - 1) bw.write(",");
            }
            bw.newLine();
        }
    }
}
