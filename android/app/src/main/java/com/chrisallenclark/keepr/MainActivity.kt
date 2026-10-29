package com.chrisallenclark.keepr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KeeprTheme {
                FoundationScreen()
            }
        }
    }
}

/**
 * Placeholder for Phase 0: proves the app builds, installs and draws in both
 * appearances. Replaced by the tab root when the real screens arrive.
 */
@Composable
fun FoundationScreen() {
    val colors = KeeprTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(52.dp))
        BasicText(
            text = "Keepr",
            modifier = Modifier.semantics { heading() },
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.label),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.secondaryGroupedBackground, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            BasicText(
                text = "Android preview",
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "The foundation is in place. Keepr's screens arrive in the next phases.",
                style = TextStyle(fontSize = 15.sp, color = colors.secondaryLabel),
            )
        }
    }
}

@Preview
@Composable
private fun FoundationScreenPreview() {
    KeeprTheme { FoundationScreen() }
}
