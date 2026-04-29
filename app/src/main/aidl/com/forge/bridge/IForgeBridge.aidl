// IForgeBridge.aidl
package com.forge.bridge;

import com.forge.bridge.ChatRequest;
import com.forge.bridge.ChatResponse;
import com.forge.bridge.ChatChunk;
import com.forge.bridge.IChatStreamCallback;
import com.forge.bridge.ProviderInfo;

interface IForgeBridge {
    /** Returns the list of currently connected providers. */
    List<ProviderInfo> listProviders();

    /** Synchronous (non-streaming) chat completion. */
    ChatResponse chat(in ChatRequest request);

    /** Streaming chat completion. Chunks are delivered to the callback. */
    void streamChat(in ChatRequest request, IChatStreamCallback callback);

    /** Returns "ok" if the bridge server is healthy. */
    String healthCheck();
}
