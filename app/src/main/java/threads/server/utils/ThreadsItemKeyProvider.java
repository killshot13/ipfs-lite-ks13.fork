package threads.server.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;

public class ThreadsItemKeyProvider extends ItemKeyProvider<Long> {

    private final ThreadsViewAdapter mThreadsViewAdapter;


    public ThreadsItemKeyProvider(@NonNull ThreadsViewAdapter adapter) {
        super(SCOPE_CACHED);
        mThreadsViewAdapter = adapter;
    }

    @Nullable
    @Override
    public Long getKey(int position) {
        return mThreadsViewAdapter.getIdx(position);
    }

    @Override
    public int getPosition(@NonNull Long key) {
        return mThreadsViewAdapter.getPosition(key);
    }

}