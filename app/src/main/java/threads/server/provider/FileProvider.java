package threads.server.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import threads.lite.LogUtils;
import threads.server.BuildConfig;
import threads.server.services.MimeTypeService;

public class FileProvider {
    private static final String TAG = FileProvider.class.getSimpleName();
    private static final String IMAGES = "images";
    private static final String DATA = "data";
    private static FileProvider INSTANCE = null;
    private final File mImageDir;
    private final File mDataDir;

    private FileProvider(@NonNull Context context) {
        mImageDir = new File(context.getCacheDir(), IMAGES);
        mDataDir = new File(context.getCacheDir(), DATA);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasReadPermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasWritePermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }


    @NonNull
    public static String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = MimeTypeService.OCTET_MIME_TYPE;
        }
        return mimeType;
    }

    @NonNull
    public static String getFileName(@NonNull Context context, @NonNull Uri uri) {
        String filename = null;

        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            filename = cursor.getString(nameIndex);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        if (filename == null) {
            filename = uri.getLastPathSegment();
        }

        if (filename == null) {
            filename = "file_name_not_detected";
        }

        return filename;
    }

    public static long getFileSize(@NonNull Context context, @NonNull Uri uri) {

        ContentResolver contentResolver = context.getContentResolver();

        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            return cursor.getLong(nameIndex);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }


        try (ParcelFileDescriptor fd = contentResolver.openFileDescriptor(uri, "r")) {
            Objects.requireNonNull(fd);
            return fd.getStatSize();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return -1;
    }


    public static boolean isPartial(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri, new String[]{
                DocumentsContract.Document.COLUMN_FLAGS}, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();

            int docFlags = cursor.getInt(0);
            if ((docFlags & DocumentsContract.Document.FLAG_PARTIAL) != 0) {
                return true;
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    public static Uri getDataUri(@NonNull Context context, long idx) {

        FileProvider fileProvider = FileProvider.getInstance(context);
        File newFile = fileProvider.getDataFile(idx);
        if (newFile.exists()) {
            return androidx.core.content.FileProvider.getUriForFile(
                    context, BuildConfig.APPLICATION_ID, newFile);
        }
        return null;
    }


    public static FileProvider getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (FileProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FileProvider(context);
                }
            }
        }
        return INSTANCE;
    }

    public File getDataFile(long idx) {
        return new File(getDataDir(), "" + idx);
    }

    @NonNull
    public File createDataFile(long idx) throws IOException {

        File file = getDataFile(idx);
        if (file.exists()) {
            boolean result = file.delete();
            if (!result) {
                LogUtils.info(TAG, "Deleting failed");
            }
        }
        boolean succes = file.createNewFile();
        if (!succes) {
            LogUtils.info(TAG, "Failed create a new file");
        }
        return file;
    }

    public File getImageDir() {
        if (!mImageDir.isDirectory() && !mImageDir.exists()) {
            boolean result = mImageDir.mkdir();
            if (!result) {
                throw new RuntimeException("image directory does not exists");
            }
        }
        return mImageDir;
    }

    public File createTempDataFile() throws IOException {
        return File.createTempFile("tmp", ".data", getDataDir());
    }

    public File getDataDir() {
        if (!mDataDir.isDirectory() && !mDataDir.exists()) {
            boolean result = mDataDir.mkdir();
            if (!result) {
                throw new RuntimeException("data directory does not exists");
            }
        }
        return mDataDir;
    }

    public void cleanImageDir() {
        deleteFile(getImageDir());
    }

    private void deleteFile(@NonNull File root) {
        try {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteFile(file);
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        } else {
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        }
                    }
                }
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            } else {
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void cleanDataDir() {
        deleteFile(getDataDir());
    }
}
