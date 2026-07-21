#include <cassert>
#include <filesystem>
#include <fstream>
#include <memory>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

#include "../main/cpp/dustui_scanner.cpp"

namespace fs = std::filesystem;

static ScanContext contextFor(FILE* output, const fs::path& root, int depth, bool hidden,
                              std::vector<std::string> excludes = {}) {
    struct stat metadata {};
    assert(lstat(root.c_str(), &metadata) == 0);
    return ScanContext {
        output,
        metadata.st_dev,
        depth,
        hidden,
        true,
        true,
        EntryMode::All,
        0,
        10'000,
        std::move(excludes),
        std::make_shared<std::atomic_bool>(false),
        0,
        0,
        0,
        0,
        {},
    };
}

int main() {
    const fs::path root = fs::temp_directory_path() / ("dustui-native-test-" + std::to_string(getpid()));
    fs::remove_all(root);
    fs::create_directories(root / "a" / "b");
    fs::create_directories(root / ".hidden");
    std::ofstream(root / "a" / "b" / "payload.bin") << "0123456789";
    fs::create_hard_link(root / "a" / "b" / "payload.bin", root / "a" / "b" / "payload.link");
    std::ofstream(root / ".hidden" / "tiny") << "abc";
    std::ofstream(root / "skip.tmp") << "ignored";

    FILE* output = tmpfile();
    assert(output != nullptr);

    auto shallow = contextFor(output, root, 1, false, {"*.tmp"});
    const Totals shallowTotals = scanPath(shallow, root.string(), 0);
    assert(shallow.scannedItems == 2);  // root and a; excluded siblings do not muddy the depth contract.
    assert(shallowTotals.files == 0);

    auto complete = contextFor(output, root, -1, true, {"*.tmp"});
    complete.sameFilesystem = false;  // OverlayFS may report distinct devices for files and directories.
    const Totals completeTotals = scanPath(complete, root.string(), 0);
    assert(completeTotals.files == 2);  // hard link is deduplicated; excluded file is absent.
    assert(complete.scannedBytes == 13);
    assert(complete.warningCount == 0);

    auto cancelled = contextFor(output, root, -1, true);
    cancelled.cancelled->store(true);
    const Totals cancelledTotals = scanPath(cancelled, root.string(), 0);
    assert(cancelled.scannedItems == 0);
    assert(cancelledTotals.bytes == 0);

    fclose(output);
    fs::remove_all(root);
    return 0;
}
