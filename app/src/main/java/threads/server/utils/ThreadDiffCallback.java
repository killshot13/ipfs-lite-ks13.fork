package threads.server.utils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;
import java.util.Objects;

import threads.server.core.threads.Thread;

@SuppressWarnings("WeakerAccess")
public class ThreadDiffCallback extends DiffUtil.Callback {
    private final List<Thread> mOldList;
    private final List<Thread> mNewList;

    public ThreadDiffCallback(List<Thread> messages, List<Thread> messageThreads) {
        this.mOldList = messages;
        this.mNewList = messageThreads;
    }

    @Override
    public int getOldListSize() {
        return mOldList.size();
    }

    @Override
    public int getNewListSize() {
        return mNewList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return mOldList.get(oldItemPosition).areItemsTheSame(mNewList.get(
                newItemPosition));
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return sameContent(mOldList.get(oldItemPosition), mNewList.get(newItemPosition));
    }


    private boolean sameContent(@NonNull Thread t, @NonNull Thread o) {

        if (t == o) return true;
        return t.getProgress() == o.getProgress() &&
                t.isLeaching() == o.isLeaching() &&
                t.isSeeding() == o.isSeeding() &&
                t.isDeleting() == o.isDeleting() &&
                Objects.equals(t.getSize(), o.getSize()) &&
                Objects.equals(t.getParent(), o.getParent()) &&
                Objects.equals(t.getName(), o.getName()) &&
                Objects.equals(t.getMimeType(), o.getMimeType()) &&
                Objects.equals(t.getContent(), o.getContent()) &&
                Objects.equals(t.getUri(), o.getUri()) &&
                Objects.equals(t.getLastModified(), o.getLastModified());
    }

}
