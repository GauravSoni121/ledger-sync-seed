package in.simplifymoney.ledgersync.store;


import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MongoDocumentStore implements DocumentStore, AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoDocumentStore(String uri) {
        this.client = MongoClients.create(uri);

        var database = client.getDatabase("ledger_sync");
        this.collection = database.getCollection("transactions");

        createIndexes();
    }

    private void createIndexes() {
        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("account_last4"),
                        Indexes.ascending("month"),
                        Indexes.descending("occurred_at")
                )
        );

        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("account_last4"),
                        Indexes.ascending("category")
                )
        );

        collection.createIndex(
                Indexes.ascending("source_message_ids")
        );
    }

    @Override
    public void save(NormalizedTxn txn) {
        Document document = toDocument(txn);

        collection.updateOne(
                Filters.and(
                        Filters.eq("account_last4", txn.accountLast4()),
                        Filters.eq("occurred_at", txn.occurredAt().toString()),
                        Filters.eq("direction", txn.direction().name()),
                        Filters.eq("amount", txn.amount().setScale(2).toPlainString())
                ),
                new Document("$set", document),
                new UpdateOptions().upsert(true)
        );
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(
            String accountLast4,
            YearMonth month) {

        String monthKey = month.toString();

        List<NormalizedTxn> result = new ArrayList<>();

        try (var cursor = collection.find(
                        Filters.and(
                                Filters.eq("account_last4", accountLast4),
                                Filters.eq("month", monthKey)
                        ))
                .sort(Sorts.descending("occurred_at"))
                .iterator()) {

            while (cursor.hasNext()) {
                result.add(fromDocument(cursor.next()));
            }
        }

        return result;
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {

        Map<Category, BigDecimal> totals =
                new EnumMap<>(Category.class);

        for (Category category : Category.values()) {
            totals.put(category, BigDecimal.ZERO.setScale(2));
        }

        try (var cursor = collection.find(
                Filters.eq("account_last4", accountLast4)).iterator()) {

            while (cursor.hasNext()) {
                Document document = cursor.next();

                Category category =
                        Category.valueOf(document.getString("category"));

                BigDecimal amount =
                        new BigDecimal(document.getString("amount"));

                totals.put(
                        category,
                        totals.get(category).add(amount)
                );
            }
        }

        return totals;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {

        Document document = collection.find(
                Filters.eq("source_message_ids", messageId)
        ).first();

        if (document == null) {
            return Optional.empty();
        }

        return Optional.of(fromDocument(document));
    }

    private Document toDocument(NormalizedTxn txn) {

        return new Document()
                .append("account_last4", txn.accountLast4())
                .append("occurred_at", txn.occurredAt().toString())
                .append("month",
                        YearMonth.from(txn.occurredAt()).toString())
                .append("direction", txn.direction().name())
                .append("amount",
                        txn.amount().setScale(2).toPlainString())
                .append("category", txn.category().name())
                .append("merchant", txn.merchant())
                .append("source_message_ids",
                        txn.sourceMessageIds());
    }

    @SuppressWarnings("unchecked")
    private NormalizedTxn fromDocument(Document document) {

        List<String> sourceMessageIds =
                document.getList(
                        "source_message_ids",
                        String.class
                );

        return new NormalizedTxn(
                document.getString("account_last4"),
                OffsetDateTime.parse(
                        document.getString("occurred_at")
                ),
                Direction.valueOf(
                        document.getString("direction")
                ),
                new BigDecimal(
                        document.getString("amount")
                ).setScale(2),
                Category.valueOf(
                        document.getString("category")
                ),
                document.getString("merchant"),
                sourceMessageIds
        );
    }

    @Override
    public void close() {
        client.close();
    }
}