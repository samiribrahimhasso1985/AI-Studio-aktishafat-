package com.iktishafat.teleprompter.ui.templates

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iktishafat.teleprompter.R
import com.iktishafat.teleprompter.ui.theme.StudioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/** One editing template shipped with the app (assets/templates/templates.json). */
data class StudioTemplate(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val version: String,
    val kind: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val format: String,
    val davinciPath: String,
    val thumbnail: String,
    val preview: String,
    val packageAsset: String,
    val packageFileName: String,
    val testedInDavinci: Boolean,
)

object TemplateCatalog {
    private const val INDEX = "templates/templates.json"

    fun parse(json: String): List<StudioTemplate> {
        val arr = JSONObject(json).getJSONArray("templates")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StudioTemplate(
                id = o.getString("id"),
                nameAr = o.getString("name_ar"),
                nameEn = o.getString("name_en"),
                version = o.optString("version"),
                kind = o.optString("kind"),
                descriptionAr = o.optString("description_ar"),
                descriptionEn = o.optString("description_en"),
                format = o.optString("format"),
                davinciPath = o.optString("davinci_path"),
                thumbnail = o.getString("thumbnail"),
                preview = o.optString("preview"),
                packageAsset = o.getString("package"),
                packageFileName = o.getString("package_file_name"),
                testedInDavinci = o.optBoolean("tested_in_davinci", false),
            )
        }
    }

    fun load(context: Context): List<StudioTemplate> =
        runCatching { context.assets.open(INDEX).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun image(context: Context, path: String): ImageBitmap? =
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()

    /** Copies the template package to Downloads so it can be moved to the Windows PC. Android 10+. */
    fun saveToDownloads(context: Context, t: StudioTemplate): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, t.packageFileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/IKTISHAFAT")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)!!.use { out -> context.assets.open(t.packageAsset).use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse { resolver.delete(uri, null, null); false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val templates = remember { TemplateCatalog.load(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val arabic = Locale.getDefault().language == "ar"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.templates_title)) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).testTag("templates_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(templates, key = { it.id }) { t ->
                TemplateCard(
                    t = t,
                    arabic = arabic,
                    onSave = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { TemplateCatalog.saveToDownloads(context, t) }
                            snackbar.showSnackbar(
                                if (ok) context.getString(R.string.template_saved, "Download/IKTISHAFAT/${t.packageFileName}")
                                else context.getString(R.string.template_save_failed)
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(t: StudioTemplate, arabic: Boolean, onSave: () -> Unit) {
    val context = LocalContext.current
    val thumb = remember(t.thumbnail) { TemplateCatalog.image(context, t.thumbnail) }
    var showPreview by rememberSaveable(t.id) { mutableStateOf(false) }
    val preview = remember(t.preview, showPreview) { if (showPreview && t.preview.isNotEmpty()) TemplateCatalog.image(context, t.preview) else null }

    Card(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().testTag("template_${t.id}"),
        colors = CardDefaults.cardColors(containerColor = StudioColors.Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = if (arabic) t.nameAr else t.nameEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(if (arabic) t.nameAr else t.nameEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StudioColors.Gold)
            Text(if (arabic) t.nameEn else t.nameAr, style = MaterialTheme.typography.labelMedium, color = StudioColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text(if (arabic) t.descriptionAr else t.descriptionEn, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Spec(stringResource(R.string.template_kind), "${t.kind} · v${t.version}")
            Spec(stringResource(R.string.template_format), t.format)
            Spec(stringResource(R.string.template_location), t.davinciPath)
            Spec(
                stringResource(R.string.template_status),
                stringResource(if (t.testedInDavinci) R.string.template_tested else R.string.template_not_tested),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave, modifier = Modifier.testTag("template_save")) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.padding(start = 6.dp)); Text(stringResource(R.string.template_save))
                }
                if (t.preview.isNotEmpty()) {
                    OutlinedButton(onClick = { showPreview = !showPreview }) {
                        Icon(Icons.Default.Visibility, null); Spacer(Modifier.padding(start = 6.dp)); Text(stringResource(R.string.template_preview))
                    }
                }
            }
            preview?.let {
                Spacer(Modifier.height(12.dp))
                Image(it, null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
            }
        }
    }
}

@Composable
private fun Spec(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = StudioColors.TextMuted, modifier = Modifier.widthIn(min = 110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
