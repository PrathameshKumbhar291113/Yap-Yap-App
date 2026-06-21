package com.yapyap.feature.chat.util

import com.yapyap.core.util.currentIsoTimestamp
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newClientMessageId(): String = Uuid.random().toString()

fun currentTimestampIso(): String = currentIsoTimestamp()
