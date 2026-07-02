package com.yapyap.core.call

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.yapyap.core.push.PushNotificationChannels

const val YAP_TELECOM_EXTRA_CALL_SESSION_ID = "call_session_id"

object YapTelecomRegistry {
    private const val PHONE_ACCOUNT_ID = "yapyap_calls"
    const val EXTRA_CALL_SESSION_ID = YAP_TELECOM_EXTRA_CALL_SESSION_ID
    const val EXTRA_CALLER_NAME = "caller_name"
    const val EXTRA_IS_VIDEO = "is_video"

    private val activeConnections = mutableMapOf<String, YapTelecomConnection>()

    fun phoneAccountHandle(context: Context): PhoneAccountHandle {
        val component = ComponentName(context, YapConnectionService::class.java)
        return PhoneAccountHandle(component, PHONE_ACCOUNT_ID)
    }

    fun registerPhoneAccount(context: Context) {
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return
        val handle = phoneAccountHandle(context)
        val account = PhoneAccount.builder(handle, "YapYap")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager.registerPhoneAccount(account)
        CallDiagnostics.stage(stage = "CALL_TELECOM_ACCOUNT_REGISTERED")
    }

    private var lastIncomingSessionId: String? = null

    fun addIncomingCall(context: Context, request: CallNativeIncomingRequest) {
        if (lastIncomingSessionId == request.callSessionId) return
        lastIncomingSessionId = request.callSessionId
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return
        val handle = phoneAccountHandle(context)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, buildAddress(request.callSessionId))
            putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
                Bundle().apply {
                    putString(EXTRA_CALL_SESSION_ID, request.callSessionId)
                    putString(EXTRA_CALLER_NAME, request.callerDisplayName)
                    putBoolean(EXTRA_IS_VIDEO, request.isVideoCall)
                },
            )
        }
        CallDiagnostics.stage(
            stage = "CALL_TELECOM_INCOMING",
            callSessionId = request.callSessionId,
            extra = mapOf(
                "caller" to request.callerDisplayName,
                "video" to request.isVideoCall.toString(),
            ),
        )
        telecomManager.addNewIncomingCall(handle, extras)
    }

    fun dismissCall(callSessionId: String) {
        if (lastIncomingSessionId == callSessionId) {
            lastIncomingSessionId = null
        }
        activeConnections.remove(callSessionId)?.let { connection ->
            connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            connection.destroy()
        }
    }

    fun trackConnection(callSessionId: String, connection: YapTelecomConnection) {
        activeConnections[callSessionId] = connection
    }

    fun connectionFor(callSessionId: String): YapTelecomConnection? = activeConnections[callSessionId]

    fun extractSessionId(extras: Bundle?): String? {
        extras ?: return null
        return extras.getString(EXTRA_CALL_SESSION_ID)
            ?: extras.getParcelable<Uri>(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)?.schemeSpecificPart
    }

    private fun buildAddress(callSessionId: String): Uri =
        Uri.fromParts(PhoneAccount.SCHEME_SIP, callSessionId, null)
}

class YapConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val extras = request?.extras
        val callExtras = extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS) ?: extras
        val sessionId = YapTelecomRegistry.extractSessionId(callExtras)
            ?: YapTelecomRegistry.extractSessionId(extras)
            ?: "unknown"
        val callerName = callExtras?.getString(YapTelecomRegistry.EXTRA_CALLER_NAME).orEmpty()
        val isVideo = callExtras?.getBoolean(YapTelecomRegistry.EXTRA_IS_VIDEO) == true
        val connection = YapTelecomConnection(
            callSessionId = sessionId,
            callerDisplayName = callerName.ifBlank { "Unknown" },
            isVideoCall = isVideo,
        )
        connection.setInitializing()
        connection.setRinging()
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        }
        YapTelecomRegistry.trackConnection(sessionId, connection)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val sessionId = request?.address?.schemeSpecificPart ?: "outgoing"
        val connection = YapTelecomConnection(
            callSessionId = sessionId,
            callerDisplayName = "Outgoing",
            isVideoCall = false,
        )
        connection.setDialing()
        YapTelecomRegistry.trackConnection(sessionId, connection)
        return connection
    }
}

class YapTelecomConnection(
    private val callSessionId: String,
    private val callerDisplayName: String,
    private val isVideoCall: Boolean,
) : Connection() {

    override fun onAnswer() {
        CallDiagnostics.stage(stage = "CALL_TELECOM_ANSWER", callSessionId = callSessionId)
        setActive()
        CallNativeBridge.onAnswerFromNative(callSessionId)
        launchCallUi()
    }

    override fun onReject() {
        CallDiagnostics.stage(stage = "CALL_TELECOM_REJECT", callSessionId = callSessionId)
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        CallNativeBridge.onRejectFromNative(callSessionId)
    }

    override fun onDisconnect() {
        CallDiagnostics.stage(stage = "CALL_TELECOM_DISCONNECT", callSessionId = callSessionId)
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        CallNativeBridge.onEndFromNative(callSessionId)
    }

    override fun onAbort() {
        onReject()
    }

    private fun launchCallUi() {
        val context = org.koin.mp.KoinPlatform.getKoin().get<Context>()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(YapTelecomRegistry.EXTRA_CALL_SESSION_ID, callSessionId)
            putExtra("yapyap_open_call", true)
        } ?: return
        context.startActivity(launchIntent)
    }
}

class YapInCallService : android.telecom.InCallService() {
    override fun onCallAdded(call: android.telecom.Call) {
        super.onCallAdded(call)
        CallDiagnostics.stage(stage = "CALL_TELECOM_INCALL_ADDED")
    }

    override fun onCallRemoved(call: android.telecom.Call) {
        super.onCallRemoved(call)
        CallDiagnostics.stage(stage = "CALL_TELECOM_INCALL_REMOVED")
    }
}

class AndroidCallNativeController(
    private val appContext: Context,
) : CallNativeController {
    override fun registerPhoneAccount() {
        YapTelecomRegistry.registerPhoneAccount(appContext)
        PushNotificationChannels.create(appContext)
    }

    override fun showIncomingCall(request: CallNativeIncomingRequest) {
        YapTelecomRegistry.addIncomingCall(appContext, request)
    }

    override fun showOutgoingCall(request: CallNativeIncomingRequest) {
        // Outgoing uses in-app UI; Telecom tracks via Connection when needed.
    }

    override fun setCallActive(callSessionId: String, isVideoCall: Boolean) {
        YapTelecomRegistry.connectionFor(callSessionId)?.setActive()
    }

    override fun dismissCall(callSessionId: String, reason: CallNativeDismissReason) {
        YapTelecomRegistry.dismissCall(callSessionId)
        cancelIncomingNotification(callSessionId)
    }

    override fun cancelIncomingNotification(callSessionId: String) {
        val manager = appContext.getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.cancel("call_$callSessionId", callSessionId.hashCode())
    }

    override fun showMissedCallNotification(callSessionId: String, callerDisplayName: String) {
        PushNotificationChannels.create(appContext)
        val notification = androidx.core.app.NotificationCompat.Builder(appContext, PushNotificationChannels.CALLS)
            .setContentTitle("Missed call")
            .setContentText(callerDisplayName)
            .setSmallIcon(
                appContext.resources.getIdentifier("ic_notification", "drawable", appContext.packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.sym_call_missed,
            )
            .setAutoCancel(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .build()
        val manager = appContext.getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify("missed_$callSessionId", callSessionId.hashCode(), notification)
    }
}
