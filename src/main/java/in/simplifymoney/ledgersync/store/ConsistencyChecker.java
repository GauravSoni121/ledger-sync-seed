package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Proves the two stores agree, and says precisely where they do not.
 *
 * The checker compares the actual transaction fields, not just row counts.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        List<Divergence> divergences = new ArrayList<>();

        for (NormalizedTxn sqlTxn : sql.all()) {

            /*
             * Every SQL transaction should be discoverable through at least
             * one of its source message IDs.
             */
            if (sqlTxn.sourceMessageIds().isEmpty()) {
                divergences.add(new Divergence(
                        transactionName(sqlTxn) + ".source_message_ids",
                        "missing",
                        "not checkable"
                ));
                continue;
            }

            for (String messageId : sqlTxn.sourceMessageIds()) {

                Optional<NormalizedTxn> documentTxn =
                        documents.byMessageId(messageId);

                if (documentTxn.isEmpty()) {
                    divergences.add(new Divergence(
                            transactionName(sqlTxn),
                            describe(sqlTxn),
                            "missing for message_id=" + messageId
                    ));
                    continue;
                }

                compare(sqlTxn, documentTxn.get(), messageId, divergences);
            }
        }

        return divergences;
    }

    private static void compare(
            NormalizedTxn sqlTxn,
            NormalizedTxn documentTxn,
            String messageId,
            List<Divergence> divergences) {

        compareField(
                transactionName(sqlTxn) + ".account_last4",
                sqlTxn.accountLast4(),
                documentTxn.accountLast4(),
                divergences
        );

        compareField(
                transactionName(sqlTxn) + ".occurred_at",
                sqlTxn.occurredAt().toString(),
                documentTxn.occurredAt().toString(),
                divergences
        );

        compareField(
                transactionName(sqlTxn) + ".direction",
                sqlTxn.direction().name(),
                documentTxn.direction().name(),
                divergences
        );

        compareField(
                transactionName(sqlTxn) + ".amount",
                sqlTxn.amount().setScale(2).toPlainString(),
                documentTxn.amount().setScale(2).toPlainString(),
                divergences
        );

        compareField(
                transactionName(sqlTxn) + ".category",
                sqlTxn.category().name(),
                documentTxn.category().name(),
                divergences
        );

        compareField(
                transactionName(sqlTxn) + ".merchant",
                normalize(sqlTxn.merchant()),
                normalize(documentTxn.merchant()),
                divergences
        );

        /*
         * The message ID being checked must still point to the transaction
         * returned by the document store.
         */
        if (!documentTxn.sourceMessageIds().contains(messageId)) {
            divergences.add(new Divergence(
                    transactionName(sqlTxn) + ".source_message_ids",
                    messageId,
                    documentTxn.sourceMessageIds().toString()
            ));
        }
    }

    private static void compareField(
            String what,
            String sqlValue,
            String documentValue,
            List<Divergence> divergences) {

        if (!sqlValue.equals(documentValue)) {
            divergences.add(new Divergence(
                    what,
                    sqlValue,
                    documentValue
            ));
        }
    }

    private static String transactionName(NormalizedTxn txn) {
        return "transaction["
                + txn.accountLast4()
                + "|"
                + txn.occurredAt()
                + "|"
                + txn.direction()
                + "|"
                + txn.amount().setScale(2).toPlainString()
                + "]";
    }

    private static String describe(NormalizedTxn txn) {
        return "account=" + txn.accountLast4()
                + ", occurred_at=" + txn.occurredAt()
                + ", direction=" + txn.direction()
                + ", amount=" + txn.amount().setScale(2).toPlainString()
                + ", category=" + txn.category()
                + ", merchant=" + txn.merchant();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    /** One place the two stores disagree. */
    public record Divergence(String what, String inSql, String inDocuments) {}
}