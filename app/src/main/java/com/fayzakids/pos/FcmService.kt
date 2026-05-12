package com.fayzakids.pos

/**
 * FcmService — Push Notification via Firebase Cloud Messaging (OPSIONAL)
 *
 * Untuk mengaktifkan FCM:
 * 1. Buat project di https://console.firebase.google.com
 * 2. Tambahkan app Android (package: com.fayzakids.pos)
 * 3. Download google-services.json → taruh di folder app/
 * 4. Di app/build.gradle: uncomment baris Firebase (plugin + dependencies)
 * 5. Uncomment kode di bawah ini
 * 6. Di PHP: include fcm_notify.php dan panggil sendTransactionNotification()
 *
 * Tanpa FCM: notifikasi tetap bekerja via JS bridge (window.AndroidPrinter.showNotification)
 * saat aplikasi sedang terbuka.
 */

// import com.google.firebase.messaging.FirebaseMessagingService
// import com.google.firebase.messaging.RemoteMessage
//
// class FcmService : FirebaseMessagingService() {
//     override fun onNewToken(token: String) { super.onNewToken(token) }
//     override fun onMessageReceived(message: RemoteMessage) {
//         super.onMessageReceived(message)
//         val title = message.notification?.title ?: message.data["title"] ?: "Transaksi Baru"
//         val body  = message.notification?.body  ?: message.data["body"]  ?: "Ada transaksi masuk"
//         NotificationHelper.createChannel(this)
//         NotificationHelper.show(this, title, body)
//     }
// }
