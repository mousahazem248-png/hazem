package com.ultradownloader.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.data.local.ThemePreference
import com.ultradownloader.app.data.local.UserSettings
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeChange: (ThemePreference) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onConcurrentLimitChange: (Int) -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val isAr = settings.language == AppLanguage.ARABIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = if (isAr) "الإعدادات والتخصيص" else "Settings & Preferences",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Language Selection (Arabic RTL / English LTR)
        LuxuryGlassCard {
            Text(
                text = if (isAr) "اللغة واتجاه الواجهة (Language & RTL/LTR)" else "Language & Layout Direction",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = settings.language == AppLanguage.ARABIC,
                    onClick = { onLanguageChange(AppLanguage.ARABIC) },
                    label = { Text("العربية (RTL)") }
                )
                FilterChip(
                    selected = settings.language == AppLanguage.ENGLISH,
                    onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                    label = { Text("English (LTR)") }
                )
            }
        }

        // Theme Selection (Obsidian Dark / Daylight Light / System)
        LuxuryGlassCard {
            Text(
                text = if (isAr) "المظهر البصري" else "Visual Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = settings.theme == ThemePreference.DARK,
                    onClick = { onThemeChange(ThemePreference.DARK) },
                    label = { Text(if (isAr) "الداكن الفاخر" else "Obsidian Dark") }
                )
                FilterChip(
                    selected = settings.theme == ThemePreference.LIGHT,
                    onClick = { onThemeChange(ThemePreference.LIGHT) },
                    label = { Text(if (isAr) "الفاتح" else "Daylight") }
                )
                FilterChip(
                    selected = settings.theme == ThemePreference.SYSTEM,
                    onClick = { onThemeChange(ThemePreference.SYSTEM) },
                    label = { Text(if (isAr) "تلقائي" else "System") }
                )
            }
        }

        // Network & Concurrency Settings
        LuxuryGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "التنزيل عبر Wi-Fi فقط" else "Download over Wi-Fi Only",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAr) "إيقاف التنزيل التلقائي عند استخدام بيانات الهاتف" else "Pause background transfers on metered mobile data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.wifiOnly,
                    onCheckedChange = onWifiOnlyChange
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (isAr) {
                    "الحد الأقصى للتنزيلات المتزامنة: ${settings.maxConcurrentDownloads}"
                } else {
                    "Max Concurrent Downloads: ${settings.maxConcurrentDownloads}"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = settings.maxConcurrentDownloads.toFloat(),
                onValueChange = { onConcurrentLimitChange(it.roundToInt()) },
                valueRange = 1f..5f,
                steps = 3
            )
        }

        // Storage & About Links
        LuxuryGlassCard {
            Text(
                text = if (isAr) "التخزين والأمان" else "Scoped Storage & Security",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAr) {
                    "يتم حفظ الفيديوهات في Movies/UltraDownloader والملفات الصوتية في Music/UltraDownloader عبر Android MediaStore دون طلب أذونات وصول شاملة لملفاتك الخاصة."
                } else {
                    "Videos are saved to Movies/UltraDownloader and audio to Music/UltraDownloader via Android Scoped MediaStore with minimal permissions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = if (isAr) "حول التطبيق وسياسة الخصوصية" else "About & Privacy Policy")
            }
        }
    }
}
