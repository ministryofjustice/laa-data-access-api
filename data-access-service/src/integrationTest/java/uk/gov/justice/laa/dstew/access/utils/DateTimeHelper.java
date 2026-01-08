package uk.gov.justice.laa.dstew.access.utils;

import java.time.Instant;
import java.time.InstantSource;

public class DateTimeHelper {
    private DateTimeHelper(){
    }

    public static Instant GetSystemInstanceWithoutNanoseconds() {
        var instant = InstantSource.system().instant();
        return instant.minusNanos(instant.getNano());
    }
}
