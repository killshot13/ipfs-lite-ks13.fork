package threads.server;


import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.io.PrintStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.DeleteOperation;
import threads.server.core.books.BOOKS;
import threads.server.core.books.Bookmark;
import threads.server.core.events.EVENTS;
import threads.server.core.events.EventViewModel;
import threads.server.core.threads.SortOrder;
import threads.server.core.threads.THREADS;
import threads.server.fragments.BookmarksDialogFragment;
import threads.server.fragments.BrowserFragment;
import threads.server.fragments.ContentDialogFragment;
import threads.server.fragments.EditContentDialogFragment;
import threads.server.fragments.NewFolderDialogFragment;
import threads.server.fragments.SettingsDialogFragment;
import threads.server.fragments.TextDialogFragment;
import threads.server.fragments.ThreadsFragment;
import threads.server.provider.FileProvider;
import threads.server.services.DaemonService;
import threads.server.services.DiscoveryService;
import threads.server.services.LiteService;
import threads.server.services.LocalConnectService;
import threads.server.services.MimeTypeService;
import threads.server.services.QRCodeService;
import threads.server.services.RegistrationService;
import threads.server.services.UploadService;
import threads.server.utils.BookmarksAdapter;
import threads.server.utils.CodecDecider;
import threads.server.utils.PermissionAction;
import threads.server.utils.SelectionViewModel;
import threads.server.work.BackupWorker;
import threads.server.work.DeleteThreadsWorker;
import threads.server.work.DownloadContentWorker;
import threads.server.work.SwarmConnectWorker;
import threads.server.work.UploadFilesWorker;
import threads.server.work.UploadFolderWorker;


