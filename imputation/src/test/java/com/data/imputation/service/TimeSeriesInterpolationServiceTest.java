package com.data.imputation.service;

import com.data.imputation.model.CsvTable;
import com.data.imputation.model.DataRow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSeriesInterpolationServiceTest {

    // ---------- test_all_empty_middle.csv ----------

    @Test
    void fills_long_middle_gap_and_keeps_ends_uninterpolated() throws Exception {
        Path tempDir = Files.createTempDirectory("tsi-test");
        Path input = copyResourceToTemp("test_files/test_all_empty_middle.csv", tempDir);

        CsvService csvService = new CsvService();
        TimeSeriesInterpolationService service = new TimeSeriesInterpolationService(csvService);

        Path output = service.processFile(input, "test");

        // Read only data section (before 'Average' stats row)
        CsvTable resultTable = readDataSectionIgnoringStats(output, csvService);
        List<DataRow> rows = resultTable.getRows();

        // Locate the first and last timestamps we care about
        int firstIdx = indexOfTimestamp(rows, "2025-01-01T00:00:00Z");
        int lastIdx  = indexOfTimestamp(rows, "2025-01-01T08:00:00Z");

        String firstValStr = rows.get(firstIdx).getValues().get(0);
        String lastValStr  = rows.get(lastIdx).getValues().get(0);

        double firstVal = Double.parseDouble(firstValStr);
        double lastVal  = Double.parseDouble(lastValStr);

        // First value should remain the anchor 10
        assertThat(firstValStr).isEqualTo("10");

        // Last value should be strictly greater than the first (some interpolated result)
        assertThat(lastVal).isGreaterThan(firstVal);

        // Check that at least one middle point is interpolated and between first and last
        boolean foundInterpolated = false;
        for (int i = firstIdx + 1; i < lastIdx; i++) {
            String vStr = rows.get(i).getValues().get(0);
            if (vStr != null && !vStr.isBlank()) {
                double v = Double.parseDouble(vStr);
                assertThat(v).isGreaterThan(firstVal).isLessThan(lastVal);
                foundInterpolated = true;
            }
        }
        assertThat(foundInterpolated).isTrue();
    }

    // ---------- test_sparse_columns.csv ----------

    @Test
    void sparse_columns_interpolate_numeric_and_preserve_keywords() throws Exception {
        Path tempDir = Files.createTempDirectory("tsi-test");
        Path input = copyResourceToTemp("test_files/test_sparse_columns.csv", tempDir);

        CsvService csvService = new CsvService();
        TimeSeriesInterpolationService service = new TimeSeriesInterpolationService(csvService);

        Path output = service.processFile(input, "test");
        CsvTable table = readDataSectionIgnoringStats(output, csvService);
        List<DataRow> rows = table.getRows();

        // sensor_a is column index 0 (after timestamp)
        int idx0100 = indexOfTimestamp(rows, "2025-01-01T01:00:00Z");
        int idx0400 = indexOfTimestamp(rows, "2025-01-01T04:00:00Z");
        int idx0600 = indexOfTimestamp(rows, "2025-01-01T06:00:00Z");

        double a0100 = Double.parseDouble(rows.get(idx0100).getValues().get(0));
        double a0400 = Double.parseDouble(rows.get(idx0400).getValues().get(0));
        double a0600 = Double.parseDouble(rows.get(idx0600).getValues().get(0));

        // Anchors based on test_sparse_columns.csv we defined
        assertThat(a0100).isEqualTo(10.0);
        assertThat(a0400).isEqualTo(16.0);
        assertThat(a0600).isEqualTo(22.0);

        // sensor_b is column index 1
        int idx0600b = idx0600;
        int idx0700 = indexOfTimestamp(rows, "2025-01-01T07:00:00Z");
        int idx0800 = indexOfTimestamp(rows, "2025-01-01T08:00:00Z");
        int idx0900 = indexOfTimestamp(rows, "2025-01-01T09:00:00Z");

        double b0600 = Double.parseDouble(rows.get(idx0600b).getValues().get(1));
        String b0700 = rows.get(idx0700).getValues().get(1);
        String b0800 = rows.get(idx0800).getValues().get(1);
        double b0900 = Double.parseDouble(rows.get(idx0900).getValues().get(1));

        // keyword preserved
        assertThat(b0700).isEqualTo("BLOCK");

        // 08:00 value interpolated between 50 and 55
        double v0800 = Double.parseDouble(b0800);
        assertThat(v0800).isGreaterThan(b0600).isLessThan(b0900);
    }

    // ---------- test_mixed_keywords.csv ----------

    @Test
    void mixed_keywords_column_sets_nonNumericalDetected_flag() throws Exception {
        Path tempDir = Files.createTempDirectory("tsi-test");
        Path input = copyResourceToTemp("test_files/test_mixed_keywords.csv", tempDir);

        CsvService csvService = new CsvService();
        TimeSeriesInterpolationService service = new TimeSeriesInterpolationService(csvService);

        Path output = service.processFile(input, "test");
        List<String> lines = Files.readAllLines(output);

        // Last line should be the NonNumericalDetected stats row
        String nonNumRow = lines.get(lines.size() - 1);
        assertThat(nonNumRow).startsWith("NonNumericalDetected");
    }

    // ---------- test_120hrs_of_rows.csv ----------

    @Test
    void long_series_preserves_start_and_end_and_has_interpolations() throws Exception {
        Path tempDir = Files.createTempDirectory("tsi-test");
        Path input = copyResourceToTemp("test_files/test_120hrs_of_rows.csv", tempDir);

        CsvService csvService = new CsvService();
        TimeSeriesInterpolationService service = new TimeSeriesInterpolationService(csvService);

        Path output = service.processFile(input, "test");
        CsvTable table = readDataSectionIgnoringStats(output, csvService);
        List<DataRow> rows = table.getRows();

        // Just assert we have at least some rows
        assertThat(rows.size()).isGreaterThanOrEqualTo(10);

        Instant first = rows.get(0).getTimestamp();
        Instant last = rows.get(rows.size() - 1).getTimestamp();

        assertThat(first.toString()).startsWith("2025-01-01T00:00:00Z");
        assertThat(last.toString()).startsWith("2025-01-05T23:00:00Z");

        // There should be at least one interpolated temp value (contains ".")
        boolean hasInterpolatedTemp = rows.stream()
                .map(r -> r.getValues().get(0))
                .anyMatch(v -> v != null && !v.isBlank() && v.contains("."));
        assertThat(hasInterpolatedTemp).isTrue();
    }

    // ---------- test_800_columns.csv ----------

    @Test
    void wide_table_preserves_column_count() throws Exception {
        Path tempDir = Files.createTempDirectory("tsi-test");
        Path input = copyResourceToTemp("test_files/test_800_columns.csv", tempDir);

        CsvService csvService = new CsvService();
        TimeSeriesInterpolationService service = new TimeSeriesInterpolationService(csvService);

        CsvTable inputTable = csvService.readCsv(input);
        int inputColumnCount = inputTable.getHeaders().size();

        Path output = service.processFile(input, "test");
        CsvTable outputTable = readDataSectionIgnoringStats(output, csvService);
        int outputColumnCount = outputTable.getHeaders().size();

        assertThat(outputColumnCount).isEqualTo(inputColumnCount);

        for (DataRow row : outputTable.getRows()) {
            assertThat(row.getValues()).hasSize(inputColumnCount - 1); // minus timestamp
        }
    }

    // ---------- helpers ----------

    private Path copyResourceToTemp(String resourceName, Path dir) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + resourceName);
            }
            Path out = dir.resolve(Paths.get(resourceName).getFileName());
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        }
    }

    /** Reads only header + data rows, stopping before "Average,..." stats start. */
    private CsvTable readDataSectionIgnoringStats(Path csvPath, CsvService csvService) throws Exception {
        List<String> all = Files.readAllLines(csvPath);
        List<String> dataOnly = new ArrayList<>();

        for (String line : all) {
            if (line.startsWith("Average,")) {
                break;
            }
            dataOnly.add(line);
        }

        Path temp = Files.createTempFile("tsi-data-only", ".csv");
        Files.write(temp, dataOnly);
        return csvService.readCsv(temp);
    }

    private int indexOfTimestamp(List<DataRow> rows, String isoInstant) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getTimestamp().toString().equals(isoInstant)) {
                return i;
            }
        }
        throw new IllegalStateException("Timestamp not found in rows: " + isoInstant);
    }
}
