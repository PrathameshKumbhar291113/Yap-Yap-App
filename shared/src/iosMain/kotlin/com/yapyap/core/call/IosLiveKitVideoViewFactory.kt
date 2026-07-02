package com.yapyap.core.call

import platform.UIKit.UIViewController

fun iosLiveKitVideoViewController(trackId: String?, mirror: Boolean, fit: String = "Fill"): UIViewController {
    return iosLiveKitCreateVideoViewController(trackId, mirror, fit)
}
