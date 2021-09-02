package threads.server.core.events;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

public class EVENTS {

    public static final String DELETE = "DELETE";
    public static final String ERROR = "FAILURE";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";
    public static final String PERMISSION = "PERMISSION";
    public static final String REFRESH = "REFRESH";
    public static final String HOME = "HOME";
    private static EVENTS INSTANCE = null;
    private final EventsDatabase eventsDatabase;

    private EVENTS(EVENTS.Builder builder) {
        this.eventsDatabase = builder.eventsDatabase;
    }

    @NonNull
    private static EVENTS createEvents(@NonNull EventsDatabase eventsDatabase) {

        return new EVENTS.Builder()
                .eventsDatabase(eventsDatabase)
                .build();
    }

    public static EVENTS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (EVENTS.class) {
                if (INSTANCE == null) {
                    EventsDatabase eventsDatabase =
                            Room.inMemoryDatabaseBuilder(context,
                                    EventsDatabase.class).
                                    allowMainThreadQueries().build();
                    INSTANCE = EVENTS.createEvents(eventsDatabase);
                }
            }
        }
        return INSTANCE;
    }

    public void error(@NonNull String content) {

        storeEvent(createEvent(ERROR, content));
    }

    public void delete(@NonNull String content) {
        storeEvent(createEvent(DELETE, content));
    }

    public void permission(@NonNull String content) {

        storeEvent(createEvent(PERMISSION, content));
    }

    public void info(@NonNull String content) {

        storeEvent(createEvent(INFO, content));
    }


    public void warning(@NonNull String content) {

        storeEvent(createEvent(WARNING, content));
    }


    @NonNull
    public EventsDatabase getEventsDatabase() {
        return eventsDatabase;
    }


    @NonNull
    Event createEvent(@NonNull String identifier, @NonNull String content) {

        return Event.createEvent(identifier, content);
    }

    void storeEvent(@NonNull Event event) {

        getEventsDatabase().eventDao().insertEvent(event);
    }


    public void refresh() {
        storeEvent(createEvent(REFRESH, ""));
    }

    public void home() {
        storeEvent(createEvent(HOME, ""));
    }

    static class Builder {
        EventsDatabase eventsDatabase = null;

        EVENTS build() {

            return new EVENTS(this);
        }

        Builder eventsDatabase(@NonNull EventsDatabase eventsDatabase) {

            this.eventsDatabase = eventsDatabase;
            return this;
        }
    }
}
