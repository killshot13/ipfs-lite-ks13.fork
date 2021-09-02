package threads.server.core.pages;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Page.class}, version = 250, exportSchema = false)
public abstract class PageDatabase extends RoomDatabase {

    public abstract PageDao pageDao();

}
