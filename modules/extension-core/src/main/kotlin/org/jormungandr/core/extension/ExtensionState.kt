package org.jormungandr.core.extension

/**
 * Lifecycle states of an extension in Jörmungandr.
 */
enum class ExtensionState {
    /** Registered in metadata registry but not instantiated */
    UNLOADED,

    /** Dependencies verified and manifest validated */
    RESOLVED,

    /** Instantiated, context linked, parent Disposable registered */
    INITIALIZED,

    /** Active and processing events/queries/kernels */
    ACTIVE,

    /** Temporarily paused (caches drained, threads idle) */
    PAUSED,

    /** Shutting down (closing sockets, flushing data, draining queues) */
    DISPOSING,

    /** Fully unmounted, resources freed, ready for Garbage Collection */
    TERMINATED
}

/**
 * Pressure levels for memory trimming notifications.
 */
enum class MemoryPressureLevel {
    NORMAL,
    MODERATE,
    CRITICAL
}
