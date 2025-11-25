package com.data.imputation.service;

import com.data.imputation.model.CsvTable;
import com.data.imputation.model.DataRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TimeSeriesInterpolationService {

    private final CsvService csvService;

    public TimeSeriesInterpolationService(CsvService csvService) {
        this.csvService = csvService;
    }

    public Path processFile(Path inputPath, String suffixRaw) throws IOException {
        CsvTable table = csvService.readCsv(inputPath);
        List<DataRow> originalRows = new ArrayList<>(table.getRows());

        if (originalRows.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 data rows to interpolate.");
        }

        originalRows.sort(Comparator.comparing(DataRow::getTimestamp));

        Duration step = detectStep(originalRows);

        List<DataRow> fullRows =
                fillMissingTimestamps(originalRows, step, table.getHeaders().size() - 1);

        interpolateColumns(fullRows, step);

        CsvTable outputTable = new CsvTable(table.getHeaders(), fullRows);

        String fileName = inputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        String ext = (dotIndex > 0) ? fileName.substring(dotIndex) : ".csv";

        String suffix = (suffixRaw == null) ? "" : suffixRaw.trim();

        String middle;
        if (suffix.isEmpty()) {
            middle = "";          // no change
        } else {
            middle = "_" + suffix;
        }

        Path outputPath = inputPath.getParent()
                .resolve(baseName + middle + ext);

        // write + stats happen inside CsvService
        csvService.writeCsv(outputPath, outputTable);

        // TODO: add S3 upload using outputPath
        return outputPath;
    }

    private Duration detectStep(List<DataRow> sortedRows) {
        Map<Long, Integer> counts = new HashMap<>();

        for (int i = 0; i < sortedRows.size() - 1; i++) {
            Instant t1 = sortedRows.get(i).getTimestamp();
            Instant t2 = sortedRows.get(i + 1).getTimestamp();
            long diffMillis = Duration.between(t1, t2).toMillis();
            if (diffMillis <= 0) continue;

            counts.merge(diffMillis, 1, Integer::sum);
        }

        if (counts.isEmpty()) {
            throw new IllegalStateException("Cannot detect a positive step size.");
        }

        long bestDiff = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();

        return Duration.ofMillis(bestDiff);
    }

    private List<DataRow> fillMissingTimestamps(List<DataRow> sortedRows,
                                                Duration step,
                                                int columnCount) {

        sortedRows.sort(Comparator.comparing(DataRow::getTimestamp));

        Map<Instant, DataRow> byTimestamp = new HashMap<>();
        for (DataRow row : sortedRows) {
            byTimestamp.put(row.getTimestamp(), row);
        }

        Instant start = sortedRows.get(0).getTimestamp();
        Instant end = sortedRows.get(sortedRows.size() - 1).getTimestamp();

        List<DataRow> full = new ArrayList<>();
        for (Instant t = start; !t.isAfter(end); t = t.plus(step)) {
            DataRow existing = byTimestamp.get(t);
            if (existing != null) {
                full.add(existing);
            } else {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    values.add("");
                }
                full.add(new DataRow(t, values));
            }
        }

        return full;
    }

    private void interpolateColumns(List<DataRow> rows, Duration step) {
        if (rows.isEmpty()) return;

        int columnCount = rows.get(0).getValues().size();

        for (int col = 0; col < columnCount; col++) {
            interpolateSingleColumn(rows, col);
        }
    }

    private void interpolateSingleColumn(List<DataRow> rows, int colIndex) {
        int n = rows.size();
        int i = 0;

        while (i < n) {
            while (i < n && !isNumeric(rows.get(i).getValues().get(colIndex))) {
                i++;
            }
            if (i >= n - 1) break;

            int start = i;
            i++;

            while (i < n && !isNumeric(rows.get(i).getValues().get(colIndex))) {
                i++;
            }
            if (i >= n) break;

            int end = i;

            double vStart = Double.parseDouble(rows.get(start).getValues().get(colIndex));
            double vEnd = Double.parseDouble(rows.get(end).getValues().get(colIndex));
            Instant tStart = rows.get(start).getTimestamp();
            Instant tEnd = rows.get(end).getTimestamp();
            long totalMillis = java.time.Duration.between(tStart, tEnd).toMillis();
            if (totalMillis <= 0) {
                continue;
            }

            for (int j = start + 1; j < end; j++) {
                DataRow row = rows.get(j);
                String cell = row.getValues().get(colIndex);

                if (cell == null || cell.isBlank()) {
                    long currentMillis = java.time.Duration.between(tStart, row.getTimestamp()).toMillis();
                    double ratio = (double) currentMillis / (double) totalMillis;
                    double vCurrent = vStart + (vEnd - vStart) * ratio;
                    row.getValues().set(colIndex, Double.toString(vCurrent));
                }
            }

            i = end;
        }
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
