package com.ivor.ivormusic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PageLoadState(
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val failed: Boolean = false
)

/** Main-thread cursor ownership. Completing a page never starts another request. */
internal class DemandPagination<C : Any> {
    class Request<C>(val continuation: C?)

    private val mutableState = MutableStateFlow(PageLoadState())
    val state = mutableState.asStateFlow()
    private var next: C? = null
    private var active: Request<C>? = null
    private val consumed = mutableSetOf<C>()

    fun reset() {
        active = null
        next = null
        consumed.clear()
        mutableState.value = PageLoadState()
    }

    fun first(): Request<C> {
        reset()
        return Request<C>(null).also {
            active = it
            mutableState.value = PageLoadState(isLoading = true)
        }
    }

    fun more(): Request<C>? {
        if (active != null) return null
        val cursor = next ?: return null
        return Request(cursor).also {
            active = it
            mutableState.value = PageLoadState(isLoading = true, hasMore = true)
        }
    }

    fun isCurrent(request: Request<C>): Boolean = active === request

    fun complete(request: Request<C>, continuation: C?): Boolean {
        if (!isCurrent(request)) return false
        request.continuation?.let { consumed.add(it) }
        next = continuation?.takeUnless { it in consumed }
        active = null
        mutableState.value = PageLoadState(hasMore = next != null)
        return true
    }

    fun fail(request: Request<C>) {
        if (!isCurrent(request)) return
        active = null
        mutableState.value = PageLoadState(hasMore = next != null, failed = true)
    }

    fun cancel(request: Request<C>) {
        if (!isCurrent(request)) return
        active = null
        mutableState.value = PageLoadState(hasMore = next != null)
    }
}
