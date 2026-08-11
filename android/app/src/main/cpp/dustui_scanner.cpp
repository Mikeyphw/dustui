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
#include <sstream>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

namespace {
constexpr std::uint64_t kProgressInterval = 128;
constexpr std::size_t kOutputBufferBytes = 256 * 1024;

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
    std::uint64_t emittedFiles = 0;
    std::uint64_t emittedDirectories = 0;
    std::uint64_t warningCount = 0;
    std::uint64_t depthLimitedDirectories = 0;
    std::uint64_t skippedFilesystemEntries = 0;
    bool truncated = false;
    bool rootOpened = false;
    bool fatal = false;
    std::unordered_set<InodeKey, InodeHash> seenInodes;
};

std::mutex gCancelMutex;
std::unordered_map<std::string, std::shared_ptr<std::atomic_bool>> gCancelFlags;

std::shared_ptr<std::atomic_bool> registerCancellationFlag(const std::string& operationId) {
    auto cancelled = std::make_shared<std::atomic_bool>(false);
    if (operationId.empty()) return cancelled;
    std::lock_guard<std::mutex> guard(gCancelMutex);
    gCancelFlags[operationId] = cancelled;
    return cancelled;
}

void unregisterCancellationFlag(const std::string& operationId, const std::shared_ptr<std::atomic_bool>& expected) {
    if (operationId.empty()) return;
    std::lock_guard<std::mutex> guard(gCancelMutex);
    const auto found = gCancelFlags.find(operationId);
    if (found != gCancelFlags.end() && found->second == expected) gCancelFlags.erase(found);
}

bool requestCancellationFlag(const std::string& operationId) {
    std::lock_guard<std::mutex> guard(gCancelMutex);
    const auto found = gCancelFlags.find(operationId);
    if (found == gCancelFlags.end()) return false;
    found->second->store(true, std::memory_order_relaxed);
    return true;
}


std::string jsonEscape(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size() + 8);
    for (const unsigned char valueByte : value) {
        switch (valueByte) {
            case '"': escaped += "\\\""; break;
            case '\\': escaped += "\\\\"; break;
            case '\b': escaped += "\\b"; break;
            case '\f': escaped += "\\f"; break;
            case '\n': escaped += "\\n"; break;
            case '\r': escaped += "\\r"; break;
            case '\t': escaped += "\\t"; break;
            default:
                if (valueByte < 0x20U) {
                    char buffer[7];
                    std::snprintf(buffer, sizeof(buffer), "\\u%04x", valueByte);
                    escaped += buffer;
                } else {
                    escaped.push_back(static_cast<char>(valueByte));
                }
        }
    }
    return escaped;
}

std::string deletionJson(bool ok, bool complete, std::uint64_t deletedBytes, const std::string& message,
                         bool unknownOutcome = false) {
    std::ostringstream out;
    out << "{\"ok\":" << (ok ? "true" : "false")
        << ",\"complete\":" << (complete ? "true" : "false")
        << ",\"deletedBytes\":" << deletedBytes
        << ",\"unknownOutcome\":" << (unknownOutcome ? "true" : "false");
    if (!message.empty()) out << ",\"message\":\"" << jsonEscape(message) << "\"";
    out << "}";
    return out.str();
}

std::vector<std::string> splitRelativePath(const std::string& relativePath, std::string& error) {
    std::vector<std::string> parts;
    if (relativePath.empty() || relativePath == "." || relativePath[0] == '/') {
        error = "Native deletion requires a non-root relative path";
        return parts;
    }
    std::size_t start = 0;
    while (start <= relativePath.size()) {
        const auto end = relativePath.find('/', start);
        const auto token = relativePath.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (token.empty() || token == "." || token == ".." || token.find('\0') != std::string::npos) {
            error = "Native deletion path contains an unsafe component";
            parts.clear();
            return parts;
        }
        parts.push_back(token);
        if (end == std::string::npos) break;
        start = end + 1;
    }
    if (parts.empty()) error = "Native deletion requires at least one relative path component";
    return parts;
}

