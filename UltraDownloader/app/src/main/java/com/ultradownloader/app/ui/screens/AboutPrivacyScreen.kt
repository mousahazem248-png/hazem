package com.ultradownloader.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.ui.components.LuxuryGlassCard

@Composable
fun AboutPrivacyScreen(
    language: AppLanguage,
    onBack: () -> Unit
) {
    val isAr = language == AppLanguage.ARABIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
            Text(text = if (isAr) "رجوع للإعدادات" else "Back to Settings")
        }

        LuxuryGlassCard {
            Text(
                text = "Ultra Downloader v1.0.0",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAr) {
                    "تطبيق أندرويد احترافي مبني بـ Kotlin وJetpack Compose وفق معمارية Clean Architecture + MVVM."
                } else {
                    "Production Android application engineered with Kotlin, Jetpack Compose, Clean Architecture, and MVVM."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LuxuryGlassCard {
            Text(
                text = if (isAr) "سياسة الخصوصية والأمان" else "Privacy & Security Policy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            val bullets = if (isAr) {
                listOf(
                    "عدم تخزين بيانات الدخول: لا يطلب التطبيق أو يخزن أي كلمات مرور أو رموز جلسات (Cookies) لحساباتك في YouTube أو TikTok أو Instagram.",
                    "احترام الحافظة: لا يقرأ التطبيق الحافظة في الخلفية مطلقاً، بل يقرأ النص فقط عندما تضغط بنفسك على زر «لصق من الحافظة».",
                    "الحماية من هجمات SSRF ومسارات الملفات: يتم فحص كل رابط لمنع الاتصال بالشبكات الداخلية (127.0.0.1، 192.168.x.x، 10.x.x.x، 169.254.169.254) وتطهير أسماء الملفات ضد Path Traversal.",
                    "ملفاتك تبقى على جهازك: لا يقوم التطبيق برفع ملفاتك أو سجل تنزيلاتك إلى أي خادم خارجي."
                )
            } else {
                listOf(
                    "Zero Credential Storage: Ultra Downloader never requests or stores passwords, OAuth tokens, or session cookies for social accounts.",
                    "Clipboard Respect: The clipboard is only read when you explicitly press the 'Paste Link' button.",
                    "SSRF & Path Traversal Hardening: All URLs are validated against RFC1918/loopback/metadata IP ranges, and filenames are sanitized against traversal.",
                    "On-Device Privacy: Your downloaded files and transfer history remain strictly on your device."
                )
            }
            bullets.forEach { b ->
                Text(
                    text = "• $b",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
