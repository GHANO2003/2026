package com.example.core.models

enum class DiagnosticStatus(val labelEn: String, val labelAr: String) {
    SUPPORTED("SUPPORTED", "مدعوم"),
    PARTIALLY_SUPPORTED("PARTIALLY SUPPORTED", "مدعوم جزئياً"),
    NOT_SUPPORTED("NOT SUPPORTED", "غير مدعوم"),
    NOT_TESTED("NOT TESTED", "لم يتم الاختبار")
}

data class DiagnosticCheckItem(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val status: DiagnosticStatus,
    val detailEn: String,
    val detailAr: String
)

data class CompatibilityReport(
    val targetModel: CameraModelId,
    val testedAtMs: Long = 0L,
    val checks: List<DiagnosticCheckItem> = emptyList(),
    val isRunning: Boolean = false
)
