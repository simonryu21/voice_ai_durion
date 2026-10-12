package com.lgcns.codeengine.data.repository.appinventory.model

import com.google.gson.annotations.SerializedName

data class AppItemRequest(
    @SerializedName("package_name")
    val packageName: String,
    val label: String?,
    @SerializedName("usage_score")
    val usageScore: Double?,
    @SerializedName("is_system")
    val isSystem: Boolean?
)

data class AppInventoryRequest(
    @SerializedName("client_ts")
    val clientTs: String,
    @SerializedName("device_id")
    val deviceId: String,
    val platform: String = "android",
    @SerializedName("client_usage_stats_collected")
    val clientUsageStatsCollected: Boolean,
    @SerializedName("client_sorted")
    val clientSorted: Boolean,
    val apps: List<AppItemRequest>
)
