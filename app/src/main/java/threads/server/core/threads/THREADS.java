package threads.server.core.threads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import threads.lite.cid.Cid;

public class THREADS {

    private static THREADS INSTANCE = null;

    private final ThreadsDatabase threadsDatabase;


    private THREADS(final THREADS.Builder builder) {
        this.threadsDatabase = builder.threadsDatabase;
    }

    @NonNull
    private static THREADS createThreads(@NonNull ThreadsDatabase threadsDatabase) {


        return new THREADS.Builder()
                .threadsDatabase(threadsDatabase)
                .build();
    }

    public static THREADS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (THREADS.class) {
                if (INSTANCE == null) {
                    ThreadsDatabase threadsDatabase = Room.databaseBuilder(context,
                            ThreadsDatabase.class,
                            ThreadsDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();
                    INSTANCE = THREADS.createThreads(threadsDatabase);
                }
            }
        }
        return INSTANCE;
    }


    public void clear() {
        getThreadsDatabase().clearAllTables();
    }

    @NonNull
    public ThreadsDatabase getThreadsDatabase() {
        return threadsDatabase;
    }

    public void setThreadsDeleting(long... idxs) {
        for (long idx : idxs) {
            getThreadsDatabase().threadDao().setDeleting(idx);
        }
    }

    public void resetThreadsDeleting(long... idxs) {
        for (long idx : idxs) {
            getThreadsDatabase().threadDao().resetDeleting(idx);
        }
    }

    public void setThreadLeaching(long idx) {
        getThreadsDatabase().threadDao().setLeaching(idx);
    }

    public void resetThreadLeaching(long idx) {
        getThreadsDatabase().threadDao().resetLeaching(idx);
    }

    public boolean isThreadLeaching(long idx) {
        return getThreadsDatabase().threadDao().isLeaching(idx);
    }

    public void setThreadDone(long idx) {
        getThreadsDatabase().threadDao().setDone(idx);
    }

    public void setThreadDone(long idx, @NonNull String cid) {
        getThreadsDatabase().threadDao().setDone(idx, cid);
    }

    public List<Thread> getAncestors(long idx) {
        List<Thread> path = new ArrayList<>();
        if (idx > 0) {
            Thread thread = getThreadByIdx(idx);
            if (thread != null) {
                path.addAll(getAncestors(thread.getParent()));
                path.add(thread);
            }
        }
        return path;
    }

    public void setMimeType(@NonNull Thread thread, @NonNull String mimeType) {

        getThreadsDatabase().threadDao().setMimeType(thread.getIdx(), mimeType);
    }

    public void setThreadMimeType(long idx, @NonNull String mimeType) {

        getThreadsDatabase().threadDao().setMimeType(idx, mimeType);
    }

    @NonNull
    public Thread createThread(long parent) {
        return Thread.createThread(parent);
    }

    public boolean isReferenced(@NonNull String cid) {
        return getThreadsDatabase().threadDao().references(cid) > 0;
    }

    public void removeThread(@NonNull Thread thread) {
        getThreadsDatabase().threadDao().removeThread(thread);
    }

    public long storeThread(@NonNull Thread thread) {
        return getThreadsDatabase().threadDao().insertThread(thread);
    }

    public void setThreadName(long idx, @NonNull String name) {
        getThreadsDatabase().threadDao().setName(idx, name);
    }

    public void setThreadContent(long idx, @NonNull String cid) {
        getThreadsDatabase().threadDao().setContent(idx, cid);
    }

    public long getThreadParent(long idx) {
        return getThreadsDatabase().threadDao().getParent(idx);
    }

    public String getThreadName(long idx) {
        return getThreadsDatabase().threadDao().getName(idx);
    }

    @NonNull
    public List<Thread> getPins() {
        return getThreadsDatabase().threadDao().getPins();
    }

    @NonNull
    public List<Thread> getChildren(long parent) {
        return getThreadsDatabase().threadDao().getChildren(parent);
    }

    public long getChildrenSummarySize(long parent) {
        return getThreadsDatabase().threadDao().getChildrenSummarySize(parent);
    }

    @NonNull
    public List<Thread> getVisibleChildren(long thread) {
        return getThreadsDatabase().threadDao().getVisibleChildren(thread);
    }

    @Nullable
    public Thread getThreadByIdx(long idx) {
        return getThreadsDatabase().threadDao().getThreadByIdx(idx);
    }

    @Nullable
    public String getThreadContent(long idx) {
        return getThreadsDatabase().threadDao().getContent(idx);
    }


    @NonNull
    public List<Thread> getThreadsByContentAndParent(@NonNull Cid cid, long thread) {

        return getThreadsDatabase().threadDao().getThreadsByContentAndParent(cid.String(), thread);
    }

    @NonNull
    public List<Thread> getThreadsByNameAndParent(@NonNull String name, long thread) {

        return getThreadsDatabase().threadDao().getThreadsByNameAndParent(name, thread);
    }


    public void setThreadProgress(long idx, int progress) {

        getThreadsDatabase().threadDao().setProgress(idx, progress);
    }


    public void setThreadSize(long idx, long size) {
        getThreadsDatabase().threadDao().setSize(idx, size);
    }

    public void setThreadWork(long idx, @NonNull UUID id) {
        getThreadsDatabase().threadDao().setWork(idx, id.toString());
    }

    public void resetThreadWork(long idx) {
        getThreadsDatabase().threadDao().resetWork(idx);
    }

    public boolean isThreadInit(long idx) {
        return getThreadsDatabase().threadDao().isInit(idx);
    }

    public void resetThreadInit(long idx) {
        getThreadsDatabase().threadDao().resetInit(idx);
    }

    public List<Long> getDeletedThreads() {
        return getThreadsDatabase().threadDao().getDeletedThreads(System.currentTimeMillis());
    }

    public void setThreadLastModified(long idx, long time) {
        getThreadsDatabase().threadDao().setLastModified(idx, time);
    }

    public void setThreadUri(long idx, @NonNull String uri) {
        getThreadsDatabase().threadDao().setUri(idx, uri);
    }

    @Nullable
    public UUID getThreadWork(long idx) {
        String work = getThreadsDatabase().threadDao().getWork(idx);
        if (work != null) {
            return UUID.fromString(work);
        }
        return null;
    }

    static class Builder {

        ThreadsDatabase threadsDatabase = null;

        THREADS build() {

            return new THREADS(this);
        }

        Builder threadsDatabase(@NonNull ThreadsDatabase threadsDatabase) {

            this.threadsDatabase = threadsDatabase;
            return this;
        }


    }
}
