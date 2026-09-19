package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.Amounts;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BlastRadiusTest {

    private static final Pattern OLD_AMOUNT =
            Pattern.compile("(?:Rs\\.?|INR)\\s*([0-9,]+\\.[0-9]{2})");

    @Test
    void findAffectedMessages() throws Exception {

        var messages = IngestService.readCorpus(
                Path.of("fixtures/corpus-a.jsonl"));

        int affected = 0;

        for (RawMessage message : messages) {

            if (!"sms".equals(message.channel())) {
                continue;
            }

            if (!"AD-HDFCBK-S".equals(message.sender())) {
                continue;
            }

            String body = message.body();

            Matcher matcher = OLD_AMOUNT.matcher(body);

            if (!matcher.find()) {
                continue;
            }

            BigDecimal oldAmount =
                    new BigDecimal(matcher.group(1).replace(",", ""));

            BigDecimal balance = Amounts.statedBalance(body);

            if (balance != null && oldAmount.compareTo(balance) == 0) {
                affected++;

                System.out.println(
                        message.messageId()
                                + " -> old extracted=" + oldAmount
                                + " | balance=" + balance);
            }
        }

        System.out.println("TOTAL AFFECTED = " + affected);
    }
}