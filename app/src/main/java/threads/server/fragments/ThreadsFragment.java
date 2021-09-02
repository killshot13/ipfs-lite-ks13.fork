package threads.server.fragments;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.threads.SortOrder;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.core.threads.ThreadViewModel;
import threads.server.provider.FileProvider;
import threads.server.services.MimeTypeService;
import threads.server.services.QRCodeService;
import threads.server.services.ThreadsService;
import threads.server.utils.Folder;
import threads.server.utils.SelectionViewModel;
import threads.server.utils.ThreadItemDetailsLookup;
import threads.server.utils.ThreadsItemKeyProvider;
import threads.server.utils.ThreadsViewAdapter;
import threads.server.work.CopyDirectoryWorker;
import threads.server.work.CopyFileWorker;
import threads.server.work.PageWorker;
import threads.server.work.UploadContentWorker;


public class ThreadsFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener, ThreadsViewAdapter.ThreadsViewAdapterListener {

    private static final String TAG = ThreadsFragment.class.getSimpleName();


    private static final int CLICK_OFFSET = 500;
    @NonNull
    private final AtomicReference<LiveData<List<Thread>>> observer = new AtomicReference<>(null);
    @NonNull
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SelectionViewModel mSelectionViewModel;
    private ThreadsViewAdapter mThreadsViewAdapter;
    private ThreadViewModel mThreadViewModel;
    private long mLastClickTime = 0;
    private Context mContext;


