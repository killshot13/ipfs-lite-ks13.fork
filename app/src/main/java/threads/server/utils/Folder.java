package threads.server.utils;

public class Folder {
    private final String name;
    private final long idx;

    public Folder(String name, long idx) {
        this.name = name;
        this.idx = idx;
    }

    public String getName() {
        return name;
    }

    public long getIdx() {
        return idx;
    }
}
