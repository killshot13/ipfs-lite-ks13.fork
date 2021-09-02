package threads.server.utils;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SelectionViewModel extends ViewModel {

    @NonNull
    private final MutableLiveData<Long> parentThread = new MutableLiveData<>(0L);
    @NonNull
    private final MutableLiveData<String> query = new MutableLiveData<>("");

    @NonNull
    private final MutableLiveData<String> uri = new MutableLiveData<>(null);

    @NonNull
    public MutableLiveData<Long> getParentThread() {
        return parentThread;
    }

    public void setParentThread(long idx) {
        getParentThread().postValue(idx);
    }

    @NonNull
    public MutableLiveData<String> getQuery() {
        return query;
    }

    public void setQuery(String query) {
        getQuery().postValue(query);
    }

    @NonNull
    public MutableLiveData<String> getUri() {
        return uri;
    }

    public void setUri(String uri) {
        getUri().postValue(uri);
    }

}
