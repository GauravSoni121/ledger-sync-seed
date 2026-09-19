package in.simplifymoney.ledgersync.ingest;


import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class IngestService {

    private static final BigDecimal MICRO_LIMIT =
            new BigDecimal("100.00");

    private static final long TRANSFER_WINDOW_MINUTES = 5;

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {

        List<RawMessage> messages = readCorpus(corpus);

        int skipped = 0;

        /*
         * Deduplicate messages which describe the same transaction.
         *
         * We intentionally do NOT include statedBalance in the key because
         * duplicate bank messages can contain different balance text while
         * still describing the same transaction.
         */
        Map<TransactionKey, NormalizedTxn> transactions =
                new LinkedHashMap<>();

        for (RawMessage message : messages) {

            Optional<ParsedTxn> parsed =
                    parsers.parse(message);

            if (parsed.isEmpty()) {
                skipped++;
                continue;
            }

            ParsedTxn p = parsed.get();

            TransactionKey key =
                    new TransactionKey(
                            p.accountLast4(),
                            p.occurredAt(),
                            p.direction(),
                            p.amount().setScale(2),
                            normalizeMerchant(p.merchant())
                    );

            NormalizedTxn existing =
                    transactions.get(key);

            if (existing == null) {

                transactions.put(
                        key,
                        toTransaction(p)
                );

            } else {

                /*
                 * Same transaction appeared in another message.
                 * Keep one transaction and merge all source message IDs.
                 */
                List<String> sourceIds =
                        new ArrayList<>(
                                existing.sourceMessageIds()
                        );

                if (!sourceIds.contains(
                        p.sourceMessageId())) {

                    sourceIds.add(
                            p.sourceMessageId()
                    );
                }

                transactions.put(
                        key,
                        new NormalizedTxn(
                                existing.accountLast4(),
                                existing.occurredAt(),
                                existing.direction(),
                                existing.amount(),
                                existing.category(),
                                existing.merchant(),
                                sourceIds
                        )
                );
            }
        }

        /*
         * After deduplication, classify matching opposite-account
         * transactions as TRANSFER.
         */
        List<NormalizedTxn> classified =
                classifyTransfers(
                        new ArrayList<>(
                                transactions.values()
                        )
                );

        for (NormalizedTxn transaction : classified) {
            store.save(transaction);
        }

        return new Stats(
                messages.size(),
                classified.size(),
                skipped
        );
    }

    public static List<RawMessage> readCorpus(
            Path corpus) throws IOException {

        List<RawMessage> out =
                new ArrayList<>();

        try (Stream<String> lines =
                     Files.lines(corpus)) {

            for (String line :
                    (Iterable<String>) lines
                            .filter(s -> !s.isBlank())
                            ::iterator) {

                Map<String, Object> object =
                        Json.parseObject(line);

                out.add(
                        new RawMessage(
                                (String) object.get(
                                        "message_id"
                                ),
                                (String) object.get(
                                        "channel"
                                ),
                                (String) object.get(
                                        "sender"
                                ),
                                OffsetDateTime.parse(
                                        (String) object.get(
                                                "received_at"
                                        )
                                ),
                                (String) object.get(
                                        "device_id"
                                ),
                                (String) object.get(
                                        "body"
                                )
                        )
                );
            }
        }

        return out;
    }

    private NormalizedTxn toTransaction(
            ParsedTxn p) {

        Category category;

        /*
         * MICRO:
         * Debit transaction <= Rs.100
         * and merchant contains UPI.
         */
        if (p.direction() == Direction.DEBIT
                && p.amount().compareTo(
                MICRO_LIMIT
        ) <= 0
                && p.merchant() != null
                && p.merchant()
                .toUpperCase()
                .contains("UPI")) {

            category = Category.MICRO;

        } else if (p.direction() == Direction.DEBIT) {

            category = Category.SPEND;

        } else {

            category = Category.INCOME;
        }

        return new NormalizedTxn(
                p.accountLast4(),
                p.occurredAt(),
                p.direction(),
                p.amount().setScale(2),
                category,
                p.merchant(),
                List.of(
                        p.sourceMessageId()
                )
        );
    }

    private List<NormalizedTxn> classifyTransfers(
            List<NormalizedTxn> transactions) {

        List<NormalizedTxn> result =
                new ArrayList<>(
                        transactions
                );

        Set<Integer> matched =
                new HashSet<>();

        for (int i = 0;
             i < result.size();
             i++) {

            if (matched.contains(i)) {
                continue;
            }

            NormalizedTxn debit =
                    result.get(i);

            if (debit.direction()
                    != Direction.DEBIT) {
                continue;
            }

            for (int j = 0;
                 j < result.size();
                 j++) {

                if (i == j
                        || matched.contains(j)) {
                    continue;
                }

                NormalizedTxn credit =
                        result.get(j);

                if (credit.direction()
                        != Direction.CREDIT) {
                    continue;
                }

                /*
                 * Transfer must happen between two different accounts.
                 */
                if (debit.accountLast4()
                        .equals(
                                credit.accountLast4()
                        )) {
                    continue;
                }

                /*
                 * Transfer amount must be equal.
                 */
                if (debit.amount()
                        .compareTo(
                                credit.amount()
                        ) != 0) {
                    continue;
                }

                /*
                 * Both sides must look like transfer
                 * transactions.
                 */
                if (!isTransferMerchant(
                        debit.merchant())
                        || !isTransferMerchant(
                        credit.merchant())) {
                    continue;
                }

                /*
                 * Merchant wording must match.
                 */
                if (!sameMerchant(
                        debit.merchant(),
                        credit.merchant())) {
                    continue;
                }

                /*
                 * Transfer pair must be close in time.
                 */
                long minutes =
                        Math.abs(
                                Duration.between(
                                        debit.occurredAt(),
                                        credit.occurredAt()
                                ).toMinutes()
                        );

                if (minutes
                        > TRANSFER_WINDOW_MINUTES) {
                    continue;
                }

                /*
                 * Debit side -> TRANSFER
                 */
                result.set(
                        i,
                        withCategory(
                                debit,
                                Category.TRANSFER
                        )
                );

                /*
                 * Credit side -> TRANSFER
                 */
                result.set(
                        j,
                        withCategory(
                                credit,
                                Category.TRANSFER
                        )
                );

                matched.add(i);
                matched.add(j);

                break;
            }
        }

        return result;
    }

    private boolean sameMerchant(
            String first,
            String second) {

        if (first == null
                || second == null) {
            return false;
        }

        return normalizeMerchant(first)
                .equals(
                        normalizeMerchant(second)
                );
    }

    private boolean isTransferMerchant(
            String merchant) {

        if (merchant == null) {
            return false;
        }

        String value =
                merchant.toUpperCase();

        return value.contains(
                "IMPS/P2A/"
        ) || value.contains(
                "NEFT INWARD"
        );
    }

    private String normalizeMerchant(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .toUpperCase()
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private NormalizedTxn withCategory(
            NormalizedTxn transaction,
            Category category) {

        return new NormalizedTxn(
                transaction.accountLast4(),
                transaction.occurredAt(),
                transaction.direction(),
                transaction.amount(),
                category,
                transaction.merchant(),
                transaction.sourceMessageIds()
        );
    }

    /*
     * Deduplication key.
     *
     * statedBalance is deliberately NOT included.
     */
    private record TransactionKey(
            String accountLast4,
            OffsetDateTime occurredAt,
            Direction direction,
            BigDecimal amount,
            String merchant) {
    }

    public record Stats(
            int messagesRead,
            int transactionsWritten,
            int messagesSkipped) {
    }
}