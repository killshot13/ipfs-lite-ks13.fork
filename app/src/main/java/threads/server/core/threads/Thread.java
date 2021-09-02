package threads.server.core.threads;

import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import java.util.Objects;
import java.util.UUID;


@androidx.room.Entity
public class Thread {

    @ColumnInfo(name = "parent")
    private final long parent; // checked
    @PrimaryKey(autoGenerate = true)
    private long idx; // checked
    @ColumnInfo(name = "lastModified")
    private long lastModified; // checked

    @ColumnInfo(name = "progress")
    private int progress;  // checked
    @Nullable
    @ColumnInfo(name = "content")
    private String content;  // checked

    @ColumnInfo(name = "size")
    private long size;  // checked
    @NonNull
    @ColumnInfo(name = "mimeType")
    private String mimeType;  // checked
    @NonNull
    @ColumnInfo(name = "name")
    private String name = "";
    @Nullable
    @ColumnInfo(name = "uri")
    private String uri;
    @Nullable
    @ColumnInfo(name = "work")
    private String work;
    @ColumnInfo(name = "leaching")
    private boolean leaching; // checked
    @ColumnInfo(name = "seeding")
    private boolean seeding; // checked
    @ColumnInfo(name = "deleting")
    private boolean deleting; // checked
    @ColumnInfo(name = "init")
    private boolean init;


    Thread(long parent) {

        this.parent = parent;
        this.lastModified = System.currentTimeMillis();
        this.mimeType = "";

        this.leaching = false;
        this.seeding = false;
        this.deleting = false;
        this.progress = 0;
        this.init = false;
    }

    static Thread createThread(long parent) {
        return new Thread(parent);
    }

    @Nullable
    public String getUri() {
        return uri;
    }

    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    public boolean isLeaching() {
        return leaching;
    }

    public void setLeaching(boolean leaching) {
        this.leaching = leaching;
    }


    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }


    public long getIdx() {
        return idx;
    }

    void setIdx(long idx) {
        this.idx = idx;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(@NonNull String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean areItemsTheSame(@NonNull Thread thread) {
        return idx == thread.getIdx();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Thread thread = (Thread) o;
        return getIdx() == thread.getIdx();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdx());
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@Nullable String content) {
        this.content = content;
    }

    public long getParent() {
        return parent;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public boolean isDir() {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(getMimeType());
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isSeeding() {
        return seeding;
    }

    public void setSeeding(boolean seeding) {
        this.seeding = seeding;
    }

    public boolean isDeleting() {
        return deleting;
    }

    public void setDeleting(boolean deleting) {
        this.deleting = deleting;
    }

    @Nullable
    public String getWork() {
        return work;
    }

    public void setWork(@Nullable String work) {
        this.work = work;
    }

    @Nullable
    public UUID getWorkUUID() {
        if (work != null) {
            return UUID.fromString(work);
        }
        return null;
    }

    public boolean isInit() {
        return init;
    }

    public void setInit(boolean init) {
        this.init = init;
    }

}
