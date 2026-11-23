package com.example.fare;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.core.io.Resource;

public final class FareTable {
    private static final int FARE_COUNT = 9;
    private static final short MISSING = -1;
    private static final List<String> HEADER = List.of(
            "SRC_STATION_NAME", "SRC_STATION_ID", "DEST_STATION_NAME", "DEST_STATION_ID",
            "OCT_ADT_FARE", "OCT_STD_FARE", "OCT_JOYYOU_SIXTY_FARE", "SINGLE_ADT_FARE",
            "OCT_CON_CHILD_FARE", "OCT_CON_ELDERLY_FARE", "OCT_CON_PWD_FARE",
            "SINGLE_CON_CHILD_FARE", "SINGLE_CON_ELDERLY_FARE");

    private final Map<String, Integer> indices;
    private final short[] fares;

    private FareTable(Map<String, Integer> indices, short[] fares) {
        this.indices = Map.copyOf(indices);
        this.fares = fares;
    }

    static FareTable load(Resource resource) {
        try {
            Map<String, Integer> indices = new LinkedHashMap<>();
            List<FareCsvRow> csvRows = new ArrayList<>();
            readRows(resource, (row, line) -> {
                validateId(row.startId(), line);
                validateId(row.destId(), line);
                indices.computeIfAbsent(row.startId(), ignored -> indices.size());
                indices.computeIfAbsent(row.destId(), ignored -> indices.size());
                csvRows.add(row);
            });
            if (indices.isEmpty()) {
                throw new IOException("Fare CSV contains no rows");
            }

            int length;
            try {
                length = Math.multiplyExact(Math.multiplyExact(indices.size(), indices.size()), FARE_COUNT);
            } catch (ArithmeticException exception) {
                throw new IOException("Fare table is too large", exception);
            }
            short[] fares = new short[length];
            Arrays.fill(fares, MISSING);
            for (int rowIndex = 0; rowIndex < csvRows.size(); rowIndex++) {
                FareCsvRow row = csvRows.get(rowIndex);
                int line = rowIndex + 2;
                int offset = (indices.get(row.startId()) * indices.size() + indices.get(row.destId())) * FARE_COUNT;
                if (fares[offset] != MISSING) {
                    throw new IOException("Duplicate fare pair at CSV row " + line);
                }
                String[] values = row.fareValues();
                for (int i = 0; i < FARE_COUNT; i++) {
                    fares[offset + i] = parseCents(values[i], line, HEADER.get(i + 4));
                }
            }
            return new FareTable(indices, fares);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load MTR fare data from " + resource.getDescription(), exception);
        }
    }

    FareResponse find(String startId, String destId) {
        Integer start = indices.get(startId);
        Integer dest = indices.get(destId);
        if (start == null || dest == null) {
            return null;
        }
        int offset = (start * indices.size() + dest) * FARE_COUNT;
        if (fares[offset] == MISSING) {
            return null;
        }
        return new FareResponse(startId, destId, fares[offset], fares[offset + 1], fares[offset + 2],
                fares[offset + 3], fares[offset + 4], fares[offset + 5], fares[offset + 6],
                fares[offset + 7], fares[offset + 8]);
    }

    private static short parseCents(String value, int line, String column) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Missing fare in " + column + " at CSV row " + line);
        }
        try {
            int cents = new BigDecimal(value).movePointRight(2).intValueExact();
            if (cents < 0 || cents > Short.MAX_VALUE) {
                throw new ArithmeticException("Fare outside supported range");
            }
            return (short) cents;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IOException("Invalid fare in " + column + " at CSV row " + line, exception);
        }
    }

    private static void validateId(String id, int line) throws IOException {
        if (id == null || !id.matches("[1-9][0-9]*")) {
            throw new IOException("Invalid station ID at CSV row " + line);
        }
    }

    private static void readRows(Resource resource, RowConsumer consumer) throws IOException {
        CsvMapper mapper = new CsvMapper();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.mark(1);
            if (reader.read() != '\uFEFF') {
                reader.reset();
            }
            try (MappingIterator<FareCsvRow> rows = mapper.readerFor(FareCsvRow.class)
                    .with(CsvSchema.emptySchema().withHeader()).readValues(reader)) {
                boolean hasRow = rows.hasNextValue();
                List<String> columns = ((CsvParser) rows.getParser()).getSchema().getColumnNames();
                if (!HEADER.equals(columns)) {
                    throw new IOException("Unexpected fare CSV header");
                }
                int line = 1;
                while (hasRow) {
                    consumer.accept(rows.nextValue(), ++line);
                    hasRow = rows.hasNextValue();
                }
            }
        }
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(FareCsvRow row, int line) throws IOException;
    }

    private record FareCsvRow(
            @JsonProperty("SRC_STATION_NAME") String startName,
            @JsonProperty("SRC_STATION_ID") String startId,
            @JsonProperty("DEST_STATION_NAME") String destName,
            @JsonProperty("DEST_STATION_ID") String destId,
            @JsonProperty("OCT_ADT_FARE") String octAdult,
            @JsonProperty("OCT_STD_FARE") String octStudent,
            @JsonProperty("OCT_JOYYOU_SIXTY_FARE") String octJoyyouSixty,
            @JsonProperty("SINGLE_ADT_FARE") String singleAdult,
            @JsonProperty("OCT_CON_CHILD_FARE") String octChild,
            @JsonProperty("OCT_CON_ELDERLY_FARE") String octElderly,
            @JsonProperty("OCT_CON_PWD_FARE") String octPwd,
            @JsonProperty("SINGLE_CON_CHILD_FARE") String singleChild,
            @JsonProperty("SINGLE_CON_ELDERLY_FARE") String singleElderly) {
        String[] fareValues() {
            return new String[] {octAdult, octStudent, octJoyyouSixty, singleAdult, octChild,
                    octElderly, octPwd, singleChild, singleElderly};
        }
    }

    public record FareResponse(
            String startStationId, String destinationStationId,
            int octAdultCents, int octStudentCents, int octJoyyouSixtyCents, int singleAdultCents,
            int octChildCents, int octElderlyCents, int octPwdCents,
            int singleChildCents, int singleElderlyCents) {
    }
}
