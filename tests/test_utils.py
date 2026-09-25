from dust_tui.utils import format_size, load_json_documents, make_usage_bar, parse_size


def test_parse_size_decimal_and_binary() -> None:
    assert parse_size("2GB") == 2_000_000_000
    assert parse_size("2 GiB") == 2 * 1024**3
    assert parse_size("512") == 512


def test_format_size() -> None:
    assert format_size(999) == "999 B"
    assert format_size(1_500_000).endswith("MB")


def test_make_usage_bar_ascii_fallback() -> None:
    assert make_usage_bar(0.5, 4, ascii_only=True) == "##.."


def test_json_stream_parser() -> None:
    assert load_json_documents('{"a": 1}\n{"b": 2}') == [{"a": 1}, {"b": 2}]
