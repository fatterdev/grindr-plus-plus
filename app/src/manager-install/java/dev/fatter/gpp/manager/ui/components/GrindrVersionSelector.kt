package dev.fatter.gpp.manager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fatter.gpp.manager.installation.DownloadSource
import dev.fatter.gpp.manager.installation.GrindrVersion
import dev.fatter.gpp.manager.installation.InstallStatus

private val WorkingBg = Color(0xFF1B5E20)
private val WorkingFg = Color(0xFFC8E6C9)
private val NotWorkingBg = Color(0xFF7F1D1D)
private val NotWorkingFg = Color(0xFFFFCDD2)
private val UntestedBg = Color(0xFF4E342E)
private val UntestedFg = Color(0xFFFFE0B2)

@Composable
fun GrindrVersionSelector(
    versions: List<GrindrVersion>,
    selected: GrindrVersion?,
    onSelected: (GrindrVersion) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Host version",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Installed APK first; downloadable versions are the fallback when the installed host has no working patch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        if (versions.isEmpty()) {
            Text(
                text = "No installable host version. Install Grindr from Play or check the availability server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            versions.forEach { version ->
                val isSelected = selected?.versionCode == version.versionCode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isEnabled) { onSelected(version) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = if (isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = version.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            StatusBadge(version.status)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sourceLabel(version),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Alloy: ${version.alloy.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (version.pairip) {
                                Text(
                                    text = "PairIP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (isSelected && !version.notes.isNullOrBlank()) {
                            Text(
                                text = version.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: InstallStatus) {
    val (bg, fg) = when (status) {
        InstallStatus.WORKING -> WorkingBg to WorkingFg
        InstallStatus.NOT_WORKING -> NotWorkingBg to NotWorkingFg
        InstallStatus.UNTESTED -> UntestedBg to UntestedFg
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun sourceLabel(version: GrindrVersion): String = when {
    version.installed -> "Installed"
    version.download == DownloadSource.AURORA -> "Aurora / Play"
    version.download == DownloadSource.URL -> "Server APK"
    else -> "No download"
}
