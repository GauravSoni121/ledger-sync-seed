package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.DocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsChangedDocumentAmount() {

        Path db = tempDir.resolve("ledger");

        try (SqlLedgerStore sql = new SqlLedgerStore(db)) {

            sql.migrate(Path.of("db", "migration"));

            NormalizedTxn sqlTxn = new NormalizedTxn(
                    "4821",
                    OffsetDateTime.parse(
                            "2026-08-01T10:00:00+05:30"),
                    Direction.DEBIT,
                    new BigDecimal("500.00"),
                    Category.SPEND,
                    "TEST MERCHANT",
                    List.of("consistency-test-1")
            );

            sql.save(sqlTxn);

            NormalizedTxn alteredDocument =
                    new NormalizedTxn(
                            "4821",
                            sqlTxn.occurredAt(),
                            Direction.DEBIT,
                            new BigDecimal("999.00"),
                            Category.SPEND,
                            "TEST MERCHANT",
                            List.of("consistency-test-1")
                    );

            FakeDocumentStore documents =
                    new FakeDocumentStore(alteredDocument);

            ConsistencyChecker checker =
                    new ConsistencyChecker(sql, documents);

            List<ConsistencyChecker.Divergence> divergences =
                    checker.check();

            assertFalse(divergences.isEmpty());

            assertTrue(
                    divergences.stream()
                            .anyMatch(d ->
                                    d.what().contains(".amount"))
            );
        }
    }

    private static final class FakeDocumentStore
            implements DocumentStore {

        private final NormalizedTxn txn;

        FakeDocumentStore(NormalizedTxn txn) {
            this.txn = txn;
        }

        @Override
        public void save(NormalizedTxn txn) {
        }

        @Override
        public List<NormalizedTxn> forAccountMonth(
                String accountLast4,
                YearMonth month) {
            return List.of();
        }

        @Override
        public Map<Category, BigDecimal> categoryTotals(
                String accountLast4) {
            return new EnumMap<>(Category.class);
        }

        @Override
        public Optional<NormalizedTxn> byMessageId(
                String messageId) {

            if (txn.sourceMessageIds().contains(messageId)) {
                return Optional.of(txn);
            }

            return Optional.empty();
        }
    }
}
