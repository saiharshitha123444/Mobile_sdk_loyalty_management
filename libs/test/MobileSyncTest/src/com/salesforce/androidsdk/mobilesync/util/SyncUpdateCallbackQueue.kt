/*
 * Copyright (c) 2014-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.mobilesync.util

import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.runBlocking

/**
 * A SyncUpdateCallback which queues SyncStates, then serves them filtered by
 * the SyncState id.  This can be useful for tests which assert the sequence of
 * SyncStates, possibly for multiple and concurrent SyncStates.
 *
 * @param syncStateIds The list of SyncState ids to pre-allocate queues for
 */
class SyncUpdateCallbackQueue(
    vararg syncStateIds: Long
) : SyncUpdateCallback {

    /**
     * The map of queued SyncStates by the SyncState id which provided them.
     */
    private var syncStatesById: Map<Long, Channel<SyncState>>

    /**
     * Initializes a new instance with pre-allocated queues for each provided
     * SyncState id.
     */
    init {
        check(syncStateIds.isNotEmpty()) { "At least one SyncState id must be provided." }

        syncStatesById = syncStateIds.associateWith { Channel(UNLIMITED) }
    }

    /**
     * Queues a new SyncState by its id.
     *
     * @param syncState The new SyncState to queue
     */
    override fun onUpdate(syncState: SyncState) {
        val syncStateId = syncState.id
        val syncStates = syncStatesById[syncStateId]

        check(syncStates != null) {
            "Cannot queue SyncState with unexpected id '$syncStateId'.  Verify the expected ids are provided at initialization."
        }

        syncStates.trySendBlocking(syncState.copy())
    }

    /**
     * Returns the next queued SyncEvent event within a reasonable timeout.  The
     * first SyncState id provided at initialization is used.
     */
    val nextSyncUpdate: SyncState
        get() = getNextSyncUpdate(syncStatesById.keys.first())

    /**
     * Returns the next queued SyncEvent event within a reasonable timeout.
     *
     * @param syncStateId The SyncState id
     */
    fun getNextSyncUpdate(syncStateId: Long) = runBlocking {
        val syncStates = syncStatesById[syncStateId]

        check(syncStates != null) {
            "Cannot get SyncState with unexpected id '$syncStateId'.  Verify the expected ids are provided at initialization."
        }

        syncStates.receive()
    }
}
