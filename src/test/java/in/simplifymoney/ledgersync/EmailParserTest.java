package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.EmailParser;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmailParserTest {

    @Test
    void parsesCreditEmail() {

        String body =
                "Your account ending 4821 has been credited with "
                        + "INR 2500.00.\n"
                        + "Date: Sat, 15 Aug 2026 10:30:00 +0530\n"
                        + "Merchant / Remarks: Salary Credit";

        RawMessage message = new RawMessage(
                "email-test-1",
                "email",
                "test@example.com",
                OffsetDateTime.parse("2026-08-15T10:30:00+05:30"),
                "test-device",
                body
        );

        Optional<ParsedTxn> result =
                new EmailParser().parse(message);

        assertTrue(result.isPresent());

        ParsedTxn txn = result.get();

        assertEquals("4821", txn.accountLast4());
        assertEquals(Direction.CREDIT, txn.direction());
        assertEquals("2500.00", txn.amount().toPlainString());
        assertEquals("Salary Credit", txn.merchant());
        assertEquals("email-test-1", txn.sourceMessageId());
    }
}