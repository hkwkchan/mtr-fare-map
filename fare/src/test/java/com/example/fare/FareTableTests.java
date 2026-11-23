package com.example.fare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FareTableTests {
    private static final String HEADER = "SRC_STATION_NAME,SRC_STATION_ID,DEST_STATION_NAME,DEST_STATION_ID,"
            + "OCT_ADT_FARE,OCT_STD_FARE,OCT_JOYYOU_SIXTY_FARE,SINGLE_ADT_FARE,"
            + "OCT_CON_CHILD_FARE,OCT_CON_ELDERLY_FARE,OCT_CON_PWD_FARE,"
            + "SINGLE_CON_CHILD_FARE,SINGLE_CON_ELDERLY_FARE\n";
    private static final String ROW = "A,1,B,2,4.90,3.20,2.00,5.00,3.20,2.00,2.00,3.50,3.50\n";

    @Test
    void matchesEveryFareInCsv() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/mtr_lines_fares.csv");
        FareTable table = FareTable.load(resource);
        CsvMapper mapper = new CsvMapper();
        List<String> columns = List.of("OCT_ADT_FARE", "OCT_STD_FARE", "OCT_JOYYOU_SIXTY_FARE",
                "SINGLE_ADT_FARE", "OCT_CON_CHILD_FARE", "OCT_CON_ELDERLY_FARE",
                "OCT_CON_PWD_FARE", "SINGLE_CON_CHILD_FARE", "SINGLE_CON_ELDERLY_FARE");
        int count = 0;
        try (MappingIterator<Map<String, String>> rows = mapper.readerFor(
                mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                .with(CsvSchema.emptySchema().withHeader()).readValues(resource.getInputStream())) {
            while (rows.hasNextValue()) {
                Map<String, String> row = rows.nextValue();
                String start = row.get("SRC_STATION_ID");
                String dest = row.get("DEST_STATION_ID");
                FareTable.FareResponse fare = table.find(start, dest);
                assertNotNull(fare, start + " -> " + dest);
                List<Integer> actual = List.of(fare.octAdultCents(), fare.octStudentCents(),
                        fare.octJoyyouSixtyCents(), fare.singleAdultCents(), fare.octChildCents(),
                        fare.octElderlyCents(), fare.octPwdCents(), fare.singleChildCents(),
                        fare.singleElderlyCents());
                for (int i = 0; i < actual.size(); i++) {
                    assertEquals(new BigDecimal(row.get(columns.get(i))).movePointRight(2).intValueExact(),
                            actual.get(i), start + " -> " + dest + ", category " + i);
                }
                count++;
            }
        }
        assertEquals(9216, count);
        assertNull(table.find("1", "44"));
        assertNotNull(table.find("1", "70"));
    }

    @Test
    void distinguishesMissingPairsZeroFaresAndDirections() {
        String zeroRow = "B,2,B,2,0,0,0,0,0,0,0,0,0\n";
        String reverseRow = "B,2,A,1,6.00,3.20,2.00,5.00,3.20,2.00,2.00,3.50,3.50\n";
        FareTable table = load("\uFEFF" + HEADER + ROW + zeroRow + reverseRow);
        assertEquals(490, table.find("1", "2").octAdultCents());
        assertEquals(600, table.find("2", "1").octAdultCents());
        assertEquals(0, table.find("2", "2").octAdultCents());
        assertNull(table.find("1", "1"));
        assertNull(table.find("99", "2"));
    }

    @Test
    void loadsFromSingleUseStream() {
        String reverseRow = "B,2,A,1,6.00,3.20,2.00,5.00,3.20,2.00,2.00,3.50,3.50\n";
        InputStreamResource resource = new InputStreamResource(
                new ByteArrayInputStream((HEADER + ROW + reverseRow).getBytes(StandardCharsets.UTF_8)));
        FareTable table = FareTable.load(resource);
        assertEquals(490, table.find("1", "2").octAdultCents());
        assertEquals(600, table.find("2", "1").octAdultCents());
    }

    @Test
    void rejectsMalformedData() {
        assertInvalid("wrong,header\n1,2\n", "header");
        assertInvalid(HEADER, "no rows");
        assertInvalid(HEADER + ROW + ROW, "Duplicate fare pair at CSV row 3");
        assertInvalid(HEADER + ROW + ROW.replace("A,1,B,2", "B,2,A,1").replace("4.90", "-0.01"),
                "at CSV row 3");
        assertInvalid(HEADER + ROW.replace("A,1,B,2", "A,,B,2"), "station ID");
        assertInvalid(HEADER + ROW.replace("4.90", "-0.01"), "Invalid fare");
        assertInvalid(HEADER + ROW.replace("4.90", "4.901"), "Invalid fare");
        assertInvalid(HEADER + ROW.replace("4.90", "327.68"), "Invalid fare");
        assertInvalid(HEADER + ROW.replace("4.90", ""), "Missing fare");
    }

    private void assertInvalid(String csv, String message) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> load(csv));
        assertTrue(exception.getCause().getMessage().contains(message), exception.toString());
    }

    private FareTable load(String csv) {
        return FareTable.load(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));
    }
}
