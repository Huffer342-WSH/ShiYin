package com.Huffer342.shiyin.guard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GuardActivity : Activity() {
    private lateinit var summaryText: TextView
    private lateinit var exactButton: Button
    private lateinit var notificationButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }

        val title = TextView(this).apply {
            text = "时音闹钟守护"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }
        panel.addView(title)

        summaryText = TextView(this).apply {
            textSize = 15f
            setLineSpacing(6f, 1f)
            setPadding(0, 0, 0, 32)
        }
        panel.addView(summaryText)

        fun addButton(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setOnClickListener { action() }
            panel.addView(this)
        }

        exactButton = addButton("允许精确闹钟") {
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
        }

        notificationButton = addButton("允许通知（用于主应用卸载提醒）") {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                openAppSettings()
            }
        }

        addButton("打开系统应用设置（开启自启动 / 后台耗电）") {
            openAppSettings()
        }

        addButton("完成设置并隐藏卡片") {
            finishAndRemoveTask()
        }

        addButton("返回时音") {
            if (GuardAlarmStore.isMainAppInstalled(this)) {
                runCatching {
                    startActivity(
                        Intent()
                            .setClassName(GuardAlarmStore.MAIN_PACKAGE, "${GuardAlarmStore.MAIN_PACKAGE}.MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            finishAndRemoveTask()
        }

        addButton("卸载守护组件") {
            runCatching {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
            }
        }

        scroll.addView(panel)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        GuardAlarmStore.rescheduleAll(this)
        refresh()
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refresh() {
        val mainInstalled = GuardAlarmStore.isMainAppInstalled(this)
        val exactAllowed = GuardAlarmStore.canScheduleExactAlarms(this)
        val notifyAllowed = GuardAlarmStore.notificationsEnabled(this)
        val activeCount = GuardAlarmStore.load(this).size
        summaryText.text = buildString {
            appendLine("时音主应用：${if (mainInstalled) "已安装" else "未安装"}")
            appendLine("守护精确闹钟：${if (exactAllowed) "已允许" else "未允许（必选，否则无法准时唤醒）"}")
            appendLine("通知权限：${if (notifyAllowed) "已允许" else "未允许（可选，主应用卸载时提醒卸载守护）"}")
            appendLine("已同步未来闹钟：$activeCount 个")
            appendLine()
            append("说明：本组件无桌面图标，也不会常驻最近任务卡片。在 vivo 等国产系统上，请进入系统应用设置开启“自启动 / 关联启动”和“允许后台高耗电”，设置完成后点击“完成设置并隐藏卡片”。")
        }
        exactButton.isEnabled = !exactAllowed
        exactButton.text = if (exactAllowed) "精确闹钟已允许" else "允许精确闹钟"
        notificationButton.text = if (notifyAllowed) "通知已允许（可点此调整）" else "允许通知（用于主应用卸载提醒）"
    }
}
