package com.ultradownloader.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.ui.components.GradientPrimaryButton
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import com.ultradownloader.app.ui.theme.AmberWarning
import com.ultradownloader.app.ui.theme.CrimsonError

@Composable
fun ErrorDiagnosticScreen(
    error: AppError?,
    language: AppLanguage,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val isAr = language == AppLanguage.ARABIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LuxuryGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = CrimsonError,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isAr) {
                        error?.titleAr ?: "تعذر إتمام العملية"
                    } else {
                        error?.titleEn ?: "Unable to Complete Action"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrimsonError
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isAr) {
                    error?.descriptionAr ?: "حدث خطأ غير متوقع أثناء فحص الرابط."
                } else {
                    error?.descriptionEn ?: "An unexpected error occurred while inspecting the link."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Suggested Actionable Fixes Card
        LuxuryGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isAr) "الحلول المقترحة:" else "Suggested Solutions:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val fixes = if (isAr) {
                error?.suggestedFixesAr ?: listOf("تأكد من صحة الرابط واتصالك بالإنترنت.")
            } else {
                error?.suggestedFixesEn ?: listOf("Check the URL syntax and your internet connection.")
            }

            fixes.forEach { fix ->
                Text(
                    text = "• $fix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            error?.officialFallbackUrl?.let { fallbackUrl ->
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Outlined.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isAr) "فتح الرابط في التطبيق الرسمي" else "Open in Official App")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GradientPrimaryButton(
                text = if (isAr) "العودة وتجربة رابط آخر" else "Back & Try Another Link",
                onClick = onBackToHome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
