// IChatStreamCallback.aidl
package com.forge.bridge;

import com.forge.bridge.ChatChunk;

oneway interface IChatStreamCallback {
    void onChunk(in ChatChunk chunk);
    void onComplete();
    void onError(String message);
}
