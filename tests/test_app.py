import inspect
from pathlib import Path

from textual.screen import ModalScreen

import dust_tui.screens as screens_module
from dust_tui.app import DustTuiApp
from dust_tui.config import AppConfig
from dust_tui.screens import ConfirmTrashScreen, DirectoryPicker, EntryDetailsScreen, HelpScreen, SettingsScreen

COMPACT_SIZES = ((40, 20), (50, 20), (60, 24), (80, 24))
REGULAR_SIZES = ((95, 34), (120, 40))


def assert_region_fits_app(app: DustTuiApp, selector: str) -> None:
    widget = app.query_one(selector)
    if widget.display is False:
        return
    region = widget.region
    assert region.x >= 0
    assert region.y >= 0
    assert region.x + region.width <= app.size.width
    assert region.y + region.height <= app.size.height


async def test_app_starts_without_scan(tmp_path: Path) -> None:
    app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
    async with app.run_test(size=(120, 40)) as pilot:
        await pilot.pause()
        assert app.query_one("#path-input").value == str(tmp_path)
        assert len(app.query_one("#results-table").columns) == 5


async def test_app_compact_layout_stays_within_viewport(tmp_path: Path) -> None:
    for size in COMPACT_SIZES:
        app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
        async with app.run_test(size=size) as pilot:
            await pilot.pause()
            for selector in (
                "#scan-toolbar",
                "#summary-row",
                "#content-row",
                "#results-pane",
                "#filter-row",
                "#results-table",
                "#status-line",
            ):
                assert_region_fits_app(app, selector)
            assert app.query_one("#details-pane").display is False
            assert app.query_one("#usage-chart").display is False
            assert app.query_one("#details-button").disabled is True


async def test_app_regular_layout_keeps_details_and_chart(tmp_path: Path) -> None:
    for size in REGULAR_SIZES:
        app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
        async with app.run_test(size=size) as pilot:
            await pilot.pause()
            for selector in ("#details-pane", "#usage-chart", "#results-pane", "#results-table"):
                assert_region_fits_app(app, selector)
            assert app.query_one("#details-pane").display is True
            assert app.query_one("#usage-chart").display is True


async def test_native_scan_populates_table(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("XDG_CONFIG_HOME", str(tmp_path / "config"))
    (tmp_path / "large.bin").write_bytes(b"x" * 4096)
    app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
    app.config.backend = "native"
    app.config.apparent_size = True
    app.config.excludes = ""
    async with app.run_test(size=(120, 40)) as pilot:
        app.action_start_scan()
        for _ in range(100):
            await pilot.pause(0.02)
            if app.result is not None:
                break
        assert app.result is not None
        assert app.result.backend == "native"
        assert app.query_one("#results-table").row_count >= 1


async def test_settings_screen_exposes_dust_flags(tmp_path: Path) -> None:
    app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
    async with app.run_test(size=(120, 50)) as pilot:
        app.action_settings()
        await pilot.pause()
        for selector in (
            "#settings-backend",
            "#settings-ascii-bars",
            "#settings-follow-symlinks",
            "#settings-dust-config",
            "#settings-dust-threads",
            "#settings-dust-output-format",
            "#settings-dust-terminal-width",
            "#settings-dust-stack-size",
            "#settings-dust-file-types",
            "#settings-dust-filter",
            "#settings-dust-invert-filter",
            "#settings-dust-ignore-file",
            "#settings-dust-mtime",
            "#settings-dust-atime",
            "#settings-dust-ctime",
            "#settings-dust-files-from",
            "#settings-dust-files0-from",
            "#settings-dust-reverse",
            "#settings-dust-no-colors",
            "#settings-dust-force-colors",
            "#settings-dust-no-bars",
            "#settings-dust-bars-right",
            "#settings-dust-screen-reader",
            "#settings-dust-skip-total",
            "#settings-dust-filecount",
            "#settings-dust-print-errors",
        ):
            assert app.screen.query_one(selector)


async def test_all_modal_screens_fit_contract_sizes(tmp_path: Path) -> None:
    file_path = tmp_path / "file.bin"
    file_path.write_bytes(b"x")
    runtime_factories = {
        ConfirmTrashScreen: lambda: ConfirmTrashScreen(file_path, "1 B"),
        EntryDetailsScreen: lambda: EntryDetailsScreen("file.bin", "[dim]Path[/dim]\n/tmp/file.bin"),
        HelpScreen: HelpScreen,
        SettingsScreen: lambda: SettingsScreen(AppConfig(last_path=str(tmp_path))),
    }
    known_modal_screens = {
        ConfirmTrashScreen,
        DirectoryPicker,
        EntryDetailsScreen,
        HelpScreen,
        SettingsScreen,
    }
    discovered_modal_screens = {
        candidate
        for _, candidate in inspect.getmembers(screens_module, inspect.isclass)
        if issubclass(candidate, ModalScreen) and candidate is not ModalScreen
    }
    assert discovered_modal_screens == known_modal_screens
    assert set(runtime_factories) == known_modal_screens - {DirectoryPicker}
    directory_picker_source = inspect.getsource(DirectoryPicker.compose)
    assert 'id="dialog"' in directory_picker_source
    assert 'id="directory-tree"' in directory_picker_source

    for size in ((40, 20), (60, 24), (80, 24)):
        for factory in runtime_factories.values():
            app = DustTuiApp(initial_path=tmp_path, auto_scan=False)
            async with app.run_test(size=size) as pilot:
                app.push_screen(factory())
                await pilot.pause()
                dialog = app.screen.query_one("#dialog")
                region = dialog.region
                assert region.x >= 0
                assert region.y >= 0
                assert region.x + region.width <= app.size.width
                assert region.y + region.height <= app.size.height
                app.pop_screen()
                await pilot.pause()
