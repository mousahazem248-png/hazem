package com.ultradownloader.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.ui.components.GradientPrimaryButton
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import com.ultradownloader.app.ui.theme.UltraBrandGradient

private data class OnboardingPage(
    val icon: ImageVector,
    val titleAr: String,
    val titleEn: String,
    val descAr: String,
    val descEn: String
)

@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onFinishOnboarding: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Outlined.AutoAwesome,
                titleAr = "اكتشاف ذكي وفوري للمنصات",
                titleEn = "Smart Platform Detection",
                descAr = "الصق أي رابط مدعوم من YouTube أو TikTok أو Instagram أو الروابط المباشرة، وسيقوم التطبيق بالتعرف على المنصة وجلب المعاينة الرسمية فوراً.",
                descEn = "Paste any supported link from YouTube, TikTok, Instagram, or direct media streams. Ultra Downloader automatically identifies the platform and fetches legitimate metadata."
            ),
            OnboardingPage(
                icon = Icons.Outlined.HighQuality,
                titleAr = "جودات حقيقية وفحص سلامة الملفات",
                titleEn = "True Quality & Integrity Verification",
                descAr = "اختر الدقة المتاحة فعلياً من المصدر (360p حتى 1080p فما فوق) أو الصوت MP3، مع فحص اكتمال الملف وبصمة SHA-256 قبل عرضه كمكتمل.",
                descEn = "Select genuine resolutions provided by the source (360p to 1080p+) or MP3 audio. Every file is verified with SHA-256 and container checks."
            ),
            OnboardingPage(
                icon = Icons.Outlined.Shield,
                titleAr = "أمان فائق واحترام للخصوصية",
                titleEn = "Hardened Security & Platform Compliance",
                descAr = "حماية مدمجة ضد هجمات SSRF ومسارات الملفات الخبيثة، دون طلب أو تخزين أي كلمات مرور أو حسابات لمنصات التواصل.",
                descEn = "Built-in SSRF network protection, Path Traversal sanitization, zero social credential storage, and full respect for platform Terms of Service."
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val isAr = language == AppLanguage.ARABIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ultra Downloader",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onFinishOnboarding) {
                Text(
                    text = if (isAr) "تخطي" else "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedContent(
            targetState = pages[currentIndex],
            label = "OnboardingPageTransition"
        ) { page ->
            LuxuryGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(UltraBrandGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (isAr) page.titleAr else page.titleEn,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isAr) page.descAr else page.descEn,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { idx ->
                    val selected = idx == currentIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (selected) 28.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            GradientPrimaryButton(
                text = if (currentIndex == pages.lastIndex) {
                    if (isAr) "ابدأ الاستخدام الآن" else "Start Using Ultra Downloader"
                } else {
                    if (isAr) "التالي" else "Next"
                },
                onClick = {
                    if (currentIndex < pages.lastIndex) {
                        currentIndex++
                    } else {
                        onFinishOnboarding()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