bool statMatchesReviewedTop(const struct stat& actual, jlong expectedDevice, jlong expectedInode,
                            jint expectedMode, jlong expectedSize, jlong expectedModifiedAt) {
    return static_cast<jlong>(actual.st_dev) == expectedDevice &&
           static_cast<jlong>(actual.st_ino) == expectedInode &&
           static_cast<jint>(actual.st_mode) == expectedMode &&
           static_cast<jlong>(actual.st_size) == expectedSize &&
           static_cast<jlong>(actual.st_mtime) == expectedModifiedAt;
}

bool deleteTreeAt(int parentFd, const std::string& name, dev_t rootDevice,
                  const std::shared_ptr<std::atomic_bool>& cancelled,
                  std::uint64_t& deletedBytes, std::string& error, bool& unknownOutcome) {
    if (cancelled != nullptr && cancelled->load(std::memory_order_relaxed)) {
        error = deletedBytes > 0 ? "Native deletion cancellation requested after filesystem mutation"
                                 : "Native deletion cancellation requested before mutation";
        unknownOutcome = deletedBytes > 0;
        return false;
    }
    struct stat metadata {};
    if (fstatat(parentFd, name.c_str(), &metadata, AT_SYMLINK_NOFOLLOW) != 0) {
        error = "Unable to inspect reviewed target: " + std::string(std::strerror(errno));
        return false;
    }
    if (S_ISLNK(metadata.st_mode)) {
        error = "Native deletion refuses symbolic links";
        return false;
    }
    if (metadata.st_dev != rootDevice) {
        error = "Native deletion refused filesystem boundary crossing";
        return false;
    }
    if (S_ISDIR(metadata.st_mode)) {
        int directoryFd = openat(parentFd, name.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        if (directoryFd < 0) {
            error = "Unable to open directory for fd-relative deletion: " + std::string(std::strerror(errno));
            return false;
        }
        DIR* directory = fdopendir(directoryFd);
        if (directory == nullptr) {
            const int errorNumber = errno;
            close(directoryFd);
            error = "Unable to enumerate directory for fd-relative deletion: " + std::string(std::strerror(errorNumber));
            return false;
        }
        bool ok = true;
        while (ok) {
            errno = 0;
            dirent* entry = readdir(directory);
            if (entry == nullptr) {
                if (errno != 0) {
                    ok = false;
                    error = "Unable to read directory during fd-relative deletion: " + std::string(std::strerror(errno));
                }
                break;
            }
            const char* childName = entry->d_name;
            if (std::strcmp(childName, ".") == 0 || std::strcmp(childName, "..") == 0) continue;
            ok = deleteTreeAt(dirfd(directory), childName, rootDevice, cancelled, deletedBytes, error, unknownOutcome);
        }
        closedir(directory);
        if (!ok) return false;
        if (cancelled != nullptr && cancelled->load(std::memory_order_relaxed)) {
            error = deletedBytes > 0 ? "Native deletion cancellation requested after filesystem mutation"
                                     : "Native deletion cancellation requested before mutation";
            unknownOutcome = deletedBytes > 0;
            return false;
        }
        if (unlinkat(parentFd, name.c_str(), AT_REMOVEDIR) != 0) {
            error = "Unable to remove directory with unlinkat: " + std::string(std::strerror(errno));
            return false;
        }
        return true;
    }
    const std::uint64_t bytes = S_ISREG(metadata.st_mode) && metadata.st_size > 0
        ? static_cast<std::uint64_t>(metadata.st_size)
        : 0ULL;
    if (cancelled != nullptr && cancelled->load(std::memory_order_relaxed)) {
        error = deletedBytes > 0 ? "Native deletion cancellation requested after filesystem mutation"
                                 : "Native deletion cancellation requested before mutation";
        unknownOutcome = deletedBytes > 0;
        return false;
    }
    if (unlinkat(parentFd, name.c_str(), 0) != 0) {
        error = "Unable to remove file with unlinkat: " + std::string(std::strerror(errno));
        return false;
    }
    deletedBytes += bytes;
    return true;
}

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

void finishEvent(FILE* output, bool flush = false) {
    std::fputc('\n', output);
    if (flush) std::fflush(output);
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

void emitWarning(ScanContext& context, const std::string& code, const std::string& phase,
                 const std::string& path, const std::string& message, int errorNumber = 0) {
    ++context.warningCount;
    std::fputs("{\"type\":\"warning\",\"code\":", context.output);
    writeJsonString(context.output, code);
    std::fputs(",\"phase\":", context.output);
    writeJsonString(context.output, phase);
    std::fputs(",\"path\":", context.output);
    writeJsonString(context.output, path);
    std::fputs(",\"message\":", context.output);
    writeJsonString(context.output, message);
    if (errorNumber != 0) std::fprintf(context.output, ",\"errno\":%d", errorNumber);
    std::fputc('}', context.output);
    finishEvent(context.output);
}

void emitError(FILE* output, const std::string& code, const std::string& phase,
               const std::string& path, const std::string& message, int errorNumber = 0) {
    std::fputs("{\"type\":\"error\",\"code\":", output);
    writeJsonString(output, code);
    std::fputs(",\"phase\":", output);
    writeJsonString(output, phase);
    std::fputs(",\"path\":", output);
    writeJsonString(output, path);
    std::fputs(",\"message\":", output);
    writeJsonString(output, message);
    if (errorNumber != 0) std::fprintf(output, ",\"errno\":%d", errorNumber);
    std::fputc('}', output);
    finishEvent(output, true);
}

void emitProgress(ScanContext& context, const std::string& path) {
    std::fprintf(context.output,
        "{\"type\":\"progress\",\"scannedItems\":%" PRIu64 ",\"scannedBytes\":%" PRIu64 ",\"currentPath\":",
        context.scannedItems, context.scannedBytes);
    writeJsonString(context.output, path);
    std::fputc('}', context.output);
    finishEvent(context.output, true);
}

bool shouldEmit(ScanContext& context, bool directory, int depth, std::uint64_t size) {
    if (depth == 0 || size < context.minSize) return false;
    if (context.mode == EntryMode::Directories && !directory) return false;
    if (context.mode == EntryMode::Files && directory) return false;
    if (context.emittedEntries >= context.maxEntries) {
        context.truncated = true;
        return false;
    }
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
    if (directory) ++context.emittedDirectories; else ++context.emittedFiles;
}

Totals scanPath(ScanContext& context, const std::string& path, int depth) {
    if (context.cancelled->load(std::memory_order_relaxed)) return {};

    struct stat metadata {};
    if (lstat(path.c_str(), &metadata) != 0) {
        const int errorNumber = errno;
        emitWarning(context, "LSTAT_FAILED", "traversal", path, std::strerror(errorNumber), errorNumber);
        return {};
    }
    if (context.sameFilesystem && metadata.st_dev != context.rootDevice) {
        ++context.skippedFilesystemEntries;
        return {};
    }

    ++context.scannedItems;
    const bool directory = S_ISDIR(metadata.st_mode);
    Totals totals { sizeFor(metadata, context.apparentSize), directory ? 0ULL : 1ULL };

    if (!directory && metadata.st_nlink > 1) {
        if (!context.seenInodes.insert({metadata.st_dev, metadata.st_ino}).second) {
            totals = {};
        }
    }

    const bool mayDescend = directory && (context.maxDepth < 0 || depth < context.maxDepth);
    if (directory && !mayDescend) ++context.depthLimitedDirectories;
    if (mayDescend) {
        DIR* handle = opendir(path.c_str());
        if (handle == nullptr) {
            const int errorNumber = errno;
            if (depth == 0) {
                context.fatal = true;
                emitError(context.output, "ROOT_OPEN_FAILED", "root-open", path, std::strerror(errorNumber), errorNumber);
            } else {
                emitWarning(context, "DIRECTORY_OPEN_FAILED", "traversal", path, std::strerror(errorNumber), errorNumber);
            }
        } else {
            if (depth == 0) context.rootOpened = true;
            while (!context.cancelled->load(std::memory_order_relaxed)) {
                errno = 0;
                dirent* entry = readdir(handle);
                if (entry == nullptr) {
                    if (errno != 0) {
                        const int errorNumber = errno;
                        emitWarning(context, "DIRECTORY_READ_FAILED", "traversal", path, std::strerror(errorNumber), errorNumber);
                    }
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
    setvbuf(output, nullptr, _IOFBF, kOutputBufferBytes);

    auto cancelled = registerCancellationFlag(scanId);

    struct stat rootMetadata {};
    if (lstat(root.c_str(), &rootMetadata) != 0) {
        const int errorNumber = errno;
        emitError(output, "ROOT_LSTAT_FAILED", "root-preflight", root, std::strerror(errorNumber), errorNumber);
        std::fclose(output);
        unregisterCancellationFlag(scanId, cancelled);
        return 4;
    }
    if (S_ISLNK(rootMetadata.st_mode)) {
        emitError(output, "ROOT_SYMLINK", "root-preflight", root, "Scan root is a symbolic link");
        std::fclose(output);
        unregisterCancellationFlag(scanId, cancelled);
        return 5;
    }
    if (!S_ISDIR(rootMetadata.st_mode)) {
        emitError(output, "ROOT_NOT_DIRECTORY", "root-preflight", root, "Scan root is not a directory");
        std::fclose(output);
        unregisterCancellationFlag(scanId, cancelled);
        return 6;
    }
    DIR* rootProbe = opendir(root.c_str());
    if (rootProbe == nullptr) {
        const int errorNumber = errno;
        emitError(output, "ROOT_OPEN_FAILED", "root-preflight", root, std::strerror(errorNumber), errorNumber);
        std::fclose(output);
        unregisterCancellationFlag(scanId, cancelled);
        return 7;
    }
    closedir(rootProbe);

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
        0,
        0,
        0,
        0,
        false,
        false,
        false,
        {},
    };

    std::fputs("{\"type\":\"start\",\"backend\":\"dustui-native-root-v3\",\"root\":", output);
    writeJsonString(output, root);
    std::fputc('}', output);
    finishEvent(output, true);

    const Totals totals = scanPath(context, root, 0);
    const bool wasCancelled = cancelled->load(std::memory_order_relaxed);
    if (context.fatal) {
        std::fclose(output);
    } else {
        if (wasCancelled) {
            std::fprintf(output,
                "{\"type\":\"cancelled\",\"scannedItems\":%" PRIu64 ",\"scannedBytes\":%" PRIu64 "}",
                context.scannedItems, context.scannedBytes);
        } else {
            std::fprintf(output,
                "{\"type\":\"complete\",\"totalBytes\":%" PRIu64 ",\"scannedItems\":%" PRIu64
                ",\"scannedBytes\":%" PRIu64 ",\"warnings\":%" PRIu64
                ",\"emittedEntries\":%" PRIu64 ",\"emittedFiles\":%" PRIu64
                ",\"emittedDirectories\":%" PRIu64 ",\"truncated\":%s"
                ",\"depthLimitedDirectories\":%" PRIu64 ",\"skippedFilesystemEntries\":%" PRIu64
                ",\"rootOpened\":%s}",
                totals.bytes, context.scannedItems, context.scannedBytes, context.warningCount,
                context.emittedEntries, context.emittedFiles, context.emittedDirectories,
                context.truncated ? "true" : "false", context.depthLimitedDirectories,
                context.skippedFilesystemEntries, context.rootOpened ? "true" : "false");
        }
        finishEvent(output, true);
        std::fclose(output);
    }

    {
        unregisterCancellationFlag(scanId, cancelled);
    }
    return context.fatal ? 8 : (wasCancelled ? 130 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_cancel(JNIEnv* env, jobject, jstring scanIdValue) {
    const std::string scanId = javaString(env, scanIdValue);
    return requestCancellationFlag(scanId) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_cancelDeletion(JNIEnv* env, jobject, jstring operationIdValue) {
    const std::string operationId = javaString(env, operationIdValue);
    return requestCancellationFlag(operationId) ? JNI_TRUE : JNI_FALSE;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_deleteReviewedPath(
    JNIEnv* env, jobject, jstring operationIdValue, jstring rootValue, jstring relativeValue,
    jlong expectedDevice, jlong expectedInode, jint expectedMode, jlong expectedSize, jlong expectedModifiedAt) {
    const std::string operationId = javaString(env, operationIdValue);
    auto cancelled = registerCancellationFlag(operationId);
    const std::string root = javaString(env, rootValue);
    const std::string relative = javaString(env, relativeValue);
    auto returnResponse = [&](const std::string& response) -> jstring {
        unregisterCancellationFlag(operationId, cancelled);
        return env->NewStringUTF(response.c_str());
    };
    if (root.empty() || relative.empty()) {
        const std::string response = deletionJson(false, false, 0, "Native deletion requires root and relative target path");
        return returnResponse(response);
    }

    std::string pathError;
    const auto components = splitRelativePath(relative, pathError);
    if (!pathError.empty()) {
        const std::string response = deletionJson(false, false, 0, pathError);
        return returnResponse(response);
    }

    int rootFd = open(root.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (rootFd < 0) {
        const std::string response = deletionJson(false, false, 0, "Unable to open reviewed root with O_NOFOLLOW: " + std::string(std::strerror(errno)));
        return returnResponse(response);
    }
    struct stat rootMetadata {};
    if (fstat(rootFd, &rootMetadata) != 0) {
        const int errorNumber = errno;
        close(rootFd);
        const std::string response = deletionJson(false, false, 0, "Unable to inspect reviewed root fd: " + std::string(std::strerror(errorNumber)));
        return returnResponse(response);
    }

    int parentFd = rootFd;
    for (std::size_t index = 0; index + 1 < components.size(); ++index) {
        int nextFd = openat(parentFd, components[index].c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        if (parentFd != rootFd) close(parentFd);
        if (nextFd < 0) {
            const int errorNumber = errno;
            close(rootFd);
            const std::string response = deletionJson(false, false, 0, "Unable to resolve reviewed target parent fd: " + std::string(std::strerror(errorNumber)));
            return returnResponse(response);
        }
        struct stat parentMetadata {};
        if (fstat(nextFd, &parentMetadata) != 0) {
            const int errorNumber = errno;
            close(nextFd);
            close(rootFd);
            const std::string response = deletionJson(false, false, 0, "Unable to inspect reviewed target parent fd: " + std::string(std::strerror(errorNumber)));
            return returnResponse(response);
        }
        if (parentMetadata.st_dev != rootMetadata.st_dev) {
            close(nextFd);
            close(rootFd);
            const std::string response = deletionJson(false, false, 0, "Native deletion refused parent filesystem boundary crossing");
            return returnResponse(response);
        }
        parentFd = nextFd;
    }

    const std::string& leaf = components.back();
    struct stat topMetadata {};
    if (fstatat(parentFd, leaf.c_str(), &topMetadata, AT_SYMLINK_NOFOLLOW) != 0) {
        const int errorNumber = errno;
        if (parentFd != rootFd) close(parentFd);
        close(rootFd);
        const std::string response = deletionJson(false, false, 0, "Unable to inspect reviewed top-level target: " + std::string(std::strerror(errorNumber)));
        return returnResponse(response);
    }
    if (!statMatchesReviewedTop(topMetadata, expectedDevice, expectedInode, expectedMode, expectedSize, expectedModifiedAt)) {
        if (parentFd != rootFd) close(parentFd);
        close(rootFd);
        const std::string response = deletionJson(false, false, 0, "Reviewed top-level filesystem identity changed before native deletion");
        return returnResponse(response);
    }

    std::uint64_t deletedBytes = 0;
    std::string error;
    bool unknownOutcome = false;
    const bool ok = deleteTreeAt(parentFd, leaf, rootMetadata.st_dev, cancelled, deletedBytes, error, unknownOutcome);
    if (parentFd != rootFd) close(parentFd);
    close(rootFd);
    const std::string response = deletionJson(ok, ok, deletedBytes, error, unknownOutcome);
    return returnResponse(response);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikeyphw_dustui_root_NativeDustScanner_version(JNIEnv* env, jobject) {
    return env->NewStringUTF("dustui-native-root/3");
}
