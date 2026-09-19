const db = db.getSiblingDB("ledger_sync");

function findMetric(obj, key) {
    if (obj === null || obj === undefined) return null;

    if (typeof obj === "object") {
        if (obj[key] !== undefined) return obj[key];

        for (const k of Object.keys(obj)) {
            const value = findMetric(obj[k], key);
            if (value !== null && value !== undefined) {
                return value;
            }
        }
    }

    return null;
}

print("========================================");
print("MONGODB BENCHMARK - 100,000 TRANSACTIONS");
print("========================================");

print("\nDOCUMENT COUNT");
print("count = " + db.transactions.countDocuments());

/* Q1 */
print("\nQ1: ACCOUNT + MONTH");

const q1 = db.runCommand({
    explain: {
        find: "transactions",
        filter: {
            account_last4: "9075",
            month: "2026-02"
        },
        sort: {
            occurred_at: -1
        }
    },
    verbosity: "executionStats"
});

print("examined = " + findMetric(q1, "totalDocsExamined"));
print("returned = " + findMetric(q1, "nReturned"));

/* Q2 */
print("\nQ2: CATEGORY TOTALS");

const q2 = db.runCommand({
    explain: {
        aggregate: "transactions",
        pipeline: [
            {
                $match: {
                    account_last4: "9075"
                }
            },
            {
                $group: {
                    _id: "$category",
                    total: {
                        $sum: {
                            $toDouble: "$amount"
                        }
                    }
                }
            }
        ],
        cursor: {}
    },
    verbosity: "executionStats"
});

print("examined = " + findMetric(q2, "totalDocsExamined"));
print("returned = " + findMetric(q2, "nReturned"));

/* Q3 */
print("\nQ3: MESSAGE ID");

const q3 = db.runCommand({
    explain: {
        find: "transactions",
        filter: {
            source_message_ids: "test-message-50000"
        }
    },
    verbosity: "executionStats"
});

print("examined = " + findMetric(q3, "totalDocsExamined"));
print("returned = " + findMetric(q3, "nReturned"));

print("\n========================================");
print("BENCHMARK COMPLETE");
print("========================================");
