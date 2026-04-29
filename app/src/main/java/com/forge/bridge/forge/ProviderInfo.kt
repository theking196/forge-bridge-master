package com.forge.bridge.forge

import android.os.Parcel
import android.os.Parcelable

data class ProviderInfo(
    val id: String,
    val name: String,
    val tier: String,
    val status: String,
    val models: List<String>,
    val features: List<String>
) : Parcelable {
    constructor(p: Parcel) : this(
        id = p.readString().orEmpty(),
        name = p.readString().orEmpty(),
        tier = p.readString().orEmpty(),
        status = p.readString().orEmpty(),
        models = mutableListOf<String>().also { p.readStringList(it) },
        features = mutableListOf<String>().also { p.readStringList(it) }
    )

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(id); p.writeString(name); p.writeString(tier); p.writeString(status)
        p.writeStringList(models); p.writeStringList(features)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProviderInfo> {
        override fun createFromParcel(p: Parcel) = ProviderInfo(p)
        override fun newArray(size: Int): Array<ProviderInfo?> = arrayOfNulls(size)
    }
}
