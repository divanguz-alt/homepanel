package hr.divanguz.homepanel.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.OffsetDateTime

class ClaudeUsageTest {
    @Test fun parsesBothWindows() {
        val s = ClaudeUsage.parse(
            """{"five_hour":{"utilization":17.0,"resets_at":"2026-10-20T14:00:00+00:00"},
            "seven_day":{"utilization":46.5,"resets_at":"2026-10-22T09:00:00.123456+00:00"},"seven_day_opus":null}""",
        )
        assertEquals(17f, s.fiveHour!!, 0.001f)
        assertEquals(46.5f, s.week!!, 0.001f)
        assertEquals(OffsetDateTime.parse("2026-10-20T14:00:00+00:00").toInstant().toEpochMilli(), s.fiveHourResetsAt)
        assertFalse(s.needsLogin)
    }

    @Test(expected = Exception::class) fun rejectsResponsesWithoutWindows() {
        ClaudeUsage.parse("""{"something_else":1}""")
    }
}
