package threads.server.core.books;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;


public class BOOKS {

    private static BOOKS INSTANCE = null;
    private final BookmarkDatabase bookmarkDatabase;


    private BOOKS(final BOOKS.Builder builder) {
        bookmarkDatabase = builder.bookmarkDatabase;
    }

    @NonNull
    private static BOOKS createBooks(@NonNull BookmarkDatabase bookmarkDatabase) {

        return new BOOKS.Builder()
                .bookmarkDatabase(bookmarkDatabase)
                .build();
    }

    public static BOOKS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (BOOKS.class) {
                if (INSTANCE == null) {
                    BookmarkDatabase pageDatabase = Room.databaseBuilder(context,
                            BookmarkDatabase.class,
                            BookmarkDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();

                    INSTANCE = BOOKS.createBooks(pageDatabase);
                }
            }
        }
        return INSTANCE;
    }


    @NonNull
    public Bookmark createBookmark(@NonNull String uri, @NonNull String title) {
        return new Bookmark(uri, title);
    }

    public void storeBookmark(@NonNull Bookmark bookmark) {
        bookmarkDatabase.bookmarkDao().insertBookmark(bookmark);
    }

    @NonNull
    public BookmarkDatabase getBookmarkDatabase() {
        return bookmarkDatabase;
    }

    @Nullable
    public Bookmark getBookmark(@NonNull String uri) {
        return bookmarkDatabase.bookmarkDao().getBookmark(uri);
    }

    public boolean hasBookmark(@NonNull String uri) {
        return getBookmark(uri) != null;
    }

    public void removeBookmark(@NonNull Bookmark bookmark) {
        bookmarkDatabase.bookmarkDao().removeBookmark(bookmark);
    }

    public List<Bookmark> getBookmarksByQuery(@NonNull String query) {

        String searchQuery = query.trim();
        if (!searchQuery.startsWith("%")) {
            searchQuery = "%" + searchQuery;
        }
        if (!searchQuery.endsWith("%")) {
            searchQuery = searchQuery + "%";
        }
        return bookmarkDatabase.bookmarkDao().getBookmarksByQuery(searchQuery);
    }


    public void storeDnsLink(@NonNull String uri, @NonNull String link) {
        bookmarkDatabase.bookmarkDao().setDnsLink(uri, link);
    }

    @Nullable
    public String getDnsLink(@NonNull String uri) {
        return bookmarkDatabase.bookmarkDao().getDnsLink(uri);
    }


    static class Builder {

        BookmarkDatabase bookmarkDatabase = null;

        BOOKS build() {

            return new BOOKS(this);
        }

        BOOKS.Builder bookmarkDatabase(@NonNull BookmarkDatabase bookmarkDatabase) {

            this.bookmarkDatabase = bookmarkDatabase;
            return this;
        }
    }
}
