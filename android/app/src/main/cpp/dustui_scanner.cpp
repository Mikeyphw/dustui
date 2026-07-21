#define _FILE_OFFSET_BITS 64

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <functional>
#include <fnmatch.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>

#include <atomic>
#include <cinttypes>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

namespace {
constexpr std::uint64_t kProgressInterval = 128;

enum class EntryMode : int { All = 0, Directories = 1, Files = 2 };

struct InodeKey {
    dev_t device;
    ino_t inode;
    bool operator==(const InodeKey& other) const { return device == other.device && inode == other.inode; }
};

struct InodeHash {
    std::size_t operator()(const InodeKey& key) const {
        const auto first = std::hash<std::uint64_t>{}(static_cast<std::uint64_t>(key.device));
        const auto second = std::hash<std::uint64_t>{}(static_cast<std::uint64_t>(key.inode));
        return first ^ (second << 1U);
    }
};

struct Totals {
    std::uint64_t bytes = 0;
    std::uint64_t files = 0;
};

struct ScanContext {
    FILE* output;
    dev_t rootDevice;
    int maxDepth;
    bool includeHidden;
    bool apparentSize;
    bool sameFilesystem;
    EntryMode mode;
    std::uint64_t minSize;
    std::uint64_t maxEntries;
    std::vector<std::string> excludes;
    std::shared_ptr<std::atomic_bool> cancelled;
    std::uint64_t scannedItems = 0;
    std::uint64_t scannedBytes = 0;
    std::uint64_t emittedEntries = 0;
    std::uint64_t warningCount = 0;
    std::unordered_set<InodeKey, InodeHash> seenInodes;
};

std::mutex gCancelMutex;
std::unordered_map<std::string, std::shared_ptr<std::atomic_bool>> gCancelFlags;

void writeJsonString(FILE* output, const std::string& value) {
    std::fputc('"', output);
    for (const unsigned char valueByte : value) {
        switch (valueByte) {
            case '"': std::fputs("\\\"", output); break;
            case '\\': std::fputs("\\\\", output); break;
            case '\b': std::fputs("\\b", output); break;
            case '\f': std::fputs("\\f", output); break;
            case '\n': std::fputs("\\n", output); break;
            case '\r': std::fputs("\\r", output); break;
            case '\t': std::fputs("\\t", output); break;
            default:
                if (valueByte < 0x20U) {
                    std::fprintf(output, "\\u%04x", valueByte);
                } else {
                    std::fputc(valueByte, output);
                }
        }
    }
    std::fputc('"', output);
}

void finishEvent(FILE* output) {
    std::fputc('\n', output);
    std::fflush(output);
}

std::string baseName(const std::string& path) {
    if (path == "/") return "/";
    const auto end = path.find_last_not_of('/');
    if (end == std::string::npos) return path;
    const auto separator = path.find_last_of('/', end);
    return separator == std::string::npos ? path.substr(0, end + 1) : path.substr(separator + 1, end - separator);
}

std::string parentPath(const std::string& path) {
    if (path == "/") return "/";
    const auto end = path.find_last_not_of('/');
    if (end == std::string::npos) return "/";
    const auto separator = path.find_last_of('/', end);
    if (separator == std::string::npos) return ".";
    return separator == 0 ? "/" : path.substr(0, separator);
}

std::string joinPath(const std::string& parent, const char* child) {
    if (parent.empty() || parent == "/") return parent == "/" ? "/" + std::string(child) : std::string(child);
    return parent + "/" + child;
}

std::uint64_t sizeFor(const struct stat& metadata, bool apparentSize) {
    if (apparentSize) return metadata.st_size > 0 ? static_cast<std::uint64_t>(metadata.st_size) : 0;
    if (metadata.st_blocks > 0) return static_cast<std::uint64_t>(metadata.st_blocks) * 512ULL;
    return metadata.st_size > 0 ? static_cast<std::uint64_t>(metadata.st_size) : 0;
}

bool excluded(const ScanContext& context, const std::string& path, const std::string& name) {
    for (const auto& pattern : context.excludes) {
        if (fnmatch(pattern.c_str(), name.c_str(), 0) == 0 || fnmatch(pattern.c_str(), path.c_str(), FNM_PATHNAME) == 0) {
            return true;
        }
    }
    return false;
}

void emitWarning(ScanContext& context, const std::string& path, const std::string& message) {
    ++context.warningCount;
    std::fputs("{\"type\":\"warning\",\"path\":", context.output);
    writeJsonString(context.output, path);
    std::fputs(",\"message\":", context.output);
    writeJsonString(context.output, message);
    std::fputc('}', context.output);
    finishEvent(context.output);
}

void emitProgress(ScanContext& context, const std::string& path) {
    std::fprintf(context.output,
        "{\"type\":\"progress\",\"scannedItems\":%" PRIu64 ",\"scannedBytes\":%" PRIu64 ",\"currentPath\":",
        context.scannedItems, context.scannedBytes);
    writeJsonString(context.output, path);
    std::fputc('}', context.output);
    finishEvent(context.output);
}

bool shouldEmit(const ScanContext& context, bool directory, int depth, std::uint64_t size) {
    if (depth == 0 || size < context.minSize || context.emittedEntries >= context.maxEntries) return false;
    if (context.mode == EntryMode::Directories && !directory) return false;
    if (context.mode == EntryMode::Files && directory) return false;
    return true;
}

void emitEntry(ScanContext& context, const std::string& path, const struct stat& metadata,
               bool directory, int depth, const Totals& totals) {
    std::fputs("{\"type\":\"entry\",\"path\":", context.output);
    writeJsonString(context.output, path);
    std::fputs(",\"parentPath\":", context.output);
    writeJsonString(context.output, parentPath(path));
    std::fputs(",\"name\":", context.output);
    writeJsonString(context.output, baseName(path));
    std::fprintf(context.output,
        ",\"sizeBytes\":%" PRIu64 ",\"allocatedBytes\":%" PRIu64
        ",\"directory\":%s,\"depth\":%d,\"modifiedAt\":%" PRId64 ",\"fileCount\":%" PRIu64 "}",
        totals.bytes, sizeFor(metadata, false), directory ? "true" : "false", depth,
        static_cast<std::int64_t>(metadata.st_mtime), totals.files);
    finishEvent(context.output);
    ++context.emittedEntries;
}

Totals scanPath(ScanContext& context, const std::string& path, int depth) {
    if (context.cancelled->load(std::memory_order_relaxed)) return {};

    struct stat metadata {};
    if (lstat(path.c_str(), &metadata) != 0) {
        emitWarning(context, path, std::strerror(errno));
        return {};
    }
    if (context.sameFilesystem && metadata.st_dev != context.rootDevice) return {};

    ++context.scannedItems;
    const bool directory = S_ISDIR(metadata.st_mode);
    Totals totals { sizeFor(metadata, context.apparentSize), directory ? 0ULL : 1ULL };

    if (!directory && metadata.st_nlink > 1) {
        if (!context.seenInodes.insert({metadata.st_dev, metadata.st_ino}).second) {
            totals = {};
        }
    }

    const bool mayDescend = directory && (context.maxDepth < 0 || depth < context.maxDepth);
    if (mayDescend) {
        DIR* handle = opendir(path.c_str());
        if (handle == nullptr) {
            emitWarning(context, path, std::strerror(errno));
        } else {
            while (!context.cancelled->load(std::memory_order_relaxed)) {
                errno = 0;
                dirent* entry = readdir(handle);
                if (entry == nullptr) {
                    if (errno != 0) emitWarning(context, path, std::strerror(errno));
                    break;
                }
                const char* name = entry->d_name;
                if (std::strcmp(name, ".") == 0 || std::strcmp(name, "..") == 0) continue;
                if (!context.includeHidden && name[0] == '.') continue;
                const std::string child = joinPath(path, name);
                if (excluded(context, child, name)) continue;
                const Totals childTotals = scanPath(context, child, depth + 1);
                totals.bytes += childTotals.bytes;
                totals.files += childTotals.files;
            }
            closedir(handle);
        }
    }

    if (!directory) context.scannedBytes += totals.bytes;
    if (shouldEmit(context, directory, depth, totals.bytes)) emitEntry(context, path, metadata, directory, depth, totals);
    if (context.scannedItems % kProgressInterval == 0) emitProgress(context, path);
    return totals;
}

std::vector<std::string> stringsFromJava(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> result;
    if (values == nullptr) return result;
    const jsize count = env->GetArrayLength(values);
    result.reserve(static_cast<std::size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        if (value == nullptr) continue;
        const char* chars = env->GetStringUTFChars(value, nullptr);
        if (chars != nullptr) {
            result.emplace_back(chars);
            env->ReleaseStringUTFChars(value, chars);
        }
        env->DeleteLocalRef(value);
    }
    return result;
}

std::string javaString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_scan(
    JNIEnv* env, jobject, jstring scanIdValue, jstring rootValue, jint outputFd,
    jint maxDepth, jboolean includeHidden, jboolean apparentSize, jboolean sameFilesystem,
    jint mode, jlong minSize, jlong maxEntries, jobjectArray excludesValue) {
    const std::string scanId = javaString(env, scanIdValue);
    const std::string root = javaString(env, rootValue);
    if (scanId.empty() || root.empty() || outputFd < 0) {
        if (outputFd >= 0) close(outputFd);
        return 2;
    }

    FILE* output = fdopen(outputFd, "w");
    if (output == nullptr) {
        close(outputFd);
        return 3;
    }
    setvbuf(output, nullptr, _IOLBF, 0);

    auto cancelled = std::make_shared<std::atomic_bool>(false);
    {
        std::lock_guard<std::mutex> guard(gCancelMutex);
        gCancelFlags[scanId] = cancelled;
    }

    struct stat rootMetadata {};
    if (lstat(root.c_str(), &rootMetadata) != 0) {
        std::fputs("{\"type\":\"error\",\"message\":", output);
        writeJsonString(output, std::strerror(errno));
        std::fputc('}', output);
        finishEvent(output);
        std::fclose(output);
        std::lock_guard<std::mutex> guard(gCancelMutex);
        gCancelFlags.erase(scanId);
        return 4;
    }

    ScanContext context {
        output,
        rootMetadata.st_dev,
        maxDepth,
        includeHidden == JNI_TRUE,
        apparentSize == JNI_TRUE,
        sameFilesystem == JNI_TRUE,
        static_cast<EntryMode>(mode),
        minSize < 0 ? 0ULL : static_cast<std::uint64_t>(minSize),
        maxEntries <= 0 ? UINT64_MAX : static_cast<std::uint64_t>(maxEntries),
        stringsFromJava(env, excludesValue),
        cancelled,
        0,
        0,
        0,
        0,
        {},
    };

    std::fputs("{\"type\":\"start\",\"backend\":\"dustui-native-root-v2\",\"root\":", output);
    writeJsonString(output, root);
    std::fputc('}', output);
    finishEvent(output);

    const Totals totals = scanPath(context, root, 0);
    const bool wasCancelled = cancelled->load(std::memory_order_relaxed);
    if (wasCancelled) {
        std::fprintf(output,
            "{\"type\":\"cancelled\",\"scannedItems\":%" PRIu64 ",\"scannedBytes\":%" PRIu64 "}",
            context.scannedItems, context.scannedBytes);
    } else {
        std::fprintf(output,
            "{\"type\":\"complete\",\"totalBytes\":%" PRIu64 ",\"scannedItems\":%" PRIu64
            ",\"scannedBytes\":%" PRIu64 ",\"warnings\":%" PRIu64 ",\"emittedEntries\":%" PRIu64 "}",
            totals.bytes, context.scannedItems, context.scannedBytes, context.warningCount, context.emittedEntries);
    }
    finishEvent(output);
    std::fclose(output);

    {
        std::lock_guard<std::mutex> guard(gCancelMutex);
        gCancelFlags.erase(scanId);
    }
    return wasCancelled ? 130 : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_cancel(JNIEnv* env, jobject, jstring scanIdValue) {
    const std::string scanId = javaString(env, scanIdValue);
    std::lock_guard<std::mutex> guard(gCancelMutex);
    const auto found = gCancelFlags.find(scanId);
    if (found == gCancelFlags.end()) return JNI_FALSE;
    found->second->store(true, std::memory_order_relaxed);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_version(JNIEnv* env, jobject) {
    return env->NewStringUTF("dustui-native-root/2");
}
