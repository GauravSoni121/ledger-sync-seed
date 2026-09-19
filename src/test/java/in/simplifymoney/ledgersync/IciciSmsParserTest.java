package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.IciciSmsParser;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IciciSmsParserTest {

    @Test
    void parsesIciciV2Format() {

        String body =
                "ICICI Bank Acct XX9075 Cr INR 18000.00 on "
                        + "01-Aug-2026 21:14; NEFT INWARD SELF "
                        + "ref no 896005189492. BalAvl Rs 65,352.47";

        RawMessage message = new RawMessage(
                "icici-v2-test",
                "sms",
                "VM-ICICIB-T",
                OffsetDateTime.parse("2026-08-01T21:14:00+05:30"),
                "test-device",
                body
        );

        Optional<ParsedTxn> result =
                new IciciSmsParser().parse(message);

        assertTrue(result.isPresent());

        ParsedTxn txn = result.get();

        assertEquals("9075", txn.accountLast4());
        assertEquals(Direction.CREDIT, txn.direction());
        assertEquals("18000.00", txn.amount().toPlainString());
        assertEquals("icici-v2-test", txn.sourceMessageId());
    }
}