package io.pyroscope.javaagent;

import io.pyroscope.javaagent.impl.ContinuousProfilingScheduler;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ProfilingSchedulerTest {
    @Test
    public void testAlignProfilingTimeBackward() throws ParseException {
        long t = parse("2022-06-11 16:10:32.239");
        long expected = parse("2022-06-11 16:10:30.000");
        long res = ContinuousProfilingScheduler.alignProfilingIntervalStartTime(t, 10 * DateUtils.NANOS_PER_SECOND);
        assertEquals(expected, res);
    }
    @Test
    public void testAlignProfilingTimeForward() throws ParseException {
        long t = parse("2022-06-11 16:10:39.239");
        long expected = parse("2022-06-11 16:10:40.000");
        long res = ContinuousProfilingScheduler.alignProfilingIntervalStartTime(t, 10 * DateUtils.NANOS_PER_SECOND);
        assertEquals(expected, res);
    }


    @Test
    public void qwe() throws ParseException {
        long t = 1654938631*1000000000L;
        long expected = 1654938630 * 1000000000L;
        long res = ContinuousProfilingScheduler.alignProfilingIntervalStartTime(t, 10 * DateUtils.NANOS_PER_SECOND);
        assertEquals(expected, res);
        Date date = new Date(res / 1000000);
        System.out.println(date);
    }


    private long parse(String text) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date parse = f.parse(text);
        return parse.getTime() * 1_000_000L;
    }
}
