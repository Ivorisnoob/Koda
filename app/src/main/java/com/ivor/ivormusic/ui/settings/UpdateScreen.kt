package com.ivor.ivormusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.data.UpdateRepository
import com.ivor.ivormusic.data.UpdateResult
import kotlinx.coroutines.delay

/**
 * 🌟 Premium Update Screen
 * 
 * A beautiful, Material You-styled update experience with:
 * - Animated hero section with version info
 * - Rich release notes with markdown rendering
 * - Release image gallery
 * - Smart ABI-aware download button
 * - Smooth micro-animations throughout
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val updateRepository = remember { UpdateRepository() }
    var updateResult by remember { mutableStateOf<UpdateResult>(UpdateResult.Checking) }
    
    // Colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    // Stagger animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(updateResult) {
        if (updateResult is UpdateResult.Checking) {
            updateResult = updateRepository.checkForUpdate(
                repoPath = BuildConfig.GITHUB_REPO,
                currentVersion = BuildConfig.VERSION_NAME
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 100.dp
            )
        ) {
            // ===== TOP BAR =====
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Software Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
            
            // ===== HERO SECTION =====
            item {
                UpdateHeroSection(
                    updateResult = updateResult,
                    primaryColor = primaryColor,
                    tertiaryColor = tertiaryColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    isVisible = isVisible
                )
            }
            
            // ===== CONTENT BASED ON STATE =====
            when (val result = updateResult) {
                is UpdateResult.Checking -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Checking for updates...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }
                }
                
                is UpdateResult.UpdateAvailable -> {
                    
                    // What's New Section
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400, delayMillis = 300)) + slideInVertically(
                                initialOffsetY = { 60 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            WhatsNewSection(
                                releaseNotes = result.releaseNotes,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                    
                    // Download Section
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400, delayMillis = 400)) + slideInVertically(
                                initialOffsetY = { 60 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            DownloadSection(
                                result = result,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }
                
                is UpdateResult.UpToDate -> {
                    item {
                        UpToDateSection(
                            version = result.currentVersion,
                            primaryColor = primaryColor,
                            surfaceColor = surfaceColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
                
                is UpdateResult.Error -> {
                    item {
                        ErrorSection(
                            message = result.message,
                            onRetry = {
                                updateResult = UpdateResult.Checking
                            },
                            surfaceColor = surfaceColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            primaryColor = primaryColor
                        )
                    }

                }
                
                is UpdateResult.NoReleases -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No releases available yet.",
                                color = secondaryTextColor,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================
// HERO SECTION
// ===========================

@Composable
private fun UpdateHeroSection(
    updateResult: UpdateResult,
    primaryColor: Color,
    tertiaryColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isVisible: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )
    
    val isUpdateAvailable = updateResult is UpdateResult.UpdateAvailable
    val isUpToDate = updateResult is UpdateResult.UpToDate
    
    val heroColor = when {
        isUpdateAvailable -> primaryColor
        isUpToDate -> Color(0xFF4CAF50)
        else -> secondaryTextColor
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(500)) + slideInVertically(
            initialOffsetY = { -40 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(32.dp))
                .drawBehind {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            heroColor.copy(alpha = 0.15f),
                            tertiaryColor.copy(alpha = 0.08f),
                            heroColor.copy(alpha = 0.12f)
                        ),
                        start = Offset(
                            size.width * gradientOffset,
                            0f
                        ),
                        end = Offset(
                            size.width * (1f - gradientOffset),
                            size.height
                        )
                    )
                    drawRect(brush)
                }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated Icon
                val iconScale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.5f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "iconScale"
                )
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .background(heroColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isUpdateAvailable -> Icons.Rounded.SystemUpdate
                            isUpToDate -> Icons.Rounded.CheckCircle
                            updateResult is UpdateResult.Checking -> Icons.Rounded.Sync
                            else -> Icons.Rounded.Info
                        },
                        contentDescription = null,
                        tint = heroColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Status Text
                Text(
                    text = when (updateResult) {
                        is UpdateResult.UpdateAvailable -> "Update Available!"
                        is UpdateResult.UpToDate -> "You're Up to Date"
                        is UpdateResult.Checking -> "Checking..."
                        is UpdateResult.Error -> "Something Went Wrong"
                        is UpdateResult.NoReleases -> "No Releases"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Version Info
                if (updateResult is UpdateResult.UpdateAvailable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = secondaryTextColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                color = secondaryTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = heroColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = heroColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "v${(updateResult as UpdateResult.UpdateAvailable).latestVersion}",
                                color = heroColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else if (updateResult is UpdateResult.UpToDate) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = heroColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = heroColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
                
                // Release name
                if (updateResult is UpdateResult.UpdateAvailable) {
                    val result = updateResult as UpdateResult.UpdateAvailable
                    if (result.releaseName.isNotBlank() && result.releaseName != result.latestVersion) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.releaseName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


// ===========================
// WHAT'S NEW SECTION
// ===========================

// Markdown Item Types
sealed class MarkdownItem {
    data class Header(val text: String, val level: Int) : MarkdownItem()
    data class Bullet(val content: AnnotatedString) : MarkdownItem()
    data class Paragraph(val content: AnnotatedString) : MarkdownItem()
    data class Image(val url: String) : MarkdownItem()
}

@Composable
private fun WhatsNewSection(
    releaseNotes: String,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Comprehensive parsing logic
    val markdownItems = remember(releaseNotes) {
        val items = mutableListOf<MarkdownItem>()
        val lines = releaseNotes.lines()
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach

            // Check for Markdown Images: ![alt](url)
            val mdImageMatch = Regex("""!\[.*?]\((.*?)\)""").find(trimmedLine)
            if (mdImageMatch != null) {
                items.add(MarkdownItem.Image(mdImageMatch.groupValues[1]))
                return@forEach
            }

            // Check for HTML Images: <img ... src="url" ... />
            val htmlImageMatch = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""").find(trimmedLine)
            if (htmlImageMatch != null) {
                items.add(MarkdownItem.Image(htmlImageMatch.groupValues[1]))
                return@forEach
            }

            // Check for RAW GitHub Attachment URLs (often used in releases)
            val rawAttachmentMatch = Regex("""https://github\.com/user-attachments/assets/[a-f0-9\-]+""").find(trimmedLine)
            if (rawAttachmentMatch != null && !trimmedLine.contains("![") && !trimmedLine.contains("<img")) {
                items.add(MarkdownItem.Image(rawAttachmentMatch.groupValues[0]))
                return@forEach
            }

            // Check for Headers: #, ##, ###, ####
            val headerMatch = Regex("""^(#{1,4})\s+(.*)$""").find(trimmedLine)
            if (headerMatch != null) {
                items.add(MarkdownItem.Header(headerMatch.groupValues[2], headerMatch.groupValues[1].length))
                return@forEach
            }

            // Check for Bullets: -, *, +
            val bulletMatch = Regex("""^[\-\*\+]\s+(.*)$""").find(trimmedLine)
            if (bulletMatch != null) {
                items.add(MarkdownItem.Bullet(parseMarkdownInline(bulletMatch.groupValues[1])))
                return@forEach
            }

            // Default: Paragraph
            items.add(MarkdownItem.Paragraph(parseMarkdownInline(trimmedLine)))
        }
        items
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.NewReleases,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "What's New",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Release Notes Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
            ) {
                val displayItems = if (isExpanded) markdownItems else markdownItems.take(12)
                
                displayItems.forEachIndexed { index, item ->
                    RenderMarkdownItem(
                        item = item,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        primaryColor = primaryColor
                    )
                    if (index < displayItems.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                if (markdownItems.size > 12 && !isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Show more",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMarkdownItem(
    item: MarkdownItem,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color
) {
    when (item) {
        is MarkdownItem.Header -> {
            Spacer(modifier = Modifier.height(if (item.level == 1) 12.dp else 6.dp))
            Text(
                text = item.text,
                style = when (item.level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.Bold,
                color = if (item.level <= 2) primaryColor else textColor
            )
        }
        is MarkdownItem.Bullet -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
        }
        is MarkdownItem.Paragraph -> {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                lineHeight = 22.sp
            )
        }
        is MarkdownItem.Image -> {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.Black.copy(alpha = 0.05f)
            ) {
                AsyncImage(
                    model = item.url,
                    contentDescription = "Release Image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Basic inline markdown parser for bold (**text**)
 */
private fun parseMarkdownInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        
        boldRegex.findAll(text).forEach { match ->
            // Append text before the match
            append(text.substring(cursor, match.range.first))
            
            // Append bold text
            withStyle(
                SpanStyle(fontWeight = FontWeight.Bold)
            ) {
                append(match.groupValues[1])
            }
            
            cursor = match.range.last + 1
        }
        
        // Append remaining text
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}



// ===========================
// DOWNLOAD SECTION
// ===========================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadSection(
    result: UpdateResult.UpdateAvailable,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val context = LocalContext.current
    val deviceAbi = remember { UpdateRepository.getDeviceAbi() }
    val bestApk = remember(result.apkAssets) { UpdateRepository.findBestApk(result.apkAssets) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Device info chip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Your Device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Device details
                DeviceDetailRow("Architecture", deviceAbi, textColor, secondaryTextColor)
                Spacer(modifier = Modifier.height(6.dp))
                DeviceDetailRow("Android", "API ${android.os.Build.VERSION.SDK_INT}", textColor, secondaryTextColor)
                
                if (bestApk != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DeviceDetailRow(
                        "APK",
                        bestApk.name,
                        textColor,
                        secondaryTextColor
                    )
                    if (bestApk.size > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DeviceDetailRow(
                            "Size",
                            formatFileSize(bestApk.size),
                            textColor,
                            secondaryTextColor
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Download Button
        Button(
            onClick = {
                val downloadUrl = bestApk?.downloadUrl ?: result.htmlUrl
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor
            )
        ) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (bestApk != null) "Download for $deviceAbi" else "View on GitHub",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // View on GitHub link
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "View Release on GitHub",
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // All APK variants
        if (result.apkAssets.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "All Variants",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            
            result.apkAssets.forEach { apk ->
                val isBest = apk == bestApk
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apk.downloadUrl))
                            context.startActivity(intent)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBest) primaryColor.copy(alpha = 0.1f) else surfaceColor,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Android,
                            contentDescription = null,
                            tint = if (isBest) primaryColor else secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = apk.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (apk.size > 0) {
                                Text(
                                    text = formatFileSize(apk.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        if (isBest) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = primaryColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "Recommended",
                                    color = primaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = if (isBest) primaryColor else secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailRow(
    label: String,
    value: String,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

// ===========================
// UP TO DATE SECTION
// ===========================

@Composable
private fun UpToDateSection(
    version: String,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val greenColor = Color(0xFF4CAF50)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = greenColor.copy(alpha = 0.12f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = greenColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    "All Good!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You're running the latest version of Koda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Version $version • Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ===========================
// ERROR SECTION
// ===========================

@Composable
private fun ErrorSection(
    message: String,
    onRetry: () -> Unit,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    "Couldn't Check for Updates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    )
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ===========================
// UTILITY
// ===========================

private fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1_000_000_000 -> "%.1f GB".format(sizeBytes / 1_000_000_000.0)
        sizeBytes >= 1_000_000 -> "%.1f MB".format(sizeBytes / 1_000_000.0)
        sizeBytes >= 1_000 -> "%.1f KB".format(sizeBytes / 1_000.0)
        else -> "$sizeBytes B"
    }
}
