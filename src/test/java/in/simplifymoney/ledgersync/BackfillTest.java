package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.model.*;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.DocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BackfillTest {

    @Test
    void duplicateRowsAreSkipped() {

        NormalizedTxn txn = new NormalizedTxn(
                "4821",
                OffsetDateTime.parse("2026-08-01T10:00:00+05:30"),
                Direction.DEBIT,
                new BigDecimal("100.00"),
                Category.SPEND,
                "TEST",
                List.of("msg-1")
        );

        FakeStore target = new FakeStore();

        target.save(txn);

        target.save(txn);

        assertEquals(1, target.data.size());
    }

    static class FakeStore implements DocumentStore {

        Map<String, NormalizedTxn> data = new HashMap<>();

        @Override
        public void save(NormalizedTxn txn) {
            data.put(
                    txn.accountLast4()
                            + txn.occurredAt()
                            + txn.amount(),
                    txn
            );
        }

        @Override
        public List<NormalizedTxn> forAccountMonth(
                String accountLast4, YearMonth month) {
            return new ArrayList<>(data.values());
        }

        @Override
        public Map<Category, BigDecimal> categoryTotals(
                String accountLast4) {
            return new EnumMap<>(Category.class);
        }

        @Override
        public Optional<NormalizedTxn> byMessageId(
                String messageId) {
            return data.values().stream()
                    .filter(t -> t.sourceMessageIds()
                            .contains(messageId))
                    .findFirst();
        }
    }
}