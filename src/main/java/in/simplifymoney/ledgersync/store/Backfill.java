package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves everything already in the SQL store into the document store.
 *
 * Duplicate SQL rows are skipped within a single run.
 * The document store's save operation must be idempotent so the backfill
 * can safely be run again after a partial failure.
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {

        List<NormalizedTxn> rows = source.all();

        long read = 0;
        long written = 0;
        long skipped = 0;

        Set<TransactionKey> seen = new HashSet<>();

        for (NormalizedTxn txn : rows) {

            read++;

            TransactionKey key = TransactionKey.from(txn);

            // SQL may contain duplicate transaction rows.
            if (!seen.add(key)) {
                skipped++;
                continue;
            }

            /*
             * DocumentStore.save() is an upsert in the Mongo implementation,
             * so running this method again is safe after a partial failure.
             */
            target.save(txn);
            written++;
        }

        return new Result(read, written, skipped);
    }

    private record TransactionKey(
            String accountLast4,
            String occurredAt,
            String direction,
            String amount,
            String merchant,
            String category) {

        static TransactionKey from(NormalizedTxn txn) {

            return new TransactionKey(
                    txn.accountLast4(),
                    txn.occurredAt().toString(),
                    txn.direction().name(),
                    txn.amount().setScale(2).toPlainString(),
                    normalize(txn.merchant()),
                    txn.category().name()
            );
        }

        private static String normalize(String value) {

            if (value == null) {
                return "";
            }

            return value
                    .trim()
                    .replaceAll("\\s+", " ")
                    .toUpperCase();
        }
    }

    public record Result(
            long read,
            long written,
            long skipped) {
    }
}