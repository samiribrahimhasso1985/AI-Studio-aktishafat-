package com.iktishafat.teleprompter.ui.prompter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.Text
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.iktishafat.teleprompter.domain.model.PrompterSettings
import com.iktishafat.teleprompter.domain.text.Paragraph
import com.iktishafat.teleprompter.ui.theme.StudioColors
import kotlinx.coroutines.isActive

private val markerRegex = Regex("\\[[^\\]\\n]{1,40}]")

/**
 * Renders the scrolling script. Scrolling is done with a graphics-layer translation so
 * that it is sub-pixel smooth at any refresh rate; the frame loop lives here.
 *
 * @param backgroundOpacity 0 = fully transparent (camera overlay), 1 = solid black.
 */
@Composable
fun PrompterSurface(
    controller: PrompterController,
    settings: PrompterSettings,
    backgroundOpacity: Float,
    modifier: Modifier = Modifier,
    mirror: Boolean = true,
    onTap: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val textColor = Color(settings.textColor.argb)
    val spokenColor = textColor.copy(alpha = 0.38f)
    val rtl = controller.language.isRtl
    controller.readingLineFraction = settings.readingLineFraction

    LaunchedEffect(controller) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) controller.onFrame((now - last) / 1_000_000f, now / 1_000_000)
                last = now
            }
        }
    }

    val textStyle = remember(settings.fontSizeSp, settings.lineSpacing, textColor) {
        TextStyle(
            color = textColor,
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
            fontWeight = FontWeight.Medium,
            textDirection = TextDirection.Content,
        )
    }
    val flipX = mirror && settings.mirrorHorizontal
    val flipY = mirror && settings.mirrorVertical

    BoxWithConstraints(
        modifier
            .background(Color.Black.copy(alpha = backgroundOpacity))
            .clipToBounds()
            .onSizeChanged { controller.setViewport(it.height.toFloat()) }
            .pointerInput(settings.tapToControl) {
                detectTapGestures(onTap = { if (settings.tapToControl) controller.togglePlayPause(); onTap() })
            }
            .pointerInput(flipY) {
                detectVerticalDragGestures(
                    onDragEnd = onDragEnd,
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        controller.drag(if (flipY) amount else -amount)
                    },
                )
            }
            .testTag("prompter_surface"),
    ) {
        val viewport = maxHeight
        val horizontalPad = maxWidth * settings.horizontalMarginFraction
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (flipX) -1f else 1f; scaleY = if (flipY) -1f else 1f },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = -controller.position }
                        .onSizeChanged { controller.setContentHeight(it.height.toFloat()) }
                        .padding(horizontal = horizontalPad),
                ) {
                    Spacer(Modifier.height(viewport * settings.readingLineFraction))
                    controller.paragraphs.forEach { p ->
                        val spokenLocal = (controller.spokenUntil - p.start).coerceIn(0, p.text.length)
                        val wordLocal = if (controller.currentWordStart in p.start until p.end) controller.currentWordStart - p.start else -1
                        val annotated = remember(p.text, spokenLocal, wordLocal, textColor) {
                            buildParagraph(p, spokenLocal, wordLocal, spokenColor)
                        }
                        Text(
                            text = annotated,
                            style = if (p.isHeading) textStyle.copy(fontSize = (settings.fontSizeSp * 0.5f).sp, color = StudioColors.Gold, fontWeight = FontWeight.SemiBold, lineHeight = (settings.fontSizeSp * 0.8f).sp) else textStyle,
                            onTextLayout = { controller.onParagraphLayout(p.index, it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { c -> controller.onParagraphPlaced(p.index, c.positionInParent().y, c.size.height.toFloat()) }
                                .padding(bottom = settings.paragraphSpacingDp.dp),
                        )
                    }
                    Spacer(Modifier.height(viewport * (1f - settings.readingLineFraction)))
                }
            }
            if (settings.showReadingLine) {
                ReadingLine(
                    yPx = with(density) { (viewport * settings.readingLineFraction).toPx() },
                    lineHeightPx = with(density) { (settings.fontSizeSp * settings.lineSpacing).sp.toPx() },
                )
            }
        }
    }
}

private fun buildParagraph(p: Paragraph, spokenLocal: Int, wordLocal: Int, spoken: Color): AnnotatedString {
    if (p.isHeading) return AnnotatedString(p.displayText)
    val text = p.text
    return buildAnnotatedString {
        append(text)
        if (spokenLocal > 0) addStyle(SpanStyle(color = spoken), 0, spokenLocal)
        if (wordLocal in 0 until spokenLocal) {
            // Current word: gold underline-like highlight. Arabic shaping is preserved because
            // only colour/background spans are applied, never splitting the text.
            addStyle(SpanStyle(color = StudioColors.Gold, background = StudioColors.Gold.copy(alpha = 0.16f)), wordLocal, spokenLocal)
        }
        markerRegex.findAll(text).forEach { m ->
            addStyle(SpanStyle(color = StudioColors.Gold.copy(alpha = 0.85f), fontSize = 0.55.em, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
    }
}

@Composable
private fun ReadingLine(yPx: Float, lineHeightPx: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val band = Color(0x14D4AF37)
        drawRect(band, topLeft = Offset(0f, yPx - lineHeightPx * 0.1f), size = androidx.compose.ui.geometry.Size(size.width, lineHeightPx * 1.2f))
        val cy = yPx + lineHeightPx * 0.5f
        val s = 14.dp.toPx()
        val left = Path().apply { moveTo(0f, cy - s); lineTo(s * 1.2f, cy); lineTo(0f, cy + s); close() }
        val right = Path().apply { moveTo(size.width, cy - s); lineTo(size.width - s * 1.2f, cy); lineTo(size.width, cy + s); close() }
        drawPath(left, StudioColors.Gold)
        drawPath(right, StudioColors.Gold)
    }
}
