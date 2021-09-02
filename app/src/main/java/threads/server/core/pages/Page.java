package threads.server.core.pages;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@androidx.room.Entity
public class Page {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "pid")
    private final String pid;
    @Nullable
    @ColumnInfo(name = "content")
    private String content;
    @ColumnInfo(name = "sequence")
    private long sequence;


    public Page(@NonNull String pid) {
        this.pid = pid;
        this.sequence = 0L;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    @NonNull
    public String getPid() {
        return pid;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }


}