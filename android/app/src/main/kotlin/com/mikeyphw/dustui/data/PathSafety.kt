package com.mikeyphw.dustui.data

import java.util.ArrayDeque
import java.util.Locale
import java.util.TreeSet

/**
 * Single canonical path relationship layer for cleanup safety.
 *
 * All destructive features must use component-aware relations here instead of
 * ad-hoc string prefix checks. The functions are intentionally lexical because
 * root-side execution performs the stronger filesystem identity checks.
 */
object PathSafety {
    fun normalize(path: String): String? {
        if (!path.startsWith('/')) return null
        if ('\u0000' in path) return null
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun isSameOrDescendant(path: String, root: String): Boolean {
        val cleanPath = normalize(path) ?: return false
        val cleanRoot = normalize(root) ?: return false
        return cleanPath == cleanRoot || cleanPath.startsWith(cleanRoot.trimEnd('/') + "/")
    }

    fun intersects(a: String, b: String): Boolean {
        val cleanA = normalize(a) ?: return false
        val cleanB = normalize(b) ?: return false
        return cleanA == cleanB || cleanA.startsWith(cleanB.trimEnd('/') + "/") || cleanB.startsWith(cleanA.trimEnd('/') + "/")
    }

    fun relation(candidate: String, protectedOrExcluded: String): PathRelation? {
        val cleanCandidate = normalize(candidate) ?: return null
        val cleanProtected = normalize(protectedOrExcluded) ?: return null
        return when {
            cleanCandidate == cleanProtected -> PathRelation.SAME
            cleanCandidate.startsWith(cleanProtected.trimEnd('/') + "/") -> PathRelation.CANDIDATE_DESCENDANT
            cleanProtected.startsWith(cleanCandidate.trimEnd('/') + "/") -> PathRelation.CANDIDATE_ANCESTOR
            else -> null
        }
    }

    fun collapseRoots(paths: Collection<String>): List<String> {
        val sorted = paths.mapNotNull(::normalize)
            .distinct()
            .sortedWith(compareBy<String> { it.count { ch -> ch == '/' } }.thenBy { it.length }.thenBy { it.lowercase(Locale.ROOT) })
        val trie = PathTrie()
        val roots = mutableListOf<String>()
        sorted.forEach { path ->
            if (trie.addIfNoAncestor(path)) roots += path
        }
        return roots
    }

    class MutableOverlapIndex(paths: Collection<String> = emptyList()) {
        private val roots = TreeSet<String>()

        init { addAll(paths) }

        fun isEmpty(): Boolean = roots.isEmpty()

        fun intersects(path: String): Boolean {
            val clean = normalize(path) ?: return false
            if (roots.contains(clean)) return true
            var probe = clean
            while (probe != "/") {
                probe = probe.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "/" }
                if (roots.contains(probe)) return true
            }
            val prefix = clean.trimEnd('/') + "/"
            val next = roots.ceiling(prefix)
            return next?.startsWith(prefix) == true
        }

        fun add(path: String) {
            addAll(listOf(path))
        }

        fun addAll(paths: Collection<String>) {
            val collapsed = collapseRoots(roots + paths)
            roots.clear()
            roots.addAll(collapsed)
        }

        fun toList(): List<String> = roots.toList()
    }

    private class PathTrie {
        private val root = Node()
        fun addIfNoAncestor(path: String): Boolean {
            val segments = path.trim('/').split('/').filter(String::isNotEmpty)
            var node = root
            for (segment in segments) {
                if (node.terminal) return false
                node = node.children.getOrPut(segment) { Node() }
            }
            if (node.terminal) return false
            node.terminal = true
            node.children.clear()
            return true
        }
    }

    private class Node(
        val children: MutableMap<String, Node> = linkedMapOf(),
        var terminal: Boolean = false,
    )
}

enum class PathRelation { SAME, CANDIDATE_DESCENDANT, CANDIDATE_ANCESTOR }
