package com.tms.banking.service

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tms.banking.TmsApp
import com.tms.banking.data.remote.dto.NotificationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

private val BANKING_PACKAGES = setOf(
    "com.mashreq.mobilebanking",
    "com.mashreq.corporate",
    "com.bankfab.mobilebanking",
    "com.fab.mobilebanking",
    "com.emiratesnbd.mobilebanking",
    "com.emiratesnbd",
    "community.revolut.com",
    "com.revolut.revolut",
    "de.number26.android",
    "com.n26",
    "de.sparkasse.banking",
    "com.dbs.ibanking.sg",
    "com.adcb.mobile",
    "com.adib.mobilebanking"
)

class BankingNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingQueue = ConcurrentLinkedQueue<NotificationDto>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        val isBankingApp = BANKING_PACKAGES.any { pkg.startsWith(it.substringBefore("*")) } ||
                BANKING_PACKAGES.contains(pkg)

        if (!isBankingApp) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: return

        if (text.isBlank()) return

        val dto = NotificationDto(
            bankPackage = pkg,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        scope.launch {
            sendNotification(dto)
        }
    }

    private suspend fun sendNotification(dto: NotificationDto) {
        val app = applicationContext as? TmsApp ?: return
        val url = try {
            app.container.backendUrlFlow.first()
        } catch (_: Exception) { return }

        if (url.isBlank()) {
            pendingQueue.offer(dto)
            return
        }

        try {
            val api = app.container.buildApi(url)
            api.postNotification(dto)
            // flush pending queue
            flushPendingQueue(api)
        } catch (_: Exception) {
            pendingQueue.offer(dto)
        }
    }

    private suspend fun flushPendingQueue(api: com.tms.banking.data.remote.TmsApi) {
        while (pendingQueue.isNotEmpty()) {
            val queued = pendingQueue.poll() ?: break
            try {
                api.postNotification(queued)
            } catch (_: Exception) {
                pendingQueue.offer(queued)
                break
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }
}
