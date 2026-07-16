from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "docs" / "UI_UX_CONTRACT.md"
README = ROOT / "README.md"
STYLES = ROOT / "src" / "dust_tui" / "styles.tcss"


def test_ui_ux_contract_is_authoritative_and_linked() -> None:
    contract = CONTRACT.read_text(encoding="utf-8")
    readme = README.read_text(encoding="utf-8")
    assert "This document is authoritative" in contract
    assert "40x20" in contract
    assert "60x24" in contract
    assert "Termux" in contract
    assert "ASCII fallback" in contract
    assert "docs/UI_UX_CONTRACT.md" in readme


def test_compact_css_contract() -> None:
    styles = STYLES.read_text(encoding="utf-8")
    assert "#results-pane" in styles
    assert "min-width: 0;" in styles
    assert "min-width: 50" not in styles
    assert "#details-button" in styles
    assert "#dialog.details-dialog" in styles
    assert "#details-dialog-body" in styles
