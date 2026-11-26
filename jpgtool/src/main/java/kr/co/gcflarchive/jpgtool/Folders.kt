package kr.co.gcflarchive.jpgtool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** Folders picked with the system folder picker (Storage Access Framework). */
object Folders {
    /** Stop scanning a huge folder tree here, so the app stays responsive. */
    const val MAX_FILES = 5000

    /** Lists the photos in folder [tree], in name order; [recursive] also looks inside subfolders. */
    fun listImages(context: Context, tree: Uri, recursive: Boolean): List<InputImage> {
        val out = ArrayList<InputImage>()
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        while (pending.isNotEmpty() && out.size < MAX_FILES) {
            val dirId = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
            val here = ArrayList<InputImage>()
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (recursive && !name.startsWith(".")) pending.add(id)
                    } else if (isPhoto(name, mime)) {
                        val size = if (c.isNull(3)) -1L else c.getLong(3)
                        here += InputImage(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size, mime)
                    }
                }
            }
            here.sortBy { it.name.lowercase() }
            out += here
        }
        return out.take(MAX_FILES)
    }

    /** Folder name shown to the user, e.g. "DCIM/Camera" → "Camera". */
    fun displayName(context: Context, tree: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            context.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/').orEmpty()
    }

    /** Keeps access to [tree] after the app restarts (needed for the save folder). */
    fun keepAccess(context: Context, tree: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching { context.contentResolver.takePersistableUriPermission(tree, flags) }
    }

    fun releaseAccess(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun canWrite(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    /** Writes a new JPG named [name] into folder [tree]; the provider adds " (1)" etc. if the name is taken. */
    fun save(context: Context, tree: Uri, name: String, bytes: ByteArray): Uri {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(context.contentResolver, dir, "image/jpeg", name)
            ?: throw IOException("Cannot create file in the save folder")
        try {
            context.contentResolver.openOutputStream(file, "w")?.use { it.write(bytes) } ?: throw IOException("Cannot write file")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, file) }
            throw e
        }
        return file
    }
}

/** Image files we can read: anything the system calls an image, or a known extension when it gives no type. */
fun isPhoto(name: String, mime: String?): Boolean {
    if (name.startsWith(".")) return false // hidden files, e.g. ".trashed-…" or ".pending-…"
    if (mime != null && mime != "application/octet-stream") return mime.startsWith("image/") && mime != "image/svg+xml"
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
}
