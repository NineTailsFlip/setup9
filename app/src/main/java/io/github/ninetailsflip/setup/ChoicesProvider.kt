package io.github.ninetailsflip.setup

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.ninetailsflip.update.SetupChoices

/** Serves [Choices] to the Nine apps (nine-core's SetupChoices reads them): one row per key.
 *  Read-only. */
class ChoicesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf(SetupChoices.COLUMN_KEY, SetupChoices.COLUMN_VALUE))
        if (uri.path == SetupChoices.URI.path) {
            Choices.served(requireNotNull(context)).forEach { (key, value) -> cursor.addRow(arrayOf(key, value)) }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
}
