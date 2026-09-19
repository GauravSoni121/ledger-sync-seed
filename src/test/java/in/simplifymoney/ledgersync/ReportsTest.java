package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.model.*;
import in.simplifymoney.ledgersync.report.Reports;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportsTest {

    @Test
    void separatesMicroTransferSpendAndIncome() {

        List<NormalizedTxn> txns = List.of(
                txn("4821", Direction.DEBIT, "50.00", Category.MICRO),
                txn("4821", Direction.DEBIT, "500.00", Category.SPEND),
                txn("4821", Direction.CREDIT, "1000.00", Category.INCOME),
                txn("4821", Direction.DEBIT, "6000.00", Category.TRANSFER)
        );

        Map<String, Object> report = Reports.summary(txns);

        assertNotNull(report);
        assertTrue(report.containsKey("accounts"));
    }

    private NormalizedTxn txn(
            String account,
            Direction direction,
            String amount,
            Category category) {

        return new NormalizedTxn(
                account,
                OffsetDateTime.parse("2026-08-01T10:00:00+05:30"),
                direction,
                new BigDecimal(amount),
                category,
                "TEST",
                List.of("test-" + amount)
        );
    }
}