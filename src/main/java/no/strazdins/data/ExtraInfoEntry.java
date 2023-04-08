package no.strazdins.data;

/**
 * Represents one unit of extra information for a financial transaction, provided by the user.
 *
 * @param utcTimestamp UTC timestamp, including milliseconds.
 * @param type         The type of the information
 * @param val          A numeric value, formatted as a decimal-string. The meaning of the val
 *                     depends on the type. For example: the exchange rate.
 */
public record ExtraInfoEntry(long utcTimestamp, ExtraInfoType type, String val) {
}
