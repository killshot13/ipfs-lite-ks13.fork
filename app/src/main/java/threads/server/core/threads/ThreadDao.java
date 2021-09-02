package threads.server.core.threads;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ThreadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertThread(Thread thread);

    @Query("SELECT content FROM Thread WHERE idx = :idx")
    String getContent(long idx);

    @Query("UPDATE Thread SET leaching = 1  WHERE idx = :idx")
    void setLeaching(long idx);

    @Query("UPDATE Thread SET leaching = 0  WHERE idx = :idx")
    void resetLeaching(long idx);

    @Query("SELECT leaching FROM Thread WHERE idx =:idx")
    boolean isLeaching(long idx);

    @Query("UPDATE Thread SET deleting = 1 WHERE idx = :idx")
    void setDeleting(long idx);

    @Query("UPDATE Thread SET deleting = 0 WHERE idx = :idx")
    void resetDeleting(long idx);

    @Query("SELECT * FROM Thread WHERE content = :cid AND parent = :parent")
    List<Thread> getThreadsByContentAndParent(String cid, long parent);

    @Query("SELECT * FROM Thread WHERE name = :name AND parent = :parent")
    List<Thread> getThreadsByNameAndParent(String name, long parent);

    @Query("SELECT COUNT(idx) FROM Thread WHERE content =:cid")
    int references(String cid);

    @Query("SELECT * FROM Thread WHERE parent =:thread")
    List<Thread> getChildren(long thread);

    @Query("SELECT SUM(size) FROM Thread WHERE parent =:parent AND deleting = 0")
    long getChildrenSummarySize(long parent);

    @Query("SELECT * FROM Thread WHERE idx =:idx")
    Thread getThreadByIdx(long idx);

    @Query("SELECT * FROM Thread WHERE parent =:parent AND deleting = 0  AND name LIKE :query")
    LiveData<List<Thread>> getLiveDataVisibleChildrenByQuery(long parent, String query);

    @Query("UPDATE Thread SET content =:cid  WHERE idx = :idx")
    void setContent(long idx, String cid);

    @Query("UPDATE Thread SET mimeType =:mimeType WHERE idx = :idx")
    void setMimeType(long idx, String mimeType);

    @Query("UPDATE Thread SET name =:name WHERE idx = :idx")
    void setName(long idx, String name);


    @Query("SELECT * FROM Thread WHERE parent = 0 AND deleting = 0 AND seeding = 1")
    List<Thread> getPins();

    @Query("SELECT * FROM Thread WHERE parent =:parent AND deleting = 0")
    List<Thread> getVisibleChildren(long parent);

    @Query("UPDATE Thread SET progress = :progress WHERE idx = :idx")
    void setProgress(long idx, int progress);

    @Query("UPDATE Thread SET seeding = 1, init = 0, progress = 0, leaching = 0 WHERE idx = :idx")
    void setDone(long idx);

    @Query("UPDATE Thread SET content =:cid, seeding = 1, init = 0, progress = 0, leaching = 0 WHERE idx = :idx")
    void setDone(long idx, String cid);

    @Query("UPDATE Thread SET size = :size WHERE idx = :idx")
    void setSize(long idx, long size);


    @Query("UPDATE Thread SET work = :work WHERE idx = :idx")
    void setWork(long idx, String work);

    @Query("UPDATE Thread SET work = null WHERE idx = :idx")
    void resetWork(long idx);

    @Query("SELECT init FROM Thread WHERE idx =:idx")
    boolean isInit(long idx);

    @Query("UPDATE Thread SET init = 0 WHERE idx = :idx")
    void resetInit(long idx);

    @Query("SELECT (idx) FROM Thread WHERE deleting = 1 AND lastModified < :time")
    List<Long> getDeletedThreads(long time);

    @Query("UPDATE Thread SET lastModified =:lastModified WHERE idx = :idx")
    void setLastModified(long idx, long lastModified);

    @Delete
    void removeThread(Thread thread);

    @Query("SELECT parent FROM Thread WHERE idx = :idx")
    long getParent(long idx);

    @Query("SELECT name FROM Thread WHERE idx = :idx")
    String getName(long idx);

    @Query("UPDATE Thread SET uri =:uri WHERE idx = :idx")
    void setUri(long idx, String uri);

    @Query("SELECT work FROM Thread WHERE idx = :idx")
    String getWork(long idx);
}
