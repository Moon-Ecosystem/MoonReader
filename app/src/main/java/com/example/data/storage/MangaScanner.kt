package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.Chapter
import com.example.data.Manga
import java.io.File
import java.io.FileOutputStream

object MangaScanner {
    private const val TAG = "MangaScanner"

    data class ScanResult(
        val mangaList: List<Manga>,
        val chaptersByMangaId: Map<String, List<Chapter>>
    )

    private fun isArchive(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower.endsWith(".cbz") ||
                lower.endsWith(".zip") ||
                lower.endsWith(".cbr") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".7z")
    }

    private fun isImage(name: String): Boolean {
        return CbzExtractor.isImageFile(name)
    }

    // Lightweight SAF node: DocumentFile issues a separate ContentResolver query for every name/type lookup,
    // so we read id, name and mime for all children in a single query instead.
    private class SafDoc(
        val uri: Uri,
        val docId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long = -1L,
        val lastModified: Long = 0L
    )

    private fun listSafChildren(context: Context, treeUri: Uri, parentDocId: String): List<SafDoc> {
        val result = mutableListOf<SafDoc>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    result.add(
                        SafDoc(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            docId = id,
                            name = c.getString(1) ?: "",
                            isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (c.isNull(3)) -1L else c.getLong(3),
                            lastModified = if (c.isNull(4)) 0L else c.getLong(4)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list SAF children of $parentDocId", e)
        }
        return result
    }

    private fun findArchiveDocsRecursively(dir: SafDoc, ls: (SafDoc) -> List<SafDoc>, depth: Int = 0): List<SafDoc> {
        if (depth > 3) return emptyList()
        val files = ls(dir)
        val direct = files.filter { !it.isDirectory && isArchive(it.name) }
        val nested = files.filter { it.isDirectory }.flatMap { findArchiveDocsRecursively(it, ls, depth + 1) }
        return direct + nested
    }

    private fun findImageDocsRecursively(dir: SafDoc, ls: (SafDoc) -> List<SafDoc>, depth: Int = 0): List<SafDoc> {
        if (depth > 2) return emptyList()
        val files = ls(dir)
        val direct = files.filter { !it.isDirectory && isImage(it.name) }
        val nested = files.filter { it.isDirectory }.flatMap { findImageDocsRecursively(it, ls, depth + 1) }
        return direct + nested
    }

    private fun findArchiveFilesRecursively(dir: File, depth: Int = 0): List<File> {
        if (depth > 3) return emptyList()
        val files = dir.listFiles() ?: emptyArray()
        val direct = files.filter { it.isFile && isArchive(it.name) }
        val nested = files.filter { it.isDirectory }.flatMap { findArchiveFilesRecursively(it, depth + 1) }
        return direct + nested
    }

    private fun findImageFilesRecursively(dir: File, depth: Int = 0): List<File> {
        if (depth > 2) return emptyList()
        val files = dir.listFiles() ?: emptyArray()
        val direct = files.filter { it.isFile && isImage(it.name) }
        val nested = files.filter { it.isDirectory }.flatMap { findImageFilesRecursively(it, depth + 1) }
        return direct + nested
    }

    private fun copySafCoverToInternalStorage(context: Context, coverDoc: SafDoc, mangaId: String): String? {
        return try {
            val hash = CbzExtractor.getStableHash(mangaId)
            val coversDir = File(context.filesDir, "folder_covers")
            coversDir.mkdirs()
            val destFile = File(coversDir, "folder_cover_$hash.jpg")
            // Rescans run on every launch; skip the copy when the cached cover is still current
            if (destFile.length() > 0L && destFile.length() == coverDoc.size &&
                coverDoc.lastModified > 0L && destFile.lastModified() >= coverDoc.lastModified
            ) {
                return destFile.absolutePath
            }
            context.contentResolver.openInputStream(coverDoc.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (e: Exception) {
            Log.e(TAG, "Error copying folder cover for $mangaId", e)
            null
        }
    }

    /**
     * Scans a target folder URI (either a SAF tree URI or a file:// URI) for manga subfolders and chapters.
     */
    fun scanFolder(context: Context, rootUriString: String): ScanResult {
        return if (rootUriString.startsWith("content://")) {
            scanSafFolder(context, Uri.parse(rootUriString))
        } else {
            scanLocalFileFolder(context, rootUriString)
        }
    }

    private fun scanSafFolder(context: Context, treeUri: Uri): ScanResult {
        val mangaList = mutableListOf<Manga>()
        val chaptersMap = mutableMapOf<String, List<Chapter>>()

        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return ScanResult(emptyList(), emptyMap())
            // Each folder is visited several times below; list it only once per scan
            val listingCache = HashMap<String, List<SafDoc>>()
            val ls: (SafDoc) -> List<SafDoc> = { doc ->
                listingCache.getOrPut(doc.docId) { listSafChildren(context, treeUri, doc.docId) }
            }
            val rootNode = SafDoc(rootDoc.uri, DocumentsContract.getTreeDocumentId(treeUri), rootDoc.name ?: "", true)
            val children = ls(rootNode)

            // Check if there are subfolders
            val subfolders = children.filter { it.isDirectory }
            val directCbzFiles = children.filter { !it.isDirectory && isArchive(it.name) }

            // Expand candidate folders (handle nested grouping folders if present)
            val candidateFolders = mutableListOf<SafDoc>()
            for (sub in subfolders) {
                val subChildren = ls(sub)
                val directArchives = subChildren.filter { !it.isDirectory && isArchive(it.name) }
                val nestedDirs = subChildren.filter { it.isDirectory }
                if (directArchives.isNotEmpty() || nestedDirs.isEmpty()) {
                    candidateFolders.add(sub)
                } else {
                    // Check if sub contains subfolders that have archives
                    val subWithArchives = nestedDirs.filter { findArchiveDocsRecursively(it, ls).isNotEmpty() }
                    if (subWithArchives.isNotEmpty()) {
                        candidateFolders.addAll(subWithArchives)
                    } else {
                        candidateFolders.add(sub)
                    }
                }
            }

            for (folder in candidateFolders) {
                val mangaTitle = folder.name.ifEmpty { "Untitled Manga" }
                val mangaId = folder.uri.toString()

                val chapterDocs = findArchiveDocsRecursively(folder, ls)
                if (chapterDocs.isNotEmpty()) {
                    val chapters = chapterDocs.map { doc ->
                        val fileName = doc.name.ifEmpty { "chapter.cbz" }
                        val chapterTitle = cleanChapterTitle(fileName)
                        val chapterNumber = NaturalOrderComparator.extractChapterNumber(fileName)
                        Chapter(
                            id = doc.uri.toString(),
                            mangaId = mangaId,
                            title = chapterTitle,
                            fileName = fileName,
                            fileUriString = doc.uri.toString(),
                            chapterNumber = chapterNumber
                        )
                    }.sortedWith { a, b ->
                        val numDiff = a.chapterNumber.compareTo(b.chapterNumber)
                        if (numDiff != 0) numDiff else NaturalOrderComparator.compare(a.fileName, b.fileName)
                    }

                    // 1. First priority: Check if user has kept a standalone cover image in this folder!
                    val imageDocs = findImageDocsRecursively(folder, ls)
                    val explicitCoverDoc = imageDocs.firstOrNull { doc ->
                        val n = doc.name.lowercase()
                        n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("front.")
                    } ?: imageDocs.firstOrNull { doc ->
                        val n = doc.name.lowercase()
                        n.contains("cover") || n.contains("folder") || n.contains("poster") || n.contains("front")
                    } ?: imageDocs.firstOrNull()

                    val (coverPath, hasFolderCover) = if (explicitCoverDoc != null) {
                        val cachedPath = copySafCoverToInternalStorage(context, explicitCoverDoc, mangaId)
                        Pair(cachedPath, cachedPath != null)
                    } else {
                        // 2. Fallback: Extract cover thumbnail from first chapter
                        val firstChapter = chapters.firstOrNull()
                        val extractedPath = firstChapter?.let {
                            CbzExtractor.extractCover(context, it.fileUriString, mangaId)
                        }
                        Pair(extractedPath, false)
                    }

                    val manga = Manga(
                        id = mangaId,
                        title = mangaTitle,
                        folderUriString = mangaId,
                        chapterCount = chapters.size,
                        coverPath = coverPath,
                        hasExplicitFolderCover = hasFolderCover
                    )

                    mangaList.add(manga)
                    chaptersMap[mangaId] = chapters
                }
            }

            // If the folder directly contains cbz files (and no subfolders or additional root files)
            if (directCbzFiles.isNotEmpty()) {
                val mangaTitle = rootDoc.name ?: "Manga Library"
                val mangaId = rootDoc.uri.toString() + "_root"

                val chapters = directCbzFiles.map { doc ->
                    val fileName = doc.name.ifEmpty { "chapter.cbz" }
                    val chapterTitle = cleanChapterTitle(fileName)
                    val chapterNumber = NaturalOrderComparator.extractChapterNumber(fileName)
                    Chapter(
                        id = doc.uri.toString(),
                        mangaId = mangaId,
                        title = chapterTitle,
                        fileName = fileName,
                        fileUriString = doc.uri.toString(),
                        chapterNumber = chapterNumber
                    )
                }.sortedWith { a, b ->
                    val numDiff = a.chapterNumber.compareTo(b.chapterNumber)
                    if (numDiff != 0) numDiff else NaturalOrderComparator.compare(a.fileName, b.fileName)
                }

                val imageDocs = children.filter { !it.isDirectory && isImage(it.name) }
                val explicitCoverDoc = imageDocs.firstOrNull { doc ->
                    val n = doc.name.lowercase()
                    n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("front.")
                } ?: imageDocs.firstOrNull { doc ->
                    val n = doc.name.lowercase()
                    n.contains("cover") || n.contains("folder") || n.contains("poster") || n.contains("front")
                } ?: imageDocs.firstOrNull()

                val (coverPath, hasFolderCover) = if (explicitCoverDoc != null) {
                    val cachedPath = copySafCoverToInternalStorage(context, explicitCoverDoc, mangaId)
                    Pair(cachedPath, cachedPath != null)
                } else {
                    val firstChapter = chapters.firstOrNull()
                    val extractedPath = firstChapter?.let {
                        CbzExtractor.extractCover(context, it.fileUriString, mangaId)
                    }
                    Pair(extractedPath, false)
                }

                val manga = Manga(
                    id = mangaId,
                    title = mangaTitle,
                    folderUriString = rootDoc.uri.toString(),
                    chapterCount = chapters.size,
                    coverPath = coverPath,
                    hasExplicitFolderCover = hasFolderCover
                )
                mangaList.add(manga)
                chaptersMap[mangaId] = chapters
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SAF folder $treeUri", e)
        }

        mangaList.sortBy { it.title }
        return ScanResult(mangaList, chaptersMap)
    }

    private fun scanLocalFileFolder(context: Context, folderPathOrUri: String): ScanResult {
        val mangaList = mutableListOf<Manga>()
        val chaptersMap = mutableMapOf<String, List<Chapter>>()

        try {
            val path = if (folderPathOrUri.startsWith("file://")) {
                Uri.parse(folderPathOrUri).path ?: folderPathOrUri
            } else {
                folderPathOrUri
            }
            val rootFile = File(path)
            if (!rootFile.exists() || !rootFile.isDirectory) {
                return ScanResult(emptyList(), emptyMap())
            }

            val entries = rootFile.listFiles() ?: emptyArray()
            val subdirs = entries.filter { it.isDirectory }
            val directFiles = entries.filter { it.isFile && isArchive(it.name) }

            // Expand candidate subdirs
            val candidateDirs = mutableListOf<File>()
            for (sub in subdirs) {
                val subChildren = sub.listFiles() ?: emptyArray()
                val directArchives = subChildren.filter { it.isFile && isArchive(it.name) }
                val nestedDirs = subChildren.filter { it.isDirectory }
                if (directArchives.isNotEmpty() || nestedDirs.isEmpty()) {
                    candidateDirs.add(sub)
                } else {
                    val subWithArchives = nestedDirs.filter { findArchiveFilesRecursively(it).isNotEmpty() }
                    if (subWithArchives.isNotEmpty()) {
                        candidateDirs.addAll(subWithArchives)
                    } else {
                        candidateDirs.add(sub)
                    }
                }
            }

            for (dir in candidateDirs) {
                val mangaTitle = dir.name
                val mangaId = dir.absolutePath

                val chapterFiles = findArchiveFilesRecursively(dir)
                if (chapterFiles.isNotEmpty()) {
                    val chapters = chapterFiles.map { file ->
                        val fileName = file.name
                        val chapterTitle = cleanChapterTitle(fileName)
                        val chapterNumber = NaturalOrderComparator.extractChapterNumber(fileName)
                        Chapter(
                            id = file.absolutePath,
                            mangaId = mangaId,
                            title = chapterTitle,
                            fileName = fileName,
                            fileUriString = file.absolutePath,
                            chapterNumber = chapterNumber
                        )
                    }.sortedWith { a, b ->
                        val numDiff = a.chapterNumber.compareTo(b.chapterNumber)
                        if (numDiff != 0) numDiff else NaturalOrderComparator.compare(a.fileName, b.fileName)
                    }

                    // Check if there is an explicit image in the manga folder (e.g. cover.jpg)
                    val imageFiles = findImageFilesRecursively(dir)
                    val explicitCoverFile = imageFiles.firstOrNull { file ->
                        val n = file.name.lowercase()
                        n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("front.")
                    } ?: imageFiles.firstOrNull { file ->
                        val n = file.name.lowercase()
                        n.contains("cover") || n.contains("folder") || n.contains("poster") || n.contains("front")
                    } ?: imageFiles.firstOrNull()

                    val (coverPath, hasFolderCover) = if (explicitCoverFile != null) {
                        Pair(explicitCoverFile.absolutePath, true)
                    } else {
                        val firstChapter = chapters.firstOrNull()
                        val extracted = firstChapter?.let {
                            CbzExtractor.extractCover(context, it.fileUriString, mangaId)
                        }
                        Pair(extracted, false)
                    }

                    val manga = Manga(
                        id = mangaId,
                        title = mangaTitle,
                        folderUriString = mangaId,
                        chapterCount = chapters.size,
                        coverPath = coverPath,
                        hasExplicitFolderCover = hasFolderCover
                    )
                    mangaList.add(manga)
                    chaptersMap[mangaId] = chapters
                }
            }

            if (directFiles.isNotEmpty()) {
                val mangaTitle = rootFile.name
                val mangaId = rootFile.absolutePath + "_root"
                val chapters = directFiles.map { file ->
                    val fileName = file.name
                    val chapterTitle = cleanChapterTitle(fileName)
                    val chapterNumber = NaturalOrderComparator.extractChapterNumber(fileName)
                    Chapter(
                        id = file.absolutePath,
                        mangaId = mangaId,
                        title = chapterTitle,
                        fileName = fileName,
                        fileUriString = file.absolutePath,
                        chapterNumber = chapterNumber
                    )
                }.sortedWith { a, b ->
                    val numDiff = a.chapterNumber.compareTo(b.chapterNumber)
                    if (numDiff != 0) numDiff else NaturalOrderComparator.compare(a.fileName, b.fileName)
                }

                val imageFiles = entries.filter { it.isFile && isImage(it.name) }
                val explicitCoverFile = imageFiles.firstOrNull { file ->
                    val n = file.name.lowercase()
                    n.startsWith("cover.") || n.startsWith("folder.") || n.startsWith("poster.") || n.startsWith("front.")
                } ?: imageFiles.firstOrNull { file ->
                    val n = file.name.lowercase()
                    n.contains("cover") || n.contains("folder") || n.contains("poster") || n.contains("front")
                } ?: imageFiles.firstOrNull()

                val (coverPath, hasFolderCover) = if (explicitCoverFile != null) {
                    Pair(explicitCoverFile.absolutePath, true)
                } else {
                    val firstChapter = chapters.firstOrNull()
                    val extracted = firstChapter?.let {
                        CbzExtractor.extractCover(context, it.fileUriString, mangaId)
                    }
                    Pair(extracted, false)
                }

                val manga = Manga(
                    id = mangaId,
                    title = mangaTitle,
                    folderUriString = rootFile.absolutePath,
                    chapterCount = chapters.size,
                    coverPath = coverPath,
                    hasExplicitFolderCover = hasFolderCover
                )
                mangaList.add(manga)
                chaptersMap[mangaId] = chapters
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local folder $folderPathOrUri", e)
        }

        mangaList.sortBy { it.title }
        return ScanResult(mangaList, chaptersMap)
    }

    private fun cleanChapterTitle(fileName: String): String {
        return fileName
            .removeSuffix(".cbz")
            .removeSuffix(".CBZ")
            .removeSuffix(".zip")
            .removeSuffix(".ZIP")
            .removeSuffix(".cbr")
            .removeSuffix(".CBR")
            .removeSuffix(".rar")
            .removeSuffix(".RAR")
            .removeSuffix(".7z")
            .removeSuffix(".7Z")
            .replace("_", " ")
            .trim()
    }
}
