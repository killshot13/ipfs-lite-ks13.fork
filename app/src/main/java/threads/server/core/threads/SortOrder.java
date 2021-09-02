package threads.server.core.threads;

import androidx.annotation.NonNull;
import androidx.room.TypeConverter;


public enum SortOrder {
    DATE(0),
    NAME(1),
    SIZE(2),
    DATE_INVERSE(3),
    NAME_INVERSE(4),
    SIZE_INVERSE(5);
    @NonNull
    private final Integer code;

    SortOrder(@NonNull Integer code) {

        this.code = code;
    }

    @TypeConverter
    public static SortOrder toSort(Integer status) {

        if (status.equals(SortOrder.NAME.getCode())) {
            return SortOrder.NAME;
        } else if (status.equals(SortOrder.SIZE.getCode())) {
            return SortOrder.SIZE;
        } else if (status.equals(SortOrder.DATE_INVERSE.getCode())) {
            return SortOrder.DATE_INVERSE;
        } else if (status.equals(SortOrder.NAME_INVERSE.getCode())) {
            return SortOrder.NAME_INVERSE;
        } else if (status.equals(SortOrder.DATE.getCode())) {
            return SortOrder.DATE;
        } else if (status.equals(SortOrder.SIZE_INVERSE.getCode())) {
            return SortOrder.SIZE_INVERSE;
        } else {
            throw new IllegalArgumentException("Could not recognize status");
        }
    }

    @TypeConverter
    public static Integer toInteger(SortOrder status) {

        return status.getCode();
    }

    @NonNull
    private Integer getCode() {
        return code;
    }
}
