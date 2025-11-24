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
import java.util.List;

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
            // header
            bw.write(String.join(",", table.getHeaders()));
            bw.newLine();

            for (DataRow row : table.getRows()) {
                StringBuilder sb = new StringBuilder();
                sb.append(row.getTimestamp().toString());

                for (String v : row.getValues()) {
                    sb.append(",");
                    sb.append(v == null ? "" : v);
                }

                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }
}
