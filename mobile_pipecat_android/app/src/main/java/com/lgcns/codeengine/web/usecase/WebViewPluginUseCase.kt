package com.lgcns.codeengine.web.usecase

import com.lgcns.codeengine.data.repository.datastore.SecureDataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.WebDataStoreRepository
import com.lgcns.codeengine.data.repository.device.DeviceRepository
import com.lgcns.codeengine.data.repository.media.MediaRepository
import com.lgcns.codeengine.data.repository.location.LocationRepository
import javax.inject.Inject

class WebViewPluginUseCase @Inject constructor(
    val webDataStoreRepository: WebDataStoreRepository,
    val locationRepository: LocationRepository,
    val mediaRepository: MediaRepository,
    val secureDataStoreRepository: SecureDataStoreRepository,
    val deviceRepository: DeviceRepository,
) {
}
