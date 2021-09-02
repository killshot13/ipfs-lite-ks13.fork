package threads.server.utils;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class ThreadItemDetailsLookup extends ItemDetailsLookup<Long> {

    @NonNull
    private final RecyclerView mRecyclerView;

    private boolean mActive;

    public ThreadItemDetailsLookup(@NonNull RecyclerView recyclerView) {
        this.mRecyclerView = recyclerView;
        this.mActive = true;
    }

    public void setActive(boolean active) {
        mActive = active;
    }

    @Nullable
    @Override
    public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
        if (mActive) {
            View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ThreadsViewAdapter.ThreadViewHolder) {
                    return ((ThreadsViewAdapter.ThreadViewHolder) viewHolder).getThreadsItemDetails();
                }
            }
        }
        return null;
    }
}
