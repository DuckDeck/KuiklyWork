package com.ss.kuiklywork.download

internal fun recoverCosPublishedTarget(
    candidates: List<String>, isPublished: (String) -> Boolean,
    checkpoint: (String) -> Unit, remember: (String) -> Unit
): String? {
    val target = candidates.distinct().firstOrNull(isPublished) ?: return null
    checkpoint(target)
    remember(target)
    return target
}
