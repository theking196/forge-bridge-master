package com.forge.bridge.forge

import android.os.Parcel
import android.os.Parcelable

data class ChatResponse(
    val id: String,
    val content: String,
    val finishReason: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) : Parcelable {
    constructor(p: Parcel) : this(
        p.readString().orEmpty(),
        p.readString().orEmpty(),
        p.readString(),
        p.readInt(),
        p.readInt()
    )

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(id)
        p.writeString(content)
        p.writeString(finishReason)
        p.writeInt(promptTokens)
        p.writeInt(completionTokens)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatResponse> {
        override fun createFromParcel(p: Parcel) = ChatResponse(p)
        override fun newArray(size: Int): Array<ChatResponse?> = arrayOfNulls(size)
    }
}