public class MainActivity extends AppCompatActivity implements
        ThreadsFragment.ActionListener,
        BrowserFragment.ActionListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FRAG = "FRAG";
    private final AtomicInteger currentFragment = new AtomicInteger(R.id.navigation_browser);
    private final ActivityResultLauncher<Intent> mContentForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    try {
                        Objects.requireNonNull(data);
                        Uri uri = data.getData();
                        Objects.requireNonNull(uri);
                        if (!FileProvider.hasWritePermission(getApplicationContext(), uri)) {
                            EVENTS.getInstance(getApplicationContext()).error(
                                    getString(R.string.file_has_no_write_permission));
                            return;
                        }
                        Uri contentUri = LiteService.getContentUri(getApplicationContext());
                        Objects.requireNonNull(contentUri);
                        DownloadContentWorker.download(getApplicationContext(), uri, contentUri);

                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });
    private final ActivityResultLauncher<Intent> mFilesImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);

                                if (data.getClipData() != null) {
                                    ClipData mClipData = data.getClipData();
                                    long parent = getThread(getApplicationContext());
                                    LiteService.files(getApplicationContext(), mClipData, parent);

                                } else if (data.getData() != null) {
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);
                                    if (!FileProvider.hasReadPermission(getApplicationContext(), uri)) {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.file_has_no_read_permission));
                                        return;
                                    }

                                    if (FileProvider.isPartial(getApplicationContext(), uri)) {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.file_not_valid));
                                        return;
                                    }

                                    long parent = getThread(getApplicationContext());

                                    LiteService.file(getApplicationContext(), parent, uri);
                                }

                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private final ActivityResultLauncher<Intent> mFolderImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);
                                long parent = getThread(getApplicationContext());
                                if (data.getClipData() != null) {
                                    ClipData mClipData = data.getClipData();
                                    int items = mClipData.getItemCount();
                                    if (items > 0) {
                                        for (int i = 0; i < items; i++) {
                                            ClipData.Item item = mClipData.getItemAt(i);
                                            Uri uri = item.getUri();

                                            if (!FileProvider.hasReadPermission(getApplicationContext(), uri)) {
                                                EVENTS.getInstance(getApplicationContext()).error(
                                                        getString(R.string.file_has_no_read_permission));
                                                return;
                                            }

                                            if (FileProvider.isPartial(getApplicationContext(), uri)) {
                                                EVENTS.getInstance(getApplicationContext()).error(
                                                        getString(R.string.file_not_valid));
                                                return;
                                            }

                                            UploadFolderWorker.load(getApplicationContext(), parent, uri);
                                        }
                                    }
                                } else {
                                    Uri uri = data.getData();
                                    if (uri != null) {
                                        if (!FileProvider.hasReadPermission(getApplicationContext(), uri)) {
                                            EVENTS.getInstance(getApplicationContext()).error(
                                                    getString(R.string.file_has_no_read_permission));
                                            return;
                                        }

                                        if (FileProvider.isPartial(getApplicationContext(), uri)) {
                                            EVENTS.getInstance(getApplicationContext()).error(
                                                    getString(R.string.file_not_valid));
                                            return;
                                        }

                                        UploadFolderWorker.load(getApplicationContext(), parent, uri);
                                    }
                                }
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private final ActivityResultLauncher<Intent> mBackupForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);
                                Uri uri = data.getData();
                                Objects.requireNonNull(uri);


                                if (!FileProvider.hasWritePermission(getApplicationContext(), uri)) {
                                    EVENTS.getInstance(getApplicationContext()).error(
                                            getString(R.string.file_has_no_write_permission));
                                    return;
                                }
                                BackupWorker.backup(getApplicationContext(), uri);

                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private final AtomicBoolean downloadActive = new AtomicBoolean(false);
    private ConnectivityManager.NetworkCallback networkCallback;
    private long mLastClickTime = 0;
    private CoordinatorLayout mDrawerLayout;
    private ImageView mActionBookmarks;
    private ImageView mActionDaemon;
    private ImageButton mActionHome;
    private NsdManager mNsdManager;
    private FloatingActionButton mFloatingActionButton;
    private SelectionViewModel mSelectionViewModel;
    private TextView mBrowserText;
    private ActionMode mActionMode;
    private ImageButton mActionBookmark;
    private BrowserFragment mBrowserFragment;
    private ThreadsFragment mThreadsFragment;
    private final ActivityResultLauncher<Intent> mScanRequestForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    IntentResult resultIntent = IntentIntegrator.parseActivityResult(
                            IntentIntegrator.REQUEST_CODE, result.getResultCode(), result.getData());
                    if (resultIntent != null) {
                        if (resultIntent.getContents() != null) {

                            try {
                                Uri uri = Uri.parse(resultIntent.getContents());
                                if (uri != null) {
                                    String scheme = uri.getScheme();
                                    if (Objects.equals(scheme, Content.IPNS) ||
                                            Objects.equals(scheme, Content.IPFS) ||
                                            Objects.equals(scheme, Content.HTTP) ||
                                            Objects.equals(scheme, Content.HTTPS)) {
                                        openBrowserView(uri);
                                    } else {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.codec_not_supported));
                                    }
                                } else {
                                    EVENTS.getInstance(getApplicationContext()).error(
                                            getString(R.string.codec_not_supported));
                                }
                            } catch (Throwable throwable) {
                                EVENTS.getInstance(getApplicationContext()).error(
                                        getString(R.string.codec_not_supported));
                            }
                        }
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            });
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    invokeScan();
                } else {
                    EVENTS.getInstance(getApplicationContext()).permission(
                            getString(R.string.permission_camera_denied));
                }
            });
    private boolean hasCamera;


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

    public void unRegisterNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    try {
                        ConnectivityManager connectivityManager =
                                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

                        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                        String interfaceName = null;
                        if (linkProperties != null) {
                            interfaceName = linkProperties.getInterfaceName();
                        }

                        IPFS ipfs = IPFS.getInstance(getApplicationContext());
                        if (interfaceName != null) {
                            ipfs.updateNetwork(interfaceName);
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    } finally {
                        SwarmConnectWorker.dialing(getApplicationContext());
                    }

                }

                @Override
                public void onLost(Network network) {
                }
            };


            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (mNsdManager != null) {
                mNsdManager.unregisterService(RegistrationService.getInstance());
                mNsdManager.stopServiceDiscovery(DiscoveryService.getInstance());
            }
            unRegisterNetworkCallback();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void registerService(int port) {
        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            String peerID = ipfs.getPeerID().toBase58();
            Objects.requireNonNull(peerID);
            String serviceType = "_ipfs-discovery._udp";
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(peerID);
            serviceInfo.setServiceType(serviceType);
            serviceInfo.setPort(port);
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            Objects.requireNonNull(mNsdManager);
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD,
                    RegistrationService.getInstance());


            DiscoveryService discovery = DiscoveryService.getInstance();
            discovery.setOnServiceFoundListener((info) -> mNsdManager.resolveService(info, new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {

                }


                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {

                    try {

                        String serviceName = serviceInfo.getServiceName();
                        boolean connect = !Objects.equals(peerID, serviceName);
                        if (connect) {
                            InetAddress inetAddress = serviceInfo.getHost();
                            LocalConnectService.connect(getApplicationContext(),
                                    serviceName, serviceInfo.getHost().toString(),
                                    serviceInfo.getPort(), inetAddress instanceof Inet6Address);
                        }

                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                }
            }));
            mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void setFabImage(@DrawableRes int resId) {
        mFloatingActionButton.setImageResource(resId);
    }

    public void showFab(boolean visible) {

        if (visible) {
            int value = currentFragment.intValue();
            if (value == R.id.navigation_files) {
                mFloatingActionButton.show();
            } else {
                mFloatingActionButton.hide();
            }
        } else {
            mFloatingActionButton.hide();
        }

    }

    private void setSortOrder(@NonNull SortOrder sortOrder) {
        Settings.setSortOrder(getApplicationContext(), sortOrder);
    }

    private void clickFilesAdd() {

        try {
            Long idx = mSelectionViewModel.getParentThread().getValue();
            Objects.requireNonNull(idx);

            setThread(getApplicationContext(), idx);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(MimeTypeService.ALL);
            String[] mimeTypes = {MimeTypeService.ALL};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            mFilesImportForResult.launch(intent);

        } catch (Throwable e) {
            EVENTS.getInstance(getApplicationContext()).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntents(intent);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                BrowserFragment.class.getSimpleName());
        if (fragment instanceof BrowserFragment) {
            BrowserFragment browserFragment = (BrowserFragment) fragment;
            if (browserFragment.isResumed()) {
                boolean result = browserFragment.onBackPressed();
                if (result) {
                    return;
                }
            }
        }
        super.onBackPressed();
    }

    private void handleIntents(Intent intent) {

        final String action = intent.getAction();
        try {
            ShareCompat.IntentReader intentReader = new ShareCompat.IntentReader(this);
            if (Intent.ACTION_SEND.equals(action) ||
                    Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                handleSend(intentReader);
            } else if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String scheme = uri.getScheme();
                    if (Objects.equals(scheme, Content.IPNS) ||
                            Objects.equals(scheme, Content.IPFS) ||
                            Objects.equals(scheme, Content.HTTP) ||
                            Objects.equals(scheme, Content.HTTPS)) {
                        openBrowserView(uri);
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }


    public void openBrowserView(@NonNull Uri uri) {
        try {
            mSelectionViewModel.setUri(uri.toString());
            showFragment(R.id.navigation_browser);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    private void handleSend(ShareCompat.IntentReader intentReader) {

        try {
            Objects.requireNonNull(intentReader);
            if (intentReader.isMultipleShare()) {
                int items = intentReader.getStreamCount();

                if (items > 0) {
                    FileProvider fileProvider =
                            FileProvider.getInstance(getApplicationContext());
                    File file = fileProvider.createTempDataFile();

                    try (PrintStream out = new PrintStream(file)) {
                        for (int i = 0; i < items; i++) {
                            Uri uri = intentReader.getStream(i);
                            if (uri != null) {
                                out.println(uri.toString());
                            }
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            getApplicationContext(), BuildConfig.APPLICATION_ID, file);
                    Objects.requireNonNull(uri);
                    UploadFilesWorker.load(getApplicationContext(), 0L, uri);
                }
            } else {
                String type = intentReader.getType();
                if (Objects.equals(type, MimeTypeService.PLAIN_MIME_TYPE)) {
                    CharSequence textObject = intentReader.getText();
                    Objects.requireNonNull(textObject);
                    String text = textObject.toString();
                    if (!text.isEmpty()) {

                        Uri uri = Uri.parse(text);
                        if (uri != null) {
                            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                                    Objects.equals(uri.getScheme(), Content.IPNS) ||
                                    Objects.equals(uri.getScheme(), Content.HTTP) ||
                                    Objects.equals(uri.getScheme(), Content.HTTPS)) {
                                openBrowserView(uri);
                                return;
                            }
                        }

                        CodecDecider result = CodecDecider.evaluate(getApplicationContext(), text);

                        if (result.getCodex() == CodecDecider.Codec.MULTIHASH) {

                            EditContentDialogFragment.newInstance(result.getMultihash(),
                                    false).show(
                                    getSupportFragmentManager(), EditContentDialogFragment.TAG);

                        } else if (result.getCodex() == CodecDecider.Codec.IPFS_URI) {

                            EditContentDialogFragment.newInstance(result.getMultihash(),
                                    false).show(
                                    getSupportFragmentManager(), EditContentDialogFragment.TAG);

                        } else if (result.getCodex() == CodecDecider.Codec.IPNS_URI) {
                            openBrowserView(Uri.parse(text));
                        } else {
                            if (URLUtil.isValidUrl(text)) {
                                openBrowserView(Uri.parse(text));
                            } else {
                                UploadService.storeText(
                                        getApplicationContext(), 0L, text, false);
                            }
                        }
                    }
                } else if (Objects.equals(type, MimeTypeService.HTML_MIME_TYPE)) {
                    String html = intentReader.getHtmlText();
                    Objects.requireNonNull(html);
                    if (!html.isEmpty()) {
                        UploadService.storeText(
                                getApplicationContext(), 0L, html, false);
                    }
                } else {
                    Uri uri = intentReader.getStream();
                    Objects.requireNonNull(uri);

                    if (!FileProvider.hasReadPermission(getApplicationContext(), uri)) {
                        EVENTS.getInstance(getApplicationContext()).error(
                                getString(R.string.file_has_no_read_permission));
                        return;
                    }

                    if (FileProvider.isPartial(getApplicationContext(), uri)) {

                        EVENTS.getInstance(getApplicationContext()).error(
                                getString(R.string.file_not_found));

                        return;
                    }

                    LiteService.file(getApplicationContext(), 0L, uri);

                }
            }


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(FRAG, currentFragment.intValue());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentFragment.set(savedInstanceState.getInt(FRAG));
    }

    public void updateBookmark(@NonNull Uri uri) {
        try {

            BOOKS books = BOOKS.getInstance(getApplicationContext());
            if (books.hasBookmark(uri.toString())) {
                Drawable drawable = AppCompatResources.getDrawable(getApplicationContext(),
                        R.drawable.star);
                mActionBookmark.setImageDrawable(drawable);
            } else {
                Drawable drawable = AppCompatResources.getDrawable(getApplicationContext(),
                        R.drawable.star_outline);
                mActionBookmark.setImageDrawable(drawable);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private ActionMode.Callback createSearchActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_searchable, menu);
                mode.setCustomView(null);
                mode.setTitle("");
                mode.setTitleOptionalHint(true);


                MenuItem scanMenuItem = menu.findItem(R.id.action_scan);
                if (!hasCamera) {
                    scanMenuItem.setVisible(false);
                }
                MenuItem searchMenuItem = menu.findItem(R.id.action_search);
                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();
                mSearchView.setMaxWidth(Integer.MAX_VALUE);

                TextView textView = mSearchView.findViewById(
                        androidx.appcompat.R.id.search_src_text);
                textView.setTextSize(14);

                ImageView magImage = mSearchView.findViewById(
                        androidx.appcompat.R.id.search_mag_icon);
                magImage.setVisibility(View.GONE);
                magImage.setImageDrawable(null);

                mSearchView.setIconifiedByDefault(false);
                mSearchView.setIconified(false);
                mSearchView.setSubmitButtonEnabled(false);
                mSearchView.setQueryHint(getString(R.string.enter_url));
                mSearchView.setFocusable(true);
                mSearchView.requestFocus();


                ListPopupWindow mPopupWindow = new ListPopupWindow(MainActivity.this,
                        null, R.attr.popupMenuStyle) {

                    @Override
                    public boolean isInputMethodNotNeeded() {
                        return true;
                    }
                };
                mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                mPopupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                mPopupWindow.setModal(false);
                mPopupWindow.setAnchorView(mSearchView);
                mPopupWindow.setAnimationStyle(android.R.style.Animation);


                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {

                        try {
                            mPopupWindow.dismiss();

                            if (mActionMode != null) {
                                mActionMode.finish();
                            }
                            if (query != null && !query.isEmpty()) {
                                Uri uri = Uri.parse(query);
                                String scheme = uri.getScheme();
                                if (Objects.equals(scheme, Content.IPNS) ||
                                        Objects.equals(scheme, Content.IPFS) ||
                                        Objects.equals(scheme, Content.HTTP) ||
                                        Objects.equals(scheme, Content.HTTPS)) {
                                    openBrowserView(uri);
                                } else {

                                    IPFS ipfs = IPFS.getInstance(getApplicationContext());
                                    if (ipfs.isValidCID(query)) {
                                        openBrowserView(Uri.parse(Content.IPFS + "://" + query));
                                    } else {
                                        openBrowserView(Uri.parse(
                                                Settings.getDefaultSearchEngine(query)));
                                    }
                                }
                            }
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {

                        if (!newText.isEmpty()) {
                            BOOKS books = BOOKS.getInstance(getApplicationContext());
                            List<Bookmark> bookmarks = books.getBookmarksByQuery(newText);

                            if (!bookmarks.isEmpty()) {
                                mPopupWindow.setAdapter(new BookmarksAdapter(getApplicationContext(),
                                        new ArrayList<>(bookmarks)) {
                                    @Override
                                    public void onClick(@NonNull Bookmark bookmark) {
                                        try {
                                            openBrowserView(Uri.parse(bookmark.getUri()));
                                        } catch (Throwable throwable) {
                                            LogUtils.error(TAG, throwable);
                                        } finally {
                                            mPopupWindow.dismiss();
                                            releaseActionMode();
                                        }
                                    }
                                });
                                mPopupWindow.show();
                                return true;
                            } else {
                                mPopupWindow.dismiss();
                            }
                        } else {
                            mPopupWindow.dismiss();
                        }

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
                if (item.getItemId() == R.id.action_scan) {
                    try {
                        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                            return false;
                        }
                        mLastClickTime = SystemClock.elapsedRealtime();


                        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                            return false;
                        }

                        invokeScan();

                        if (hasCamera) {
                            IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
                            integrator.setOrientationLocked(false);
                            Intent intent = integrator.createScanIntent();
                            mScanRequestForResult.launch(intent);
                        } else {
                            EVENTS.getInstance(getApplicationContext()).permission(
                                    getString(R.string.feature_camera_required));
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    } finally {
                        mode.finish();
                    }
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        };

    }

    private int border() {
        float density = getApplicationContext().getResources()
                .getDisplayMetrics().density;
        return Math.round((float) 48 * density);
    }

    private void updateHome() {
        if(currentFragment.get() == R.id.navigation_files) {
            if (!Settings.isPublisherEnabled(getApplicationContext())) {
                mActionHome.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        android.R.color.holo_red_dark), PorterDuff.Mode.SRC_ATOP);
            } else {
                mActionHome.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary), PorterDuff.Mode.SRC_ATOP);
            }
        } else {
            mActionHome.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.black), PorterDuff.Mode.SRC_ATOP);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean darkTheme = isDarkTheme();
        if (!darkTheme) {
            setLightStatusBar(this);
        }

        DOCS docs = DOCS.getInstance(getApplicationContext());
        docs.darkMode.set(darkTheme);


        PackageManager pm = getPackageManager();
        hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        mDrawerLayout = findViewById(R.id.drawer_layout);

        mFloatingActionButton = findViewById(R.id.floating_action_button);


        AppBarLayout mAppBar = findViewById(R.id.appbar);


        mAppBar.addOnOffsetChangedListener(new AppBarStateChangedListener() {
            @Override
            public void onStateChanged(State state) {
                if (state == State.EXPANDED) {
                    enableSwipeRefresh(true);
                } else if (state == State.COLLAPSED) {
                    enableSwipeRefresh(false);
                }
            }
        });


        if (savedInstanceState != null) {

            mBrowserFragment = (BrowserFragment) getSupportFragmentManager().
                    findFragmentByTag(BrowserFragment.class.getSimpleName());
            mThreadsFragment = (ThreadsFragment) getSupportFragmentManager().
                    findFragmentByTag(ThreadsFragment.class.getSimpleName());

        } else {
            mBrowserFragment = new BrowserFragment();
            mThreadsFragment = new ThreadsFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, mThreadsFragment, ThreadsFragment.class.getSimpleName())
                    .add(R.id.fragment_container, mBrowserFragment, BrowserFragment.class.getSimpleName())
                    .hide(mBrowserFragment)
                    .hide(mThreadsFragment)
                    .commit();
        }

        mActionHome = findViewById(R.id.action_home);
        mActionBookmark = findViewById(R.id.action_bookmark);
        mActionBookmark.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                mBrowserFragment.bookmark(getApplicationContext(), mActionBookmark);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        updateHome();

        mActionBookmarks = findViewById(R.id.action_bookmarks);
        mActionBookmarks.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                BookmarksDialogFragment dialogFragment = new BookmarksDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), BookmarksDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        mActionDaemon = findViewById(R.id.action_daemon);
        mActionDaemon.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                DaemonService.start(getApplicationContext());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        ImageView mActionOverflow = findViewById(R.id.action_overflow);
        mActionOverflow.setOnClickListener(v -> {
            if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);


            View menuOverflow = inflater.inflate(
                    R.layout.menu_overflow, mDrawerLayout, false);

            PopupWindow dialog = new PopupWindow(
                    MainActivity.this, null, R.attr.popupMenuStyle);
            dialog.setContentView(menuOverflow);
            dialog.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setOutsideTouchable(true);
            dialog.setFocusable(true);

            dialog.showAsDropDown(mActionOverflow, 0, -border(),
                    Gravity.TOP | Gravity.END);


            int frag = currentFragment.get();
            ImageButton actionNextPage = menuOverflow.findViewById(R.id.action_next_page);
            if (mBrowserFragment.canGoForward()) {
                actionNextPage.setEnabled(true);
                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionNextPage.setEnabled(false);
                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionNextPage.setOnClickListener(v1 -> {
                try {
                    mBrowserFragment.goForward();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            ImageButton actionFindPage = menuOverflow.findViewById(R.id.action_find_page);
            if (frag == R.id.navigation_browser || frag == R.id.navigation_files) {
                actionFindPage.setEnabled(true);
                actionFindPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionFindPage.setEnabled(false);
                actionFindPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionFindPage.setOnClickListener(v12 -> {
                try {
                    if (frag == R.id.navigation_browser) {
                        mBrowserFragment.findInPage();
                    } else if (frag == R.id.navigation_files) {
                        mThreadsFragment.findInPage();
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionDownload = menuOverflow.findViewById(R.id.action_download);

            if (downloadActive.get()) {
                actionDownload.setEnabled(true);
                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionDownload.setEnabled(false);
                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }

            actionDownload.setOnClickListener(v13 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Uri uri = Uri.parse(mBrowserFragment.getUrl());
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.download_title);
                    String filename = docs.getFileName(uri);
                    builder.setMessage(filename);

                    builder.setPositiveButton(getString(android.R.string.yes), (dialogInterface, which) -> {

                        LiteService.setContentUri(getApplicationContext(), uri);

                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                                Uri.parse(Settings.DOWNLOADS));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        mContentForResult.launch(intent);

                    });
                    builder.setNeutralButton(getString(android.R.string.cancel),
                            (dialogInterface, which) -> dialogInterface.cancel());
                    builder.show();

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionShare = menuOverflow.findViewById(R.id.action_share);
            if (mBrowserFragment.getUrl() != null) {
                actionShare.setEnabled(true);
                actionShare.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionShare.setEnabled(false);
                actionShare.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionShare.setOnClickListener(v14 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Uri uri = Uri.parse(mBrowserFragment.getUrl());

                    ComponentName[] names = {new ComponentName(getApplicationContext(), MainActivity.class)};

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                    intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                    intent.setType(MimeTypeService.PLAIN_MIME_TYPE);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                    Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                    chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                    startActivity(chooser);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionReload = menuOverflow.findViewById(R.id.action_reload);

            if (frag == R.id.navigation_browser) {
                actionReload.setEnabled(true);
                actionReload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionReload.setEnabled(false);
                actionReload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionReload.setOnClickListener(v15 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    if (frag == R.id.navigation_browser) {
                        mBrowserFragment.reload();
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            TextView actionClearData = menuOverflow.findViewById(R.id.action_clear_data);

            actionClearData.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle(getString(R.string.warning));
                    alertDialog.setMessage(getString(R.string.delete_browser_data_warning));
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                            (dialogInterface, which) -> {
                                mBrowserFragment.clearBrowserData();
                                dialog.dismiss();
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.cancel),
                            (dialogInterface, which) -> dialog.dismiss());
                    alertDialog.show();

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionInformation = menuOverflow.findViewById(R.id.action_information);
            if (mBrowserFragment.getUrl() != null) {
                actionInformation.setVisibility(View.VISIBLE);
            } else {
                actionInformation.setVisibility(View.GONE);
            }
            actionInformation.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Uri uri = Uri.parse(mBrowserFragment.getUrl());

                    Uri uriImage = QRCodeService.getImage(getApplicationContext(), uri.toString());
                    ContentDialogFragment.newInstance(uriImage,
                            getString(R.string.url_access), uri.toString())
                            .show(getSupportFragmentManager(), ContentDialogFragment.TAG);


                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionScanURL = menuOverflow.findViewById(R.id.action_scan_url);
            if (!hasCamera) {
                actionScanURL.setVisibility(View.GONE);
            }

            actionScanURL.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();


                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                        return;
                    }

                    invokeScan();

                    if (hasCamera) {
                        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
                        integrator.setOrientationLocked(false);
                        Intent intent = integrator.createScanIntent();
                        mScanRequestForResult.launch(intent);
                    } else {
                        EVENTS.getInstance(getApplicationContext()).permission(
                                getString(R.string.feature_camera_required));
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView mActionSorting = menuOverflow.findViewById(R.id.action_sorting);
            if (frag == R.id.navigation_files) {
                mActionSorting.setVisibility(View.VISIBLE);
            } else {
                mActionSorting.setVisibility(View.GONE);
            }
            mActionSorting.setOnClickListener(v22 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    SortOrder sortOrder = Settings.getSortOrder(getApplicationContext());
                    PopupMenu popup = new PopupMenu(MainActivity.this, v);
                    popup.inflate(R.menu.popup_sorting);

                    popup.getMenu().getItem(0).setChecked(sortOrder == SortOrder.NAME);
                    popup.getMenu().getItem(1).setChecked(sortOrder == SortOrder.NAME_INVERSE);
                    popup.getMenu().getItem(2).setChecked(sortOrder == SortOrder.DATE);
                    popup.getMenu().getItem(3).setChecked(sortOrder == SortOrder.DATE_INVERSE);
                    popup.getMenu().getItem(4).setChecked(sortOrder == SortOrder.SIZE);
                    popup.getMenu().getItem(5).setChecked(sortOrder == SortOrder.SIZE_INVERSE);

                    popup.setOnMenuItemClickListener(item -> {
                        try {
                            int itemId = item.getItemId();
                            if (itemId == R.id.sort_date) {

                                setSortOrder(SortOrder.DATE);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.DATE, true);
                                return true;
                            } else if (itemId == R.id.sort_date_inverse) {

                                setSortOrder(SortOrder.DATE_INVERSE);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.DATE_INVERSE, true);
                                return true;
                            } else if (itemId == R.id.sort_name) {

                                setSortOrder(SortOrder.NAME);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.NAME, true);
                                return true;
                            } else if (itemId == R.id.sort_name_inverse) {

                                setSortOrder(SortOrder.NAME_INVERSE);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.NAME_INVERSE, true);
                                return true;
                            } else if (itemId == R.id.sort_size) {

                                setSortOrder(SortOrder.SIZE);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.SIZE, true);
                                return true;
                            } else if (itemId == R.id.sort_size_inverse) {

                                setSortOrder(SortOrder.SIZE_INVERSE);

                                mThreadsFragment.updateDirectory(
                                        mSelectionViewModel.getParentThread().getValue(),
                                        mSelectionViewModel.getQuery().getValue(),
                                        SortOrder.SIZE_INVERSE, true);
                                return true;
                            }
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                        return false;
                    });
                    popup.show();

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }
            });


            TextView actionNewFolder = menuOverflow.findViewById(R.id.action_new_folder);
            if (frag == R.id.navigation_files) {
                actionNewFolder.setVisibility(View.VISIBLE);
            } else {
                actionNewFolder.setVisibility(View.GONE);
            }
            actionNewFolder.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();


                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }

                    NewFolderDialogFragment.newInstance(parent).
                            show(getSupportFragmentManager(), NewFolderDialogFragment.TAG);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionImportFolder = menuOverflow.findViewById(R.id.action_import_folder);
            if (frag == R.id.navigation_files) {
                actionImportFolder.setVisibility(View.VISIBLE);
            } else {
                actionImportFolder.setVisibility(View.GONE);
            }
            actionImportFolder.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }
                    setThread(getApplicationContext(), parent);

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    mFolderImportForResult.launch(intent);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionNewText = menuOverflow.findViewById(R.id.action_new_text);
            if (frag == R.id.navigation_files) {
                actionNewText.setVisibility(View.VISIBLE);
            } else {
                actionNewText.setVisibility(View.GONE);
            }
            actionNewText.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }

                    TextDialogFragment.newInstance(parent).
                            show(getSupportFragmentManager(), TextDialogFragment.TAG);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            TextView actionBackup = menuOverflow.findViewById(R.id.action_backup);
            actionBackup.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    mBackupForResult.launch(intent);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionSettings = menuOverflow.findViewById(R.id.action_settings);
            actionSettings.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();


                    SettingsDialogFragment dialogFragment = new SettingsDialogFragment();
                    dialogFragment.show(getSupportFragmentManager(), SettingsDialogFragment.TAG);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            TextView actionDocumentation = menuOverflow.findViewById(R.id.action_documentation);
            actionDocumentation.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    String uri = "https://gitlab.com/remmer.wilts/ipfs-lite";
                    openBrowserView(Uri.parse(uri));
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


        });

        mBrowserText = findViewById(R.id.action_browser);
        mBrowserText.setClickable(true);
        mBrowserText.setBackgroundResource(R.drawable.browser);
        mBrowserText.getBackground().setAlpha(75);


        mBrowserText.setOnClickListener(view -> {
            try {
                try {
                    mActionMode = startSupportActionMode(
                            createSearchActionModeCallback());
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        mSelectionViewModel = new ViewModelProvider(this).get(SelectionViewModel.class);

        Uri uri = docs.getPinsPageUri();
        mSelectionViewModel.setUri(uri.toString());
        updateUri(uri);


        mFloatingActionButton.setOnClickListener((v) -> {

            if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            int value = currentFragment.intValue();
            if (value == R.id.navigation_files) {
                clickFilesAdd();
            }
        });


        if (savedInstanceState != null) {
            showFragment(savedInstanceState.getInt(FRAG));
        } else {
            showFragment(R.id.navigation_browser);
        }

        mActionHome.setOnClickListener(view -> toggleFragment());

        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);

        eventViewModel.getHome().observe(this, (event) -> {
            try {
                if (event != null) {
                    updateHome();
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        });

        eventViewModel.getRefresh().observe(this, (event) -> {
            try {
                if (event != null) {
                    String url = mBrowserFragment.getUrl();
                    if (url != null) {
                        Uri checkUri = Uri.parse(url);
                        if (Objects.equals(checkUri.getHost(), docs.getHost())) {
                            mSelectionViewModel.setUri(docs.getPinsPageUri().toString());
                        }
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        });

        eventViewModel.getDelete().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Gson gson = new Gson();
                        DeleteOperation deleteOperation = gson.fromJson(content, DeleteOperation.class);

                        long[] idxs = deleteOperation.indices;


                        String message;
                        if (idxs.length == 1) {
                            message = getString(R.string.delete_file);
                        } else {
                            message = getString(
                                    R.string.delete_files, "" + idxs.length);
                        }
                        AtomicBoolean deleteThreads = new AtomicBoolean(true);
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, message, Snackbar.LENGTH_LONG);
                        snackbar.setAction(getString(R.string.revert_operation), (view) -> {

                            try {
                                deleteThreads.set(false);
                                ExecutorService executor = Executors.newSingleThreadExecutor();
                                executor.submit(() -> THREADS.getInstance(
                                        getApplicationContext()).resetThreadsDeleting(idxs));
                            } catch (Throwable e) {
                                LogUtils.error(TAG, e);
                            } finally {
                                snackbar.dismiss();
                            }

                        });

                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (deleteThreads.get()) {
                                    DeleteThreadsWorker.cleanup(getApplicationContext());
                                }
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();
                    }

                    eventViewModel.removeEvent(event);

                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });
        eventViewModel.getError().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(android.R.string.ok, (view) -> snackbar.dismiss());

                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);

                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });

        eventViewModel.getPermission().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(R.string.app_settings, new PermissionAction());

                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();

                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });


        eventViewModel.getWarning().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_SHORT);

                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });
        eventViewModel.getInfo().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Toast.makeText(getApplicationContext(), content, Toast.LENGTH_SHORT).show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });

        registerNetworkCallback();

        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            registerService(ipfs.getPort());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        Intent intent = getIntent();
        handleIntents(intent);

    }

    private void toggleFragment() {
        int itemId = currentFragment.get();
        if (itemId == R.id.navigation_browser) {
            showFragment(R.id.navigation_files);
        } else {
            showFragment(R.id.navigation_browser);
        }
    }

    private void showFragment(int itemId) {
        currentFragment.set(itemId);
        releaseActionMode();
        if (itemId == R.id.navigation_files) {

            getSupportFragmentManager()
                    .beginTransaction()
                    .hide(mBrowserFragment)
                    .show(mThreadsFragment)
                    .commit();

            showFab(true);

            mSelectionViewModel.setParentThread(0L);
            mActionBookmark.setVisibility(View.GONE);
            mActionBookmarks.setVisibility(View.VISIBLE);
            mActionDaemon.setVisibility(View.VISIBLE);
            setFabImage(R.drawable.plus_thick);
        } else if (itemId == R.id.navigation_browser) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .show(mBrowserFragment)
                    .hide(mThreadsFragment)
                    .commit();
            showFab(false);

            mSelectionViewModel.setParentThread(0L);
            mActionBookmark.setVisibility(View.VISIBLE);
            mActionBookmarks.setVisibility(View.VISIBLE);
            mActionDaemon.setVisibility(View.GONE);
        }
        updateHome();
    }

    private String prettyUri(@NonNull Uri uri, @NonNull String replace) {
        return uri.toString().replaceFirst(replace, "");
    }

    public void updateTitle(@NonNull Uri uri) {

        try {
            if (Objects.equals(uri.getScheme(), Content.HTTPS)) {
                mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.lock, 0, 0, 0
                );
                mBrowserText.setText(prettyUri(uri, "https://"));
            } else if (Objects.equals(uri.getScheme(), Content.HTTP)) {
                mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.lock_open, 0, 0, 0
                );
                mBrowserText.setText(prettyUri(uri, "http://"));
            } else {
                BOOKS books = BOOKS.getInstance(getApplicationContext());
                Bookmark bookmark = books.getBookmark(uri.toString());

                String title = uri.toString();
                if (bookmark != null) {
                    String bookmarkTitle = bookmark.getTitle();
                    if (!bookmarkTitle.isEmpty()) {
                        title = bookmarkTitle;
                    }
                }

                mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.lock, 0, 0, 0
                );
                mBrowserText.setText(title);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void updateDownload(@NonNull Uri uri) {
        downloadActive.set(Objects.equals(uri.getScheme(), Content.IPFS) ||
                Objects.equals(uri.getScheme(), Content.IPNS));
    }


    private void releaseActionMode() {
        try {
            if (mActionMode != null) {
                mActionMode.finish();
                mActionMode = null;
            }
            mBrowserFragment.releaseActionMode();
            mThreadsFragment.releaseActionMode();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void enableSwipeRefresh(boolean enable) {
        mBrowserFragment.enableSwipeRefresh(enable);
        mThreadsFragment.enableSwipeRefresh(enable);
    }

    @Override
    public void updateUri(@NonNull Uri uri) {
        updateTitle(uri);
        updateBookmark(uri);
        updateDownload(uri);
    }


    private void invokeScan() {
        try {
            PackageManager pm = getPackageManager();

            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.setOrientationLocked(false);
                Intent intent = integrator.createScanIntent();
                mScanRequestForResult.launch(intent);
            } else {
                EVENTS.getInstance(getApplicationContext()).permission(
                        getString(R.string.feature_camera_required));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private boolean isDarkTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setLightStatusBar(@NonNull Activity activity) {
        int flags = activity.getWindow().getDecorView().getSystemUiVisibility(); // get current flag
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;   // add LIGHT_STATUS_BAR to flag
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        activity.getWindow().setStatusBarColor(Color.WHITE); // optional
    }


    public abstract static class AppBarStateChangedListener implements AppBarLayout.OnOffsetChangedListener {

        private State mCurrentState = State.IDLE;

        @Override
        public final void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            if (verticalOffset == 0) {
                setCurrentStateAndNotify(State.EXPANDED);
            } else if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
                setCurrentStateAndNotify(State.COLLAPSED);
            } else {
                setCurrentStateAndNotify(State.IDLE);
            }
        }

        private void setCurrentStateAndNotify(State state) {
            if (mCurrentState != state) {
                onStateChanged(state);
            }
            mCurrentState = state;
        }

        public abstract void onStateChanged(State state);

        public enum State {
            EXPANDED,
            COLLAPSED,
            IDLE
        }
    }

}