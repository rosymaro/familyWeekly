package io.github.rosymaro.familyweekly.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.data.LoadIssue
import io.github.rosymaro.familyweekly.ui.theme.FamilyWeeklyTheme

/** 홈: 지금은 앱 이름 + 불러오기 문제 안내 + 디자인 미리보기(개발용). 스케줄 목록은 Phase 3. */
@Composable
fun HomeScreen(
    loadIssue: LoadIssue?,
    onDismissIssue: () -> Unit,
    onOpenDesignPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loadIssue != null) {
            LoadIssueCard(loadIssue, onDismissIssue)
        }
        Text(
            text = "familyWeekly",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "우리 아이 주간 시간표",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onOpenDesignPreview, modifier = Modifier.padding(top = 24.dp)) {
            Text("디자인 미리보기 (개발용)")
        }
    }
}

@Composable
private fun LoadIssueCard(issue: LoadIssue, onDismiss: () -> Unit) {
    val message = when (issue) {
        is LoadIssue.Corrupted ->
            "저장된 일정 파일을 읽지 못해 빈 상태로 시작했어요.\n원래 파일은 ${issue.backup.name}(으)로 보관해 두었어요."
        is LoadIssue.NewerVersion ->
            "더 새 버전 앱에서 만든 일정 파일이에요(형식 ${issue.fileVersion}).\n앱을 업데이트해 주세요. 그전까지는 저장하지 않아요."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (issue is LoadIssue.Corrupted) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("확인") }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    FamilyWeeklyTheme { HomeScreen(loadIssue = null, onDismissIssue = {}, onOpenDesignPreview = {}) }
}
