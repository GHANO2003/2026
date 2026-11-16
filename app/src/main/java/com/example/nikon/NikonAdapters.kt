package com.example.nikon

import com.example.camera.CameraProfileRegistry
import kotlinx.coroutines.CoroutineScope

class NikonD5600Adapter(scope: CoroutineScope) :
    BaseNikonPtpAdapter(CameraProfileRegistry.nikonD5600, scope)

class NikonD5500Adapter(scope: CoroutineScope) :
    BaseNikonPtpAdapter(CameraProfileRegistry.nikonD5500, scope)
