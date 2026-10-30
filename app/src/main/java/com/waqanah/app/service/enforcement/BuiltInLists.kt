package com.waqanah.app.service.enforcement

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

object BuiltInLists {

    val blocklist: Set<String> = setOf(
        "com.UCMobile.intl",
        "com.uc.browser.en",
        "com.nemo.vidmate",
        "org.torproject.torbrowser",
        "org.torproject.android",
        "com.psiphon3",
        "com.psiphon3.subscription",
        "com.expressvpn.vpn",
        "com.nordvpn.android",
        "com.surfshark.vpn"
    )

    val systemBrowsers: Set<String> = setOf(
        "com.sec.android.app.sbrowser",
        "com.mi.globalbrowser",
        "com.android.browser",
        "com.heytap.browser",
        "com.vivo.browser",
        "com.huawei.browser"
    )

    val protectedStatic: Set<String> = setOf(
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.providers.downloads",
        "com.android.providers.media",
        "com.android.providers.settings",
        "com.android.providers.telephony"
    )

    fun getRuntimeProtectedPackages(context: Context): Set<String> {
        val result = protectedStatic.toMutableSet()

        // Own package
        result.add(context.packageName)

        // Default Launcher
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(homeIntent, 0)
        resolveInfo?.activityInfo?.packageName?.let { result.add(it) }

        // Default Dialer
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecomManager?.defaultDialerPackage?.let { result.add(it) }

        // Default SMS App
        Telephony.Sms.getDefaultSmsPackage(context)?.let { result.add(it) }

        // Enabled Input Method Packages
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList?.forEach { imi ->
            result.add(imi.packageName)
        }

        return result
    }
}
