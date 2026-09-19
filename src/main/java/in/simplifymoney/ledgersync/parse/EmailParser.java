package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParser implements MessageParser {

    private static final Pattern TRANSACTION = Pattern.compile(
            "Your account ending (?<acct>\\d{4}) has been "
                    + "(?<dir>debited|credited) with "
                    + "(?:Rs\\.?|INR)\\s*(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)\\.",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MERCHANT = Pattern.compile(
            "Merchant / Remarks:\\s*(?<merchant>.+)");

    private static final Pattern DATE = Pattern.compile(
            "Date:\\s*(?<date>.+)");

    private static final DateTimeFormatter EMAIL_DATE =
            DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss xx",
                    Locale.ENGLISH);

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {

        Matcher transaction = TRANSACTION.matcher(m.body());
        if (!transaction.find()) {
            return Optional.empty();
        }

        Matcher date = DATE.matcher(m.body());
        if (!date.find()) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(
                    date.group("date").trim(),
                    EMAIL_DATE);
        } catch (Exception e) {
            return Optional.empty();
        }

        BigDecimal amount = new BigDecimal(
                transaction.group("amount").replace(",", ""))
                .setScale(2);

        String merchant = "";
        Matcher merchantMatcher = MERCHANT.matcher(m.body());
        if (merchantMatcher.find()) {
            merchant = merchantMatcher.group("merchant").trim();
        }

        Direction direction =
                "debited".equalsIgnoreCase(transaction.group("dir"))
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        return Optional.of(new ParsedTxn(
                transaction.group("acct"),
                occurredAt,
                direction,
                amount,
                merchant,
                null,
                m.messageId()));
    }
}