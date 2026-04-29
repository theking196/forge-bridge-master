package com.forge.bridge.forge

import android.os.Parcel
import android.os.Parcelable

data class ChatChunk(
    val delta: String,
    val finishReason: String? = null
) : Parcelable {
    constructor(p: Parcel) : this(p.readString().orEmpty(), p.readString())

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(delta); p.writeString(finishReason)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatChunk> {
        override fun createFromParcel(p: Parcel) = ChatChunk(p)
        override fun newArray(size: Int): Array<ChatChunk?> = arrayOfNulls(size)
    }
}
