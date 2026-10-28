package com.xb.tool.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.R
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.Intents
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun AboutScreen(state: AppState) {
    var dialog by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_preview),
                                contentDescription = "XB工具图标",
                                modifier = Modifier
                                    .size(82.dp)
                                    .clip(RoundedCornerShape(26.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("XB工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "v1.3.7 (137)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "集成 Root / 内核环境检测、实时病毒检测、强力杀毒与系统优化的手机安全工具。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.navigate(Screen.Author) }
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.avatar_author),
                        contentDescription = "作者头像",
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "作者 · 可怜的小兵",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "kelianxiaobingcn@vip.qq.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "点击卡片写邮件反馈问题或建议",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            XBListRow(
                icon = Icons.Default.Description,
                title = "开源许可",
                subtitle = "第三方组件清单、Apache 2.0 / LGPL 2.1 / MPL 2.0 全文",
                onClick = { state.navigate(Screen.License) }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Gavel,
                title = "用户协议与法律条款",
                subtitle = "使用本应用前请阅读并同意",
                onClick = {
                    dialog = "【用户协议】\n" +
                        "1. 本应用为本地安全检测与系统工具，按“现状”提供，不承诺查杀率或绝对安全。\n" +
                        "2. 你应确保对设备拥有合法控制权；在他人设备上使用须获得明确授权。\n" +
                        "3. Root / Shizuku / 应用冻结 / 文件删除等操作具有风险，可能导致数据丢失或系统异常，请自行备份并谨慎操作。\n" +
                        "4. 病毒库与公开哈希来自第三方，可能存在误报或漏报，重要文件请自行复核。\n" +
                        "5. 本应用可能包含第三方开源组件，其许可条款同样适用于对应组件。\n\n" +
                        "【法律条款】\n" +
                        "1. 严禁将本应用用于任何违反中华人民共和国法律法规及你所在司法辖区法律的用途。\n" +
                        "2. 严禁用于未经授权访问、控制、破坏他人设备或网络，严禁用于制作、传播恶意软件。\n" +
                        "3. 严禁用于侵犯他人隐私、商业秘密或其他合法权益。\n" +
                        "4. 因违反上述条款产生的法律责任由使用者自行承担，作者不承担连带责任。\n" +
                        "5. 本条款不排除或限制法律强制规定不得排除或限制的责任。"
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Warning,
                title = "作者声明",
                subtitle = "使用边界与免责说明",
                onClick = {
                    dialog = "本软件由「可怜的小兵」个人开发与维护，仅供设备安全自检、学习与研究使用。\n\n" +
                        "严禁将本软件用于任何非法用途，包括但不限于：\n" +
                        "· 未经授权侵入、控制、破坏他人设备或网络；\n" +
                        "· 制作、传播、伪装恶意软件或实施诈骗；\n" +
                        "· 侵犯他人隐私、数据、商业秘密或其他合法权益；\n" +
                        "· 规避法律监管或从事其他违法活动。\n\n" +
                        "使用者应自行确认其行为符合当地法律法规。因滥用本软件造成的一切后果由使用者承担，作者不承担任何直接或间接责任。\n\n" +
                        "如果你不同意上述声明，请立即停止使用并卸载本应用。"
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.PrivacyTip,
                title = "隐私说明",
                subtitle = "所有检测结果仅保存在本机",
                onClick = {
                    dialog = "XB工具不会上传扫描文件、应用列表或 Root 信息。\n\n" +
                        "病毒库、设置、白名单与拦截记录均存放在本机应用数据目录。\n" +
                        "卸载应用后上述数据会一并删除。\n\n" +
                        "Root / Shizuku 授权仅用于深度扫描、应用冻结与缓存清理。"
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Email,
                title = "意见反馈",
                subtitle = "作者页面 / 反馈邮箱 / 复制内容",
                onClick = { state.navigate(Screen.Author) }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Feedback,
                title = "使用提示",
                subtitle = "强力杀毒需要 Root 或 Shizuku；删除系统文件前请谨慎",
                onClick = {
                    dialog = "1. 普通杀毒扫描已安装应用与常用下载目录，无需任何权限。\n\n" +
                        "2. 强力杀毒可读取系统分区与受保护目录，需要 Root 或 Shizuku。\n\n" +
                        "3. 实时检测会常驻通知并监控下载目录与新安装应用。\n\n" +
                        "4. 应用冻结、系统缓存清理等操作具有风险，请确认目标应用后再执行。"
                }
            )
        }
        item {
            Text(
                "本工具仅供设备安全自检与学习研究，请勿用于非法用途。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    dialog?.let { text ->
        XBInfoDialog(title = "说明", text = text, onDismiss = { dialog = null })
    }
}
