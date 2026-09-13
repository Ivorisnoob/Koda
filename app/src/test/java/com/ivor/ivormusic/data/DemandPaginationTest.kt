package com.ivor.ivormusic.data

import org.junit.Assert.*
import org.junit.Test

class DemandPaginationTest {
    @Test fun firstPageStopsAndWaitsForDemandEvenWhenMoreExists() {
        val pages = DemandPagination<String>()
        val first = pages.first()
        assertNull(first.continuation)
        assertNull(pages.more())
        assertTrue(pages.complete(first, "page-2"))
        assertFalse(pages.state.value.isLoading)
        assertTrue(pages.state.value.hasMore)
        assertEquals("page-2", pages.more()?.continuation)
    }

    @Test fun duplicateBottomEventsCannotStartConcurrentRequests() {
        val pages = DemandPagination<String>()
        pages.complete(pages.first(), "page-2")
        assertNotNull(pages.more())
        assertNull(pages.more())
        assertNull(pages.more())
    }

    @Test fun failedContinuationCanRetryExactlyTheSamePage() {
        val pages = DemandPagination<String>()
        pages.complete(pages.first(), "page-2")
        pages.fail(checkNotNull(pages.more()))
        assertTrue(pages.state.value.failed)
        assertTrue(pages.state.value.hasMore)
        assertEquals("page-2", pages.more()?.continuation)
        assertFalse(pages.state.value.failed)
    }

    @Test fun refreshDiscardsLateResponsesFromTheOldList() {
        val pages = DemandPagination<String>()
        pages.complete(pages.first(), "old-next")
        val stale = checkNotNull(pages.more())
        val refresh = pages.first()
        assertFalse(pages.complete(stale, "wrong-account-next"))
        pages.fail(stale)
        pages.cancel(stale)
        assertTrue(pages.isCurrent(refresh))
        assertTrue(pages.complete(refresh, "new-next"))
        assertEquals("new-next", pages.more()?.continuation)
    }

    @Test fun profileResetRejectsOldResultsAndClearsTheCursor() {
        val pages = DemandPagination<String>()
        val old = pages.first()
        pages.reset()
        assertFalse(pages.complete(old, "old-account"))
        assertNull(pages.more())
        assertEquals(PageLoadState(), pages.state.value)
    }

    @Test fun leavingTheScreenPreservesTheUnconsumedCursor() {
        val pages = DemandPagination<String>()
        pages.complete(pages.first(), "page-2")
        val cancelled = checkNotNull(pages.more())
        pages.cancel(cancelled)
        assertFalse(pages.state.value.failed)
        assertFalse(pages.complete(cancelled, "page-3"))
        assertEquals("page-2", pages.more()?.continuation)
    }

    @Test fun exhaustedAndRepeatedCursorsCannotLoop() {
        val pages = DemandPagination<String>()
        pages.complete(pages.first(), "page-2")
        pages.complete(checkNotNull(pages.more()), "page-3")
        pages.complete(checkNotNull(pages.more()), "page-2")
        assertFalse(pages.state.value.hasMore)
        assertNull(pages.more())
        pages.complete(pages.first(), null)
        assertNull(pages.more())
    }

    @Test fun aFailedFirstPageIsNotReportedAsSuccessfulExhaustion() {
        val pages = DemandPagination<String>()
        pages.fail(pages.first())
        assertEquals(PageLoadState(failed = true), pages.state.value)
        assertTrue(pages.complete(pages.first(), "next"))
        assertFalse(pages.state.value.failed)
    }
}
