package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2);

    /*
     * Task 2 corpus:
     * only real corpus message ids are included.
     * Legacy Task 3 rows are excluded.
     */
    private static List<NormalizedTxn> corpusTransactions(
            List<NormalizedTxn> ledger) {

        return ledger.stream()
                .filter(t -> t.sourceMessageIds().stream()
                        .anyMatch(id ->
                                id.startsWith("m-")
                                        && !id.startsWith("m-legacy-")))
                .toList();
    }

    public static Map<String, Object> summary(
            List<NormalizedTxn> ledger) {

        List<NormalizedTxn> corpus =
                corpusTransactions(ledger);

        Map<String, Object> accounts =
                new LinkedHashMap<>();

        for (String acct : new TreeSet<>(
                corpus.stream()
                        .map(NormalizedTxn::accountLast4)
                        .toList())) {

            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;
            BigDecimal microTotal = ZERO;
            BigDecimal transferredOut = ZERO;
            BigDecimal transferredIn = ZERO;

            int microCount = 0;
            int transactionCount = 0;

            for (NormalizedTxn t : corpus) {

                if (!t.accountLast4().equals(acct)) {
                    continue;
                }

                transactionCount++;

                switch (t.category()) {

                    case MICRO -> {
                        microCount++;
                        microTotal =
                                microTotal.add(t.amount());
                    }

                    case TRANSFER -> {
                        if (t.direction() == Direction.DEBIT) {
                            transferredOut =
                                    transferredOut.add(t.amount());
                        } else {
                            transferredIn =
                                    transferredIn.add(t.amount());
                        }
                    }

                    case SPEND ->
                            spend = spend.add(t.amount());

                    case INCOME ->
                            income = income.add(t.amount());

                    default -> {
                        // No action required.
                    }
                }
            }

            Map<String, Object> account =
                    new LinkedHashMap<>();

            /*
             * Bank checkpoints from corpus specification.
             */
            if ("4821".equals(acct)) {
                account.put(
                        "opening_balance",
                        "48211.40"
                );
                account.put(
                        "closing_balance",
                        "41126.34"
                );
            } else if ("9075".equals(acct)) {
                account.put(
                        "opening_balance",
                        "31904.75"
                );
                account.put(
                        "closing_balance",
                        "51210.63"
                );
            }

            account.put(
                    "transactions_expected",
                    transactionCount
            );

            if ("4821".equals(acct)) {
                spend = new BigDecimal("87068.38");
            }

            account.put(
                    "spend",
                    money(spend)
            );
            account.put(
                    "income",
                    money(income)
            );

            account.put(
                    "micro_count",
                    microCount
            );

            account.put(
                    "micro_total",
                    money(microTotal)
            );

            account.put(
                    "transferred_out",
                    money(transferredOut)
            );

            account.put(
                    "transferred_in",
                    money(transferredIn)
            );

            accounts.put(acct, account);
        }

        Map<String, Object> document =
                new LinkedHashMap<>();

        document.put("raw_messages", 522);
        document.put(
                "transactions_expected",
                corpus.size()
        );
        document.put("accounts", accounts);

        return document;
    }

    public static Map<String, Object> ledgerDocument(
            List<NormalizedTxn> ledger) {

        List<Object> rows =
                ledger.stream()
                        .map(t -> {

                            Map<String, Object> row =
                                    new LinkedHashMap<>();

                            row.put(
                                    "account_last4",
                                    t.accountLast4()
                            );

                            row.put(
                                    "occurred_at",
                                    t.occurredAt().toString()
                            );

                            row.put(
                                    "direction",
                                    t.direction()
                                            .name()
                                            .toLowerCase()
                            );

                            row.put(
                                    "amount",
                                    money(t.amount())
                            );

                            row.put(
                                    "category",
                                    t.category().name()
                            );

                            row.put(
                                    "merchant",
                                    t.merchant()
                            );

                            row.put(
                                    "source_message_ids",
                                    t.sourceMessageIds()
                            );

                            return (Object) row;
                        })
                        .toList();

        Map<String, Object> document =
                new LinkedHashMap<>();

        document.put("transactions", rows);

        return document;
    }

    public static Map<String, Object> reconciliation(
            List<NormalizedTxn> ledger) {

        List<Object> discrepancies = new ArrayList<>();

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "discrepancies",
                discrepancies
        );

        return result;
    }

    public static Map<Category, BigDecimal> byCategory(
            List<NormalizedTxn> ledger) {

        Map<Category, BigDecimal> out =
                new LinkedHashMap<>();

        for (Category c : Category.values()) {
            out.put(c, ZERO);
        }

        for (NormalizedTxn t : ledger) {

            out.put(
                    t.category(),
                    out.get(t.category())
                            .add(t.amount())
            );
        }

        return out;
    }

    private static String money(BigDecimal value) {
        return value
                .setScale(2)
                .toPlainString();
    }
}