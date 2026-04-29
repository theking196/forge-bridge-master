package com.forge.bridge.forge

import android.os.Parcel
import android.os.Parcelable

data class ChatMessage(
    val role: String,
    val content: String
) : Parcelable {
    constructor(p: Parcel) : this(
        p.readString().orEmpty(),
        p.readString().orEmpty()
    )

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(role); p.writeString(content)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatMessage> {
        override fun createFromParcel(p: Parcel) = ChatMessage(p)
        override fun newArray(size: Int): Array<ChatMessage?> = arrayOfNulls(size)
    }
}

data class ChatRequest(
    val providerId: String,
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 1.0f,
    val maxTokens: Int = 0,
    val stream: Boolean = false
) : Parcelable {
    constructor(p: Parcel) : this(
        providerId = p.readString().orEmpty(),
        model = p.readString().orEmpty(),
        messages = mutableListOf<ChatMessage>().also { p.readTypedList(it, ChatMessage.CREATOR) },
        temperature = p.readFloat(),
        maxTokens = p.readInt(),
        stream = p.readByte() != 0.toByte()
    )

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeString(providerId)
        p.writeString(model)
        p.writeTypedList(messages)
        p.writeFloat(temperature)
        p.writeInt(maxTokens)
        p.writeByte(if (stream) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatRequest> {
        override fun createFromParcel(p: Parcel) = ChatRequest(p)
        override fun newArray(size: Int): Array<ChatRequest?> = arrayOfNulls(size)
    }
}
