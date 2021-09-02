package threads.server.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import threads.lite.LogUtils;
import threads.server.R;
import threads.server.core.threads.Thread;
import threads.server.services.MimeTypeService;

public class ThreadsViewAdapter extends RecyclerView.Adapter<ThreadsViewAdapter.ViewHolder> implements ThreadItemPosition {

    private static final String TAG = ThreadsViewAdapter.class.getSimpleName();
    private final Context mContext;
    private final ThreadsViewAdapterListener mListener;
    private final List<Thread> threads = new ArrayList<>();

    @Nullable
    private SelectionTracker<Long> mSelectionTracker;

    public ThreadsViewAdapter(@NonNull Context context,
                              @NonNull ThreadsViewAdapterListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }

    private static String getCompactString(@NonNull String title) {

        return title.replace("\n", " ");
    }

    public void setSelectionTracker(SelectionTracker<Long> selectionTracker) {
        this.mSelectionTracker = selectionTracker;
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.threads;
    }

    @Override
    @NonNull
    public ThreadsViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                            int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new ThreadViewHolder(this, v);
    }

    long getIdx(int position) {
        return threads.get(position).getIdx();
    }

    private boolean hasSelection() {
        if (mSelectionTracker != null) {
            return mSelectionTracker.hasSelection();
        }
        return false;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        final Thread thread = threads.get(position);


        ThreadViewHolder threadViewHolder = (ThreadViewHolder) holder;

        boolean isSelected = false;
        if (mSelectionTracker != null) {
            if (mSelectionTracker.isSelected(thread.getIdx())) {
                isSelected = true;
            }
        }

        threadViewHolder.bind(isSelected, thread);
        try {
            if (isSelected) {
                threadViewHolder.view.setBackgroundResource(R.color.colorSelectedItem);
            } else {
                threadViewHolder.view.setBackgroundColor(
                        android.R.drawable.list_selector_background);
            }

            int resId = MimeTypeService.getMediaResource(thread.getMimeType());

            String mimeType = thread.getMimeType();
            if (mimeType.startsWith(MimeTypeService.VIDEO) ||
                    mimeType.startsWith(MimeTypeService.IMAGE)) {
                Glide.with(mContext).
                        load(thread.getUri()).
                        placeholder(resId).
                        error(resId).
                        into(threadViewHolder.main_image);
            } else {
                threadViewHolder.main_image.setImageResource(resId);
            }

            threadViewHolder.view.setOnClickListener((v) -> {
                try {
                    mListener.onClick(thread);
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }
            });


            String title = getCompactString(thread.getName());
            threadViewHolder.name.setText(title);
            String info = getSize(thread);
            threadViewHolder.size.setText(info);
            Date date = new Date(thread.getLastModified());
            String dateInfo = getDate(date);
            threadViewHolder.date.setText(dateInfo);

            int progress = thread.getProgress();
            if (progress > 0 && progress < 101) {
                threadViewHolder.progress_bar.setProgress(progress);
                threadViewHolder.progress_bar.setVisibility(View.VISIBLE);
            } else {
                threadViewHolder.progress_bar.setVisibility(View.INVISIBLE);
            }

            threadViewHolder.general_action.setVisibility(View.VISIBLE);
            if (hasSelection()) {
                if (isSelected) {
                    threadViewHolder.general_action.setImageResource(
                            R.drawable.check_circle_outline);
                } else {
                    threadViewHolder.general_action.setImageResource(
                            R.drawable.checkbox_blank_circle_outline);
                }
            } else if (thread.isInit()) {
                threadViewHolder.general_action.setVisibility(View.INVISIBLE);
            } else if (thread.isLeaching()) {
                threadViewHolder.general_action.setImageResource(R.drawable.pause);
                threadViewHolder.general_action.setOnClickListener((v) ->
                        mListener.invokePauseAction(thread)
                );

            } else if (thread.isSeeding()) {
                threadViewHolder.general_action.setImageResource(R.drawable.menu_down);
                threadViewHolder.general_action.setOnClickListener((v) ->
                        mListener.invokeAction(thread, v)
                );
            } else {
                threadViewHolder.general_action.setImageResource(R.drawable.download);
                threadViewHolder.general_action.setOnClickListener((v) ->
                        mListener.invokeLoadAction(thread)
                );
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }


    }

    private String getDate(@NonNull Date date) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date today = c.getTime();
        c.set(Calendar.MONTH, 0);
        c.set(Calendar.DAY_OF_MONTH, 0);
        Date lastYear = c.getTime();

        if (date.before(today)) {
            if (date.before(lastYear)) {
                return android.text.format.DateFormat.format("dd.MM.yyyy", date).toString();
            } else {
                return android.text.format.DateFormat.format("dd.MMMM", date).toString();
            }
        } else {
            return android.text.format.DateFormat.format("HH:mm", date).toString();
        }
    }

    private String getSize(@NonNull Thread thread) {

        String fileSize;
        long size = thread.getSize();

        if (size < 1000) {
            fileSize = String.valueOf(size);
            return fileSize + " B";
        } else if (size < 1000 * 1000) {
            fileSize = String.valueOf((double) (size / 1000));
            return fileSize + " KB";
        } else {
            fileSize = String.valueOf((double) (size / (1000 * 1000)));
            return fileSize + " MB";
        }
    }

    @Override
    public int getItemCount() {
        return threads.size();
    }

    public void updateData(@NonNull List<Thread> messageThreads) {

        final ThreadDiffCallback diffCallback = new ThreadDiffCallback(this.threads, messageThreads);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.threads.clear();
        this.threads.addAll(messageThreads);
        diffResult.dispatchUpdatesTo(this);


    }


    public void selectAllThreads() {
        try {
            for (Thread thread : threads) {
                if (mSelectionTracker != null) {
                    mSelectionTracker.select(thread.getIdx());
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public synchronized int getPosition(long idx) {
        for (int i = 0; i < threads.size(); i++) {
            if (threads.get(i).getIdx() == idx) {
                return i;
            }
        }
        return 0;
    }

    public interface ThreadsViewAdapterListener {

        void invokeAction(@NonNull Thread thread, @NonNull View view);

        void onClick(@NonNull Thread thread);

        void invokePauseAction(@NonNull Thread thread);

        void invokeLoadAction(@NonNull Thread thread);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final View view;
        final TextView name;
        final TextView date;
        final TextView size;

        ViewHolder(View v) {
            super(v);
            v.setLongClickable(true);
            v.setClickable(true);
            v.setFocusable(false);
            view = v;
            name = v.findViewById(R.id.name);
            date = v.findViewById(R.id.date);
            size = v.findViewById(R.id.size);
        }
    }


    static class ThreadViewHolder extends ViewHolder {
        final ImageView main_image;
        final ImageView general_action;
        final ProgressBar progress_bar;
        final ThreadItemDetails threadItemDetails;

        ThreadViewHolder(ThreadItemPosition pos, View v) {
            super(v);
            general_action = v.findViewById(R.id.general_action);
            progress_bar = v.findViewById(R.id.progress_bar);
            main_image = v.findViewById(R.id.main_image);
            threadItemDetails = new ThreadItemDetails(pos);

        }

        void bind(boolean isSelected, Thread thread) {

            threadItemDetails.idx = thread.getIdx();

            itemView.setActivated(isSelected);


        }

        ItemDetailsLookup.ItemDetails<Long> getThreadsItemDetails() {

            return threadItemDetails;
        }
    }
}
