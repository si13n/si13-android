#!/usr/bin/env python3

from pathlib import Path
import os
import sys
import xml.etree.ElementTree as ET


REPORTS_DIR = Path("app/build/outputs/androidTest-results/connected")


def attr_int(element: ET.Element, name: str) -> int:
    try:
        return int(element.attrib.get(name, "0"))
    except ValueError:
        return 0


def esc(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", "<br>")


def main() -> int:
    xml_files = sorted(REPORTS_DIR.glob("**/*.xml"))
    lines = ["## Espresso Test Summary", ""]

    if not xml_files:
        lines.extend(
            [
                "No Android JUnit XML files were found.",
                "",
                f"Expected path: `{REPORTS_DIR}/**/*.xml`",
                "",
            ]
        )
        write_summary(lines)
        return 0

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    failed_cases = []

    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:
            failed_cases.append((str(xml_file), "XML parse error", str(exc)))
            continue

        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            totals["tests"] += attr_int(suite, "tests")
            totals["failures"] += attr_int(suite, "failures")
            totals["errors"] += attr_int(suite, "errors")
            totals["skipped"] += attr_int(suite, "skipped")

            for case in suite.findall("testcase"):
                problem = case.find("failure")
                if problem is None:
                    problem = case.find("error")
                if problem is None:
                    continue
                case_name = f"{case.attrib.get('classname', '')}.{case.attrib.get('name', '')}".strip(".")
                message = problem.attrib.get("message") or (problem.text or "").strip().splitlines()[0:1]
                if isinstance(message, list):
                    message = message[0] if message else ""
                failed_cases.append((case_name, problem.tag, message))

    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    lines.extend(
        [
            "| Tests | Passed | Failed | Errors | Skipped |",
            "| ---: | ---: | ---: | ---: | ---: |",
            f"| {totals['tests']} | {passed} | {totals['failures']} | {totals['errors']} | {totals['skipped']} |",
            "",
        ]
    )

    if failed_cases:
        lines.extend(["### Failed Tests", "", "| Test | Type | Message |", "| --- | --- | --- |"])
        for case_name, problem_type, message in failed_cases:
            lines.append(f"| `{esc(case_name)}` | {esc(problem_type)} | {esc(str(message))} |")
        lines.append("")
    else:
        lines.extend(["All Espresso tests passed.", ""])

    lines.extend(["### JUnit XML", ""])
    for xml_file in xml_files:
        lines.append(f"- `{xml_file}`")
    lines.append("")

    write_summary(lines)
    return 0


def write_summary(lines: list[str]) -> None:
    output = "\n".join(lines)
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(output)
    else:
        sys.stdout.write(output)


if __name__ == "__main__":
    raise SystemExit(main())
