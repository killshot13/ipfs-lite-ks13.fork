package threads.server.utils;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;

@SuppressWarnings("WeakerAccess")
public class ThreadItemDetails extends ItemDetailsLookup.ItemDetails<Long> {
    private final ThreadItemPosition mThreadItemPosition;
    public long idx;

    ThreadItemDetails(@NonNull ThreadItemPosition threadItemPosition) {
        this.mThreadItemPosition = threadItemPosition;
    }

    @Override
    public int getPosition() {
        return mThreadItemPosition.getPosition(idx);
    }

    @Nullable
    @Override
    public Long getSelectionKey() {
        return idx;
    }

    @Override
    public boolean inSelectionHotspot(@NonNull MotionEvent e) {
        return false;//don't consider taps as selections => Similar to google photos.
        // if true then consider click as selection
    }

    @Override
    public boolean inDragRegion(@NonNull MotionEvent e) {
        return true;
    }
}
