package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HDFC Bank SMS parser.
 */
public final class HdfcSmsParser implements MessageParser {

    public static final String SENDER = "AD-HDFCBK-S";

    private static final Pattern V1 = Pattern.compile(
            "(?<dir>debited from|credited to) a/c \\*\\*(?<acct>\\d{4}) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} at \\d{2}:\\d{2}) "
                    + "(?:to|by) (?<merchant>[^.]+)\\.",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern V2 = Pattern.compile(
            "^(?<dir>Sent|Received)\\s+(?:Rs\\.?|INR)\\s*"
                    + "(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?).*?\\n"
                    + "(?:To|From): (?<merchant>.+?)\\n"
                    + "On: (?<when>\\d{2} \\w{3} \\d{2} \\d{2}:\\d{2})\\n"
                    + "A/c: XX(?<acct>\\d{4})",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern CARD = Pattern.compile(
            "spent on HDFC Bank Card x(?<acct>\\d{4}) at (?<merchant>.+?) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\.",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern V1_AMOUNT = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CARD_AMOUNT = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        String body = m.body();

        Matcher v1 = V1.matcher(body);
        if (v1.find()) {
            Direction direction =
                    v1.group("dir").toLowerCase().startsWith("debited")
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            BigDecimal amount = extractAmountBeforeBalance(body);

            return build(
                    m,
                    v1.group("acct"),
                    v1.group("when").replace(" at ", " "),
                    direction,
                    v1.group("merchant"),
                    amount);
        }

        Matcher v2 = V2.matcher(body);
        if (v2.find()) {
            Direction direction =
                    "Sent".equalsIgnoreCase(v2.group("dir"))
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            BigDecimal amount = new BigDecimal(
                    v2.group("amount").replace(",", ""))
                    .setScale(2);

            return build(
                    m,
                    v2.group("acct"),
                    v2.group("when"),
                    direction,
                    v2.group("merchant"),
                    amount);
        }

        Matcher card = CARD.matcher(body);
        if (card.find()) {
            BigDecimal amount = firstCardAmount(body);

            return build(
                    m,
                    card.group("acct"),
                    card.group("when"),
                    Direction.DEBIT,
                    card.group("merchant"),
                    amount);
        }

        return Optional.empty();
    }

    private Optional<ParsedTxn> build(
            RawMessage m,
            String acct,
            String when,
            Direction direction,
            String merchant,
            BigDecimal amount) {

        OffsetDateTime occurredAt = Dates.ist(when);

        if (amount == null || occurredAt == null) {
            return Optional.empty();
        }

        return Optional.of(new ParsedTxn(
                acct,
                occurredAt,
                direction,
                amount,
                merchant.trim(),
                Amounts.statedBalance(m.body()),
                m.messageId()));
    }

    private BigDecimal extractAmountBeforeBalance(String body) {
        int balanceIndex = body.toLowerCase().indexOf("avl bal");

        String transactionPart = balanceIndex >= 0
                ? body.substring(0, balanceIndex)
                : body;

        Matcher matcher = V1_AMOUNT.matcher(transactionPart);

        if (!matcher.find()) {
            return null;
        }

        return new BigDecimal(
                matcher.group(1).replace(",", ""))
                .setScale(2);
    }

    private BigDecimal firstCardAmount(String body) {
        Matcher matcher = CARD_AMOUNT.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return new BigDecimal(
                matcher.group(1).replace(",", ""))
                .setScale(2);
    }
}