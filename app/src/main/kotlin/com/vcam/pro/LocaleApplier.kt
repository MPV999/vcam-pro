package com.vcam.pro

import android.content.Context
import android.content.res.Configuration
import com.vcam.core.storage.AppLanguage
import java.util.Locale

/**
 * Applies the stored UI language to the app's own resources.
 *
 * Called from [MainActivity.onCreate] before the content is set; the Settings screen calls
 * `Activity.recreate()` so a language change takes effect immediately.
 */
object LocaleApplier {

    fun apply(context: Context, language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.SYSTEM -> return
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.VIETNAMESE -> Locale("vi", "VN")
        }
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