    private final ActivityResultLauncher<Intent> mDirExportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);

                                    if (!FileProvider.hasWritePermission(mContext, uri)) {
                                        EVENTS.getInstance(mContext).error(
                                                getString(R.string.file_has_no_write_permission));
                                        return;
                                    }
                                    long threadIdx = getThread(mContext);
                                    CopyDirectoryWorker.copyTo(mContext, uri, threadIdx);

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });


    private final ActivityResultLauncher<Intent> mFileExportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);
                                    if (!FileProvider.hasWritePermission(mContext, uri)) {
                                        EVENTS.getInstance(mContext).error(
                                                getString(R.string.file_has_no_write_permission));
                                        return;
                                    }
                                    long threadIdx = getThread(mContext);
                                    CopyFileWorker.copyTo(mContext, uri, threadIdx);

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });
    private FragmentActivity mActivity;
    private ThreadsFragment.ActionListener mListener;
    private RecyclerView mRecyclerView;

    private ActionMode mActionMode;
    private SelectionTracker<Long> mSelectionTracker;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ThreadItemDetailsLookup mThreadItemDetailsLookup;


    private static long getThread(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                TAG, Context.MODE_PRIVATE);
        return sharedPref.getLong(Content.IDX, -1);
    }

    private static void setThread(@NonNull Context context, long idx) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong(Content.IDX, idx);
        editor.apply();
    }

    public static String left(String str, final int len) {
        if (str == null) {
            return "";
        }
        if (len < 0) {
            return "";
        }
        str = str.trim();
        if (str.length() <= len) {
            return str;
        }
        return str.substring(0, len).concat("...");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mActivity = getActivity();
        mListener = (ThreadsFragment.ActionListener) getActivity();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mActivity = null;
        mListener = null;
        releaseActionMode();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectionTracker != null) {
            mSelectionTracker.onSaveInstanceState(outState);
        }

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    public void findInPage() {
        try {
            if (isResumed()) {
                mActionMode = ((AppCompatActivity)
                        mActivity).startSupportActionMode(
                        createSearchActionModeCallback());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void createFolderChips(@NonNull ChipGroup group, @NonNull List<Folder> folders) {

        for (int i = folders.size(); i < group.getChildCount(); i++) {
            group.removeViewAt(i);
        }

        int size = folders.size();
        for (int i = 0; i < size; i++) {
            Folder folder = folders.get(i);

            Chip mChip = (Chip) group.getChildAt(i);

            if (mChip == null) {

                mChip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_folder,
                        group, false);
                group.addView(mChip);
            }

            mChip.setText(folder.getName());
            mChip.setChipBackgroundColorResource(R.color.colorChips);

            mChip.setOnCheckedChangeListener((compoundButton, b) -> {

                mSelectionTracker.clearSelection();
                mSelectionViewModel.setParentThread(folder.getIdx());
            });

        }

    }

    private List<Folder> createFolders(long idx) {
        THREADS threads = THREADS.getInstance(mContext);
        List<Folder> folders = new ArrayList<>();
        List<Thread> ancestors = threads.getAncestors(idx);
        for (Thread thread : ancestors) {

            String name = left(thread.getName(), 20);

            folders.add(new Folder(name, thread.getIdx()));
        }
        return folders;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.threads_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        ChipGroup chipGroup = view.findViewById(R.id.folder_group);
        HorizontalScrollView scrollView = view.findViewById(R.id.folder_scroll_view);

        ImageView homeAction = view.findViewById(R.id.home_action);
        homeAction.setOnClickListener((v) -> {

            mSelectionTracker.clearSelection();
            mSelectionViewModel.setParentThread(0L);

        });

        scrollView.setVisibility(View.GONE);


        mSelectionViewModel = new ViewModelProvider(mActivity).get(SelectionViewModel.class);

        mSelectionViewModel.getParentThread().observe(getViewLifecycleOwner(), (threadIdx) -> {

            if (threadIdx != null) {

                createFolderChips(chipGroup, createFolders(threadIdx));

                if (threadIdx == 0L) {
                    scrollView.setVisibility(View.GONE);
                } else {
                    scrollView.setVisibility(View.VISIBLE);
                }

                SortOrder sortOrder = Settings.getSortOrder(mContext);

                updateDirectory(threadIdx,
                        mSelectionViewModel.getQuery().getValue(), sortOrder, false);


                scrollView.post(() -> scrollView.scrollTo(chipGroup.getRight(), chipGroup.getTop()));
            }

        });

        mSelectionViewModel.getQuery().observe(getViewLifecycleOwner(), (query) -> {

            if (query != null) {
                Long parent = mSelectionViewModel.getParentThread().getValue();
                SortOrder sortOrder = Settings.getSortOrder(mContext);
                updateDirectory(parent, query, sortOrder, false);
            }

        });

        mThreadViewModel = new ViewModelProvider(this).get(ThreadViewModel.class);

        mRecyclerView = view.findViewById(R.id.recycler_view_message_list);


        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        mRecyclerView.setLayoutManager(linearLayoutManager);

        mRecyclerView.setItemAnimator(null);

        mThreadsViewAdapter = new ThreadsViewAdapter(mContext, this);
        mRecyclerView.setAdapter(mThreadsViewAdapter);


        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                boolean hasSelection = mSelectionTracker.hasSelection();
                if (dy > 0 && !hasSelection) {
                    mListener.showFab(false);
                } else if (dy < 0 && !hasSelection) {
                    mListener.showFab(true);
                }

            }
        });

        mThreadItemDetailsLookup = new ThreadItemDetailsLookup(mRecyclerView);


        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);


        mSelectionTracker = new SelectionTracker.Builder<>(TAG, mRecyclerView,
                new ThreadsItemKeyProvider(mThreadsViewAdapter),
                mThreadItemDetailsLookup,
                StorageStrategy.createLongStorage())
                .build();


        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onSelectionChanged() {
                if (!mSelectionTracker.hasSelection()) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                } else {
                    if (mActionMode == null) {
                        mActionMode = ((AppCompatActivity)
                                mActivity).startSupportActionMode(
                                createActionModeCallback());
                    }
                }
                if (mActionMode != null) {
                    mActionMode.setTitle("" + mSelectionTracker.getSelection().size());
                }
                super.onSelectionChanged();
            }

            @Override
            public void onSelectionRestored() {
                if (!mSelectionTracker.hasSelection()) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                } else {
                    if (mActionMode == null) {
                        mActionMode = ((AppCompatActivity)
                                mActivity).startSupportActionMode(
                                createActionModeCallback());
                    }
                }
                if (mActionMode != null) {
                    mActionMode.setTitle("" + mSelectionTracker.getSelection().size());
                }
                super.onSelectionRestored();
            }
        });

        mThreadsViewAdapter.setSelectionTracker(mSelectionTracker);


        if (savedInstanceState != null) {
            mSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }

    }


    public void updateDirectory(@Nullable Long parent, String query,
                                @NonNull SortOrder sortOrder, boolean forceScrollToTop) {
        try {

            LiveData<List<Thread>> obs = observer.get();
            if (obs != null) {
                obs.removeObservers(getViewLifecycleOwner());
            }

            if (parent == null) {
                parent = 0L;
            }

            LiveData<List<Thread>> liveData =
                    mThreadViewModel.getVisibleChildrenByQuery(parent, query);
            observer.set(liveData);


            liveData.observe(getViewLifecycleOwner(), (threads) -> {

                if (threads != null) {

                    switch (sortOrder) {
                        case DATE: {
                            threads.sort(Comparator.comparing(Thread::getLastModified).reversed());
                            break;
                        }
                        case DATE_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getLastModified));
                            break;
                        }
                        case SIZE: {
                            threads.sort(Comparator.comparing(Thread::getSize));
                            break;
                        }
                        case SIZE_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getSize).reversed());
                            break;
                        }
                        case NAME: {
                            threads.sort(Comparator.comparing(Thread::getName));
                            break;
                        }
                        case NAME_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getName).reversed());
                            break;
                        }
                        default:
                            threads.sort(Comparator.comparing(Thread::getLastModified).reversed());
                    }


                    int size = mThreadsViewAdapter.getItemCount();
                    boolean scrollToTop = size < threads.size();


                    mThreadsViewAdapter.updateData(threads);

                    if (scrollToTop || forceScrollToTop) {
                        try {
                            mRecyclerView.scrollToPosition(0);
                        } catch (Throwable e) {
                            LogUtils.error(TAG, e);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    private long[] convert(Selection<Long> entries) {
        int i = 0;

        long[] basic = new long[entries.size()];
        for (Long entry : entries) {
            basic[i] = entry;
            i++;
        }

        return basic;
    }

    private void deleteAction() {

        final EVENTS events = EVENTS.getInstance(mContext);

        if (!mSelectionTracker.hasSelection()) {
            events.warning(getString(R.string.no_marked_file_delete));
            return;
        }


        try {
            long[] entries = convert(mSelectionTracker.getSelection());

            ThreadsService.removeThreads(mContext, entries);

            mSelectionTracker.clearSelection();

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    @Override
    public void invokeAction(@NonNull Thread thread, @NonNull View view) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {
            boolean isSeeding = thread.isSeeding();

            PopupMenu menu = new PopupMenu(mContext, view);
            menu.inflate(R.menu.popup_threads_menu);
            menu.getMenu().findItem(R.id.popup_rename).setVisible(true);
            menu.getMenu().findItem(R.id.popup_share).setVisible(true);
            menu.getMenu().findItem(R.id.popup_delete).setVisible(true);
            menu.getMenu().findItem(R.id.popup_copy_to).setVisible(isSeeding);

            menu.setOnMenuItemClickListener((item) -> {

                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                if (item.getItemId() == R.id.popup_info) {
                    clickThreadInfo(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_delete) {
                    clickThreadDelete(thread.getIdx());
                    return true;
                } else if (item.getItemId() == R.id.popup_share) {
                    clickThreadShare(thread.getIdx());
                    return true;
                } else if (item.getItemId() == R.id.popup_copy_to) {
                    clickThreadCopy(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_rename) {
                    clickThreadRename(thread);
                    return true;
                } else {
                    return false;
                }
            });

            menu.show();


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }


    }


    private void clickThreadRename(@NonNull Thread thread) {
        try {
            RenameFileDialogFragment.newInstance(thread.getIdx(), thread.getName()).
                    show(getChildFragmentManager(), RenameFileDialogFragment.TAG);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void clickThreadShare(long idx) {
        final EVENTS events = EVENTS.getInstance(mContext);
        final THREADS threads = THREADS.getInstance(mContext);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread thread = threads.getThreadByIdx(idx);
                Objects.requireNonNull(thread);
                ComponentName[] names = {new ComponentName(
                        mContext.getApplicationContext(), MainActivity.class)};
                Uri uri = DOCS.getInstance(mContext).getPath(thread);

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                intent.setType(MimeTypeService.PLAIN_MIME_TYPE);
                intent.putExtra(Intent.EXTRA_SUBJECT, thread.getName());
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());


                Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);


            } catch (Throwable ignore) {
                events.warning(getString(R.string.no_activity_found_to_handle_uri));
            }
        });


    }

    private void clickThreadCopy(@NonNull Thread thread) {

        setThread(mContext, thread.getIdx());
        try {
            if (thread.isDir()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                mDirExportForResult.launch(intent);
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType(thread.getMimeType());
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                mFileExportForResult.launch(intent);
            }
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }

    }

    private ActionMode.Callback createActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_threads_action_mode, menu);

                MenuItem action_delete = menu.findItem(R.id.action_mode_delete);
                action_delete.setVisible(true);

                mListener.showFab(false);

                mHandler.post(() -> mThreadsViewAdapter.notifyDataSetChanged());

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {


                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_mode_mark_all) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    mThreadsViewAdapter.selectAllThreads();

                    return true;
                } else if (itemId == R.id.action_mode_delete) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    deleteAction();

                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

                mSelectionTracker.clearSelection();

                mListener.showFab(true);

                if (mActionMode != null) {
                    mActionMode = null;
                }
                mHandler.post(() -> mThreadsViewAdapter.notifyDataSetChanged());

            }
        };

    }

    @Override
    public void onClick(@NonNull Thread thread) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {
            if (!mSelectionTracker.hasSelection()) {

                mListener.showFab(true);

                if (mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }

                if (thread.isDir()) {
                    mSelectionViewModel.setParentThread(thread.getIdx());
                } else {
                    clickThreadPlay(thread);
                }

            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    private void clickThreadInfo(@NonNull Thread thread) {
        try {
            String cid = thread.getContent();
            Objects.requireNonNull(cid);
            String uri = Content.IPFS + "://" + cid;

            Uri uriImage = QRCodeService.getImage(mContext, uri);
            ContentDialogFragment.newInstance(uriImage,
                    getString(R.string.url_data_access, thread.getName()), uri)
                    .show(getChildFragmentManager(), ContentDialogFragment.TAG);


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    private void clickThreadPlay(@NonNull Thread thread) {


        try {

            if (thread.isSeeding()) {

                String cid = thread.getContent();
                Objects.requireNonNull(cid);


                String mimeType = thread.getMimeType();

                // special case
                if (Objects.equals(mimeType, MimeTypeService.URL_MIME_TYPE)) {
                    IPFS ipfs = IPFS.getInstance(mContext);
                    Uri uri = Uri.parse(ipfs.getText(Cid.decode(cid), () -> false));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return;
                } else if (Objects.equals(mimeType, MimeTypeService.HTML_MIME_TYPE)) {
                    Uri uri = DOCS.getInstance(mContext).getIpnsPath(thread, false);
                    mListener.openBrowserView(uri);
                    return;
                }
                Uri uri = DOCS.getInstance(mContext).getIpnsPath(thread, true);
                //Uri uri = DOCS.getInstance(mContext).getPath(thread);
                mListener.openBrowserView(uri);

            }
        } catch (Throwable ignore) {
            EVENTS.getInstance(mContext).warning(getString(R.string.no_activity_found_to_handle_uri));
        }

    }

    @Override
    public void invokePauseAction(@NonNull Thread thread) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();


        UUID uuid = thread.getWorkUUID();
        if (uuid != null) {
            WorkManager.getInstance(mContext).cancelWorkById(uuid);
        }


        Executors.newSingleThreadExecutor().submit(() -> {
            THREADS threads = THREADS.getInstance(mContext);
            threads.resetThreadLeaching(thread.getIdx());
        });

    }

    @Override
    public void invokeLoadAction(@NonNull Thread thread) {


        UUID uuid = UploadContentWorker.load(mContext, thread.getIdx(), true);

        Executors.newSingleThreadExecutor().submit(() -> {
            THREADS threads = THREADS.getInstance(mContext);
            threads.setThreadLeaching(thread.getIdx());
            threads.setThreadWork(thread.getIdx(), uuid);
        });


    }


    private void clickThreadDelete(long idx) {
        ThreadsService.removeThreads(mContext, idx);
    }


    private ActionMode.Callback createSearchActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_search_action_mode, menu);

                mThreadItemDetailsLookup.setActive(false);

                MenuItem searchMenuItem = menu.findItem(R.id.action_search);

                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();

                mSearchView.setIconifiedByDefault(false);
                mSearchView.setFocusable(true);
                mSearchView.setFocusedByDefault(true);
                String query = mSelectionViewModel.getQuery().getValue();
                Objects.requireNonNull(query);
                mSearchView.setQuery(query, true);
                mSearchView.setIconified(false);
                mSearchView.requestFocus();


                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {

                        mSelectionViewModel.getQuery().setValue(query);
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {

                        mSelectionViewModel.getQuery().setValue(newText);
                        return false;
                    }
                });

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    mThreadItemDetailsLookup.setActive(true);
                    mSelectionViewModel.setQuery("");

                    if (mActionMode != null) {
                        mActionMode = null;
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        };

    }

    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {
            if (Settings.isPublisherEnabled(mContext)) {
                EVENTS.getInstance(mContext).warning(getString(R.string.publish_files));

                PageWorker.publish(mContext);
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }

    public void enableSwipeRefresh(boolean enable) {
        try {
            if (isResumed()) {
                mSwipeRefreshLayout.setEnabled(enable);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void releaseActionMode() {
        try {
            if (isResumed()) {
                if (mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public interface ActionListener {

        void showFab(boolean b);

        void openBrowserView(@NonNull Uri uri);
    }
}
