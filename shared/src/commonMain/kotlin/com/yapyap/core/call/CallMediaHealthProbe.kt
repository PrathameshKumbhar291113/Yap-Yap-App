package com.yapyap.core.call

/**
 * Tracks renderer attach and first-frame signals for the Media Health Report.
 * Reset when a call session's room is torn down.
 */
object CallMediaHealthProbe {
    private var localRendererAttached: Boolean = false
    private var remoteRendererAttached: Boolean = false
    private var videoFrameRendered: Boolean = false

    fun reset() {
        localRendererAttached = false
        remoteRendererAttached = false
        videoFrameRendered = false
    }

    fun onRendererAttached(isLocal: Boolean) {
        if (isLocal) {
            localRendererAttached = true
        } else {
            remoteRendererAttached = true
        }
    }

    fun onFirstVideoFrame() {
        videoFrameRendered = true
    }

    fun rendererStatus(): String {
        return buildString {
            append("local=")
            append(if (localRendererAttached) "attached" else "missing")
            append(",remote=")
            append(if (remoteRendererAttached) "attached" else "missing")
        }
    }

    fun hasRenderedVideoFrame(): Boolean = videoFrameRendered

    fun localRendererAttached(): Boolean = localRendererAttached

    fun remoteRendererAttached(): Boolean = remoteRendererAttached
}
