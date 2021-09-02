package threads.server.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.LogUtils;
import threads.lite.core.Closeable;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.books.BOOKS;
import threads.server.core.books.Bookmark;
import threads.server.core.events.EVENTS;
import threads.server.provider.FileProvider;
import threads.server.services.LiteService;
import threads.server.services.MimeTypeService;
import threads.server.utils.CustomWebChromeClient;
import threads.server.utils.SelectionViewModel;
import threads.server.work.ClearBrowserDataWorker;
import threads.server.work.DownloadContentWorker;
import threads.server.work.DownloadFileWorker;


public class BrowserFragment extends Fragment {


    private static final String TAG = BrowserFragment.class.getSimpleName();

    private static final long CLICK_OFFSET = 500;
    private Context mContext;
    private final ActivityResultLauncher<Intent> mFileForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
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
                        LiteService.FileInfo fileInfo = LiteService.getFileInfo(mContext);
                        Objects.requireNonNull(fileInfo);
                        DownloadFileWorker.download(mContext, uri, fileInfo.getUri(),
                                fileInfo.getFilename(), fileInfo.getMimeType(), fileInfo.getSize());


                    } catch (Throwable e) {
                        LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
                    }
                }
            });
    private final ActivityResultLauncher<Intent> mContentForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
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
                        Uri contentUri = LiteService.getContentUri(mContext);
                        Objects.requireNonNull(contentUri);
                        DownloadContentWorker.download(mContext, uri, contentUri);

                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });
    private WebView mWebView;
    private FragmentActivity mActivity;
    private BrowserFragment.ActionListener mListener;
    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private long mLastClickTime = 0;
    private DOCS docs;

    private ActionMode mActionMode;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    private void goBack() {
        mWebView.stopLoading();
        docs.releaseThreads();
        mWebView.goBack();
    }

    public void goForward() {
        try {
            if (isResumed()) {
                mWebView.stopLoading();
                docs.releaseThreads();
                mWebView.goForward();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean onBackPressed() {
        if (mWebView.canGoBack()) {
            goBack();
            return true;
        }
        return false;
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        docs = DOCS.getInstance(context);
        mActivity = getActivity();
        mListener = (BrowserFragment.ActionListener) getActivity();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mActivity = null;
        releaseActionMode();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            try {
                mSwipeRefreshLayout.setRefreshing(true);
                reload();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        mProgressBar = view.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);


        mWebView = view.findViewById(R.id.web_view);


        CustomWebChromeClient mCustomWebChromeClient = new CustomWebChromeClient(mActivity);
        mWebView.setWebChromeClient(mCustomWebChromeClient);

        Settings.setWebSettings(mWebView, Settings.isJavascriptEnabled(mContext));


        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_AUTO);
        }

        SelectionViewModel mSelectionViewModel = new ViewModelProvider(mActivity).get(SelectionViewModel.class);


        mSelectionViewModel.getUri().observe(getViewLifecycleOwner(), (url) -> {
            if (url != null) {

                Uri uri = Uri.parse(url);
                openUri(uri);

            }
        });

        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {

            try {
                LogUtils.error(TAG, "downloadUrl : " + url);
                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    String res = uri.getQueryParameter("download");
                    if (Objects.equals(res, "0")) {
                        try {
                            EVENTS.getInstance(mContext)
                                    .warning(getString(R.string.browser_handle_file, filename));
                        } finally {
                            mProgressBar.setVisibility(View.GONE);
                        }
                    } else {
                        contentDownloader(uri);
                    }
                } else {
                    fileDownloader(uri, filename, mimeType, contentLength);
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {


            private final AtomicReference<String> host = new AtomicReference<>();

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                LogUtils.error(TAG, "onPageCommitVisible " + url);
                mProgressBar.setVisibility(View.GONE);
            }


            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

                try {

                    WebViewDatabase database = WebViewDatabase.getInstance(mContext);
                    String[] data = database.getHttpAuthUsernamePassword(host, realm);


                    String storedName = null;
                    String storedPass = null;

                    if (data != null) {
                        storedName = data[0];
                        storedPass = data[1];
                    }

                    LayoutInflater inflater = (LayoutInflater)
                            mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    final View form = inflater.inflate(R.layout.http_auth_request, null);


                    final EditText usernameInput = form.findViewById(R.id.user_name);
                    final EditText passwordInput = form.findViewById(R.id.password);

                    if (storedName != null) {
                        usernameInput.setText(storedName);
                    }

                    if (storedPass != null) {
                        passwordInput.setText(storedPass);
                    }

                    AlertDialog.Builder authDialog = new AlertDialog
                            .Builder(mActivity)
                            .setTitle(R.string.authentication)
                            .setView(form)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {

                                String username = usernameInput.getText().toString();
                                String password = passwordInput.getText().toString();

                                database.setHttpAuthUsernamePassword(host, realm, username, password);

                                handler.proceed(username, password);
                                dialog.dismiss();
                            })

                            .setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> {
                                dialog.dismiss();
                                view.stopLoading();
                                handler.cancel();
                            });


                    authDialog.show();
                    return;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }


            @Override
            public void onLoadResource(WebView view, String url) {
                LogUtils.error(TAG, "onLoadResource : " + url);
                super.onLoadResource(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                LogUtils.error(TAG, "doUpdateVisitedHistory : " + url + " " + isReload);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                LogUtils.error(TAG, "onPageStarted : " + url);

                mProgressBar.setVisibility(View.VISIBLE);
                releaseActionMode();
                mListener.updateUri(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                LogUtils.error(TAG, "onPageFinished : " + url);

                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                        Objects.equals(uri.getScheme(), Content.IPFS)) {

                    if (docs.numUris() == 0) {
                        mProgressBar.setVisibility(View.GONE);
                    }

                } else {
                    mProgressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                LogUtils.error(TAG, "" + error.getDescription());
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.error(TAG, "shouldOverrideUrlLoading : " + uri);

                    if (!Objects.equals(host.get(), uri.getHost())) {
                        LogUtils.error(TAG, uri.getHost() + " " + host.get());
                        docs.releaseThreads();
                        docs.releaseContent();
                    }

                    if (Objects.equals(uri.getScheme(), Content.ABOUT)) {
                        return true;
                    } else if (Objects.equals(uri.getScheme(), Content.HTTP) ||
                            Objects.equals(uri.getScheme(), Content.HTTPS)) {
                        Uri newUri = docs.redirectHttp(uri);
                        if (!Objects.equals(newUri, uri)) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, newUri,
                                    mContext, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            startActivity(intent);
                            return true;
                        }

                        return false;
                    } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                            Objects.equals(uri.getScheme(), Content.IPFS)) {

                        String res = uri.getQueryParameter("download");
                        if (Objects.equals(res, "1")) {
                            contentDownloader(uri);
                            return true;
                        }
                        mProgressBar.setVisibility(View.VISIBLE);

                        return false;

                    } else if (Objects.equals(uri.getScheme(), Content.MAGNET)) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            startActivity(intent);

                        } catch (Throwable ignore) {
                            EVENTS.getInstance(mContext).warning(
                                    getString(R.string.no_activity_found_to_handle_uri));
                        }
                        return true;
                    } else {
                        try {
                            // all other stuff
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            startActivity(intent);

                        } catch (Throwable ignore) {
                            EVENTS.getInstance(mContext).warning(
                                    getString(R.string.no_activity_found_to_handle_uri));
                        }
                        return true;
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
                return false;

            }


            public WebResourceResponse createRedirectMessage(@NonNull Uri uri) {
                return new WebResourceResponse(MimeTypeService.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(("<!DOCTYPE HTML>\n" +
                                "<html lang=\"en-US\">\n" +
                                "    <head>\n" +
                                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                                "        <meta charset=\"UTF-8\">\n" +
                                "        <meta http-equiv=\"refresh\" content=\"0; url=" + uri.toString() + "\">\n" +
                                "        <title>Page Redirection</title>\n" +
                                "    </head>\n" +
                                "    <body>\n" +
                                "        Automatically redirected to the <a href='" + uri.toString() + "'>index.html</a> file\n" +
                                "    </body>\n" +
                                "</html>").getBytes()));
            }

            public WebResourceResponse createEmptyResource() {
                return new WebResourceResponse("text/plain", Content.UTF8,
                        new ByteArrayInputStream("".getBytes()));
            }

            public WebResourceResponse createErrorMessage(@NonNull Throwable throwable) {
                String message = docs.generateErrorHtml(throwable);
                return new WebResourceResponse("text/html", Content.UTF8,
                        new ByteArrayInputStream(message.getBytes()));
            }


            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                Uri uri = request.getUrl();
                LogUtils.error(TAG, "shouldInterceptRequest : " + uri.toString());
                host.set(uri.getHost());

                if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                        Objects.equals(uri.getScheme(), Content.IPFS)) {
                    long start = System.currentTimeMillis();

                    docs.attachUri(uri);


                    Thread thread = Thread.currentThread();

                    docs.attachThread(thread.getId());

                    Closeable closeable = () -> !docs.shouldRun(thread.getId());


                    try {

                        Uri redirectUri = docs.redirectUri(uri, closeable);
                        if (!Objects.equals(uri, redirectUri)) {
                            return createRedirectMessage(redirectUri);
                        }

                        return docs.getResponse(mContext, redirectUri, closeable);
                    } catch (Throwable throwable) {
                        if (closeable.isClosed()) {
                            return createEmptyResource();
                        }
                        if (throwable instanceof DOCS.ContentException) {
                            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                                LogUtils.error(TAG,
                                        "Content not found ... " + uri.toString());
                            }
                        }
                        return createErrorMessage(throwable);
                    } finally {
                        docs.detachUri(uri);
                        LogUtils.info(TAG, "Finish page [" +
                                (System.currentTimeMillis() - start) + "]...");
                    }
                } else if (Objects.equals(uri.getScheme(), Content.HTTPS)) {
                    Uri redirectUri = docs.redirectHttps(uri);
                    if (!Objects.equals(redirectUri, uri)) {
                        return createRedirectMessage(redirectUri);
                    }
                }
                return null;
            }
        });

    }

    public void reload() {
        try {
            if (isResumed()) {
                try {
                    mProgressBar.setVisibility(View.GONE);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                try {
                    docs.cleanupResolver(Uri.parse(mWebView.getUrl()));
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                try {
                    mWebView.reload();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void fileDownloader(@NonNull Uri uri, @NonNull String filename,
                                @NonNull String mimeType, long size) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.download_title);
        builder.setMessage(filename);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {


            LiteService.setFileInfo(mContext, uri, filename, mimeType, size);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse(Settings.DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mFileForResult.launch(intent);
            mProgressBar.setVisibility(View.GONE);

        });
        builder.setNeutralButton(getString(android.R.string.cancel), (dialog, which) -> {
            mProgressBar.setVisibility(View.GONE);
            dialog.cancel();
        });
        builder.show();

    }


    private void contentDownloader(@NonNull Uri uri) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.download_title);
        String filename = docs.getFileName(uri);
        builder.setMessage(filename);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            LiteService.setContentUri(mContext, uri);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse(Settings.DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mContentForResult.launch(intent);

            mProgressBar.setVisibility(View.GONE);
        });
        builder.setNeutralButton(getString(android.R.string.cancel), (dialog, which) -> {
            mProgressBar.setVisibility(View.GONE);
            dialog.cancel();
        });
        builder.show();

    }


    private ActionMode.Callback createFindActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_find_action_mode, menu);


                MenuItem action_mode_find = menu.findItem(R.id.action_mode_find);
                EditText mFindText = (EditText) action_mode_find.getActionView();


                mFindText.setMaxWidth(Integer.MAX_VALUE);
                mFindText.setBackgroundResource(android.R.color.transparent);
                mFindText.setSingleLine();
                mFindText.setTextSize(14);
                mFindText.setHint(R.string.find_page);
                mFindText.setFocusable(true);
                mFindText.requestFocus();

                mFindText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        mWebView.findAllAsync(mFindText.getText().toString());
                    }
                });


                mode.setTitle("0/0");

                mWebView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                    try {
                        String result = "" + activeMatchOrdinal + "/" + numberOfMatches;
                        mode.setTitle(result);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
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
                int itemId = item.getItemId();
                if (itemId == R.id.action_mode_previous) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    try {
                        mWebView.findNext(false);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    return true;
                } else if (itemId == R.id.action_mode_next) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();
                    try {
                        mWebView.findNext(true);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    return true;

                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    mWebView.clearMatches();
                    mWebView.setFindListener(null);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        };

    }

    public void bookmark(@NonNull Context context, @NonNull ImageButton mActionBookmark) {
        try {
            if (isResumed()) {
                String url = mWebView.getUrl();
                Uri uri = Uri.parse(url);

                BOOKS books = BOOKS.getInstance(context);

                Bookmark bookmark = books.getBookmark(uri.toString());
                if (bookmark != null) {

                    String msg = bookmark.getTitle();

                    books.removeBookmark(bookmark);

                    Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.star_outline);
                    mActionBookmark.setImageDrawable(drawable);

                    if (msg.isEmpty()) {
                        msg = uri.toString();
                    }

                    EVENTS.getInstance(mContext).warning(
                            getString(R.string.bookmark_removed, msg));
                } else {
                    Bitmap bitmap = mWebView.getFavicon();
                    String title = mWebView.getTitle();
                    if (title == null) {
                        title = "";
                    }


                    bookmark = books.createBookmark(uri.toString(), title);
                    if (bitmap != null) {
                        bookmark.setBitmapIcon(bitmap);
                    } else {
                        bookmark.resetBitmapIcon();
                    }

                    books.storeBookmark(bookmark);

                    Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.star);
                    mActionBookmark.setImageDrawable(drawable);


                    String msg = title;
                    if (msg.isEmpty()) {
                        msg = uri.toString();
                    }

                    EVENTS.getInstance(mContext).warning(
                            getString(R.string.bookmark_added, msg));

                    mListener.updateUri(uri);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void clearBrowserData() {
        try {
            if (isResumed()) {
                mWebView.clearHistory();
                mWebView.clearCache(true);

                ClearBrowserDataWorker.clearCache(mContext);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.browser_view, container, false);
    }


    public void findInPage() {
        try {
            if (isResumed()) {
                mActionMode = ((AppCompatActivity)
                        mActivity).startSupportActionMode(
                        createFindActionModeCallback());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void openUri(@NonNull Uri uri) {
        long start = System.currentTimeMillis();

        try {

            mListener.updateUri(uri);

            mProgressBar.setVisibility(View.VISIBLE);

            docs.releaseThreads();
            docs.releaseContent();

            if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPFS)) {
                docs.attachUri(uri);

                mWebView.getSettings().setJavaScriptEnabled(false);
            } else {
                mWebView.getSettings().setJavaScriptEnabled(
                        Settings.isJavascriptEnabled(mContext)
                );
            }

            mWebView.stopLoading();

            mWebView.loadUrl(uri.toString());
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            LogUtils.info(TAG, "finish openUri [" +
                    (System.currentTimeMillis() - start) + "]...");
        }
    }

    public boolean canGoForward() {
        try {
            if (isResumed()) {
                return mWebView.canGoForward();
            }
            return false;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
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

    @Nullable
    public String getUrl() {
        try {
            if (isResumed()) {
                return mWebView.getUrl();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
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

        void updateUri(@NonNull Uri uri);
    }
}
