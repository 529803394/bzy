package com.example.helloworld;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

public class ShareFileProvider extends ContentProvider {

    private static final String[] COLUMNS = {
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = getFileForUri(uri);
        if (file == null || !file.exists()) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(
            projection != null ? projection : COLUMNS);
        Object[] row = new Object[2];
        row[0] = file.getName();
        row[1] = file.length();
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getFileForUri(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("File not found: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File getFileForUri(Uri uri) {
        try {
            String path = uri.getEncodedPath();
            if (path.startsWith("/")) path = path.substring(1);
            File base = new File(getContext().getCacheDir(), "images");
            return new File(base, path);
        } catch (Exception e) {
            return null;
        }
    }
}
