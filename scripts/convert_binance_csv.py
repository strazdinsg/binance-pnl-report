#!/usr/bin/env python3
"""Convert a new-format Binance Transaction History CSV to the old format
that the PNL tool (no.strazdins.Runner) understands.

Old header: User_ID,UTC_Time,Account,Operation,Coin,Change,Remark
Old time:   YYYY-MM-DD HH:MM:SS

New header: User ID,Time,Account,Operation,Coin,Change,Remark
New time:   YY-MM-DD HH:MM:SS

Usage:
    python scripts/convert_binance_csv.py <input.csv> [output.csv]
"""

import argparse
import csv
import sys
from datetime import datetime
from pathlib import Path

NEW_HEADER = ["User ID", "Time", "Account", "Operation", "Coin", "Change", "Remark"]
OLD_HEADER = ["User_ID", "UTC_Time", "Account", "Operation", "Coin", "Change", "Remark"]


def format_change(value: str) -> str:
    if "." in value:
        int_part, frac_part = value.split(".", 1)
    else:
        int_part, frac_part = value, ""
    if len(frac_part) < 8:
        frac_part = frac_part.ljust(8, "0")
    return f"{int_part}.{frac_part}"


def convert_timestamp(value: str, line_no: int) -> str:
    try:
        dt = datetime.strptime(value, "%y-%m-%d %H:%M:%S")
    except ValueError as e:
        sys.stderr.write(
            f"ERROR: unparseable timestamp on line {line_no}: {value!r} ({e})\n"
        )
        sys.exit(3)
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def convert(input_path: Path, output_path: Path) -> None:
    try:
        infile = input_path.open("r", encoding="utf-8-sig", newline="")
    except OSError as e:
        sys.stderr.write(f"ERROR: cannot open input file {input_path}: {e}\n")
        sys.exit(1)

    with infile, output_path.open("w", encoding="utf-8", newline="") as outfile:
        reader = csv.reader(infile)
        try:
            header = next(reader)
        except StopIteration:
            sys.stderr.write("ERROR: input file is empty\n")
            sys.exit(2)

        if header != NEW_HEADER:
            sys.stderr.write(
                "ERROR: unexpected header.\n"
                f"  expected: {NEW_HEADER}\n"
                f"  actual:   {header}\n"
            )
            sys.exit(2)

        writer = csv.writer(outfile, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writerow(OLD_HEADER)

        row_count = 0
        for line_no, row in enumerate(reader, start=2):
            if len(row) != 7:
                sys.stderr.write(
                    f"ERROR: line {line_no} has {len(row)} columns, expected 7: {row}\n"
                )
                sys.exit(2)

            row[1] = convert_timestamp(row[1], line_no)
            row[5] = format_change(row[5])
            writer.writerow(row)
            row_count += 1

    sys.stderr.write(
        f"Converted {row_count} data rows from {input_path} -> {output_path}\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Convert new-format Binance Transaction History CSV to old format."
    )
    parser.add_argument("input", type=Path, help="Path to new-format CSV file")
    parser.add_argument(
        "output",
        type=Path,
        nargs="?",
        default=None,
        help="Output path (default: <input_stem>.converted.csv next to input)",
    )
    args = parser.parse_args()

    output = args.output
    if output is None:
        output = args.input.with_name(args.input.stem + ".converted.csv")

    convert(args.input, output)


if __name__ == "__main__":
    main()
