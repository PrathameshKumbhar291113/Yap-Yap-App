package com.yapyap.core.network

/** True when running on an emulator (Android) or simulator (iOS). */
internal expect fun isVirtualDevice(): Boolean
