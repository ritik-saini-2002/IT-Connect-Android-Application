package com.saini.ritik.winshare.transfer

// ─── Transfer Progress ────────────────────────────────────────────────────────

data class TransferProgress(
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isUpload: Boolean = true
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val etaSeconds: Long
        get() = if (speedBytesPerSec > 0 && totalBytes > transferredBytes)
            (totalBytes - transferredBytes) / speedBytesPerSec
        else -1L

    val formattedSpeed: String get() = fmtSpeed(speedBytesPerSec)
    val formattedProgress: String get() = "${fmtBytes(transferredBytes)} / ${fmtBytes(totalBytes)}"

    val formattedEta: String
        get() = when {
            etaSeconds < 0 -> "–"
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }

    private fun fmtBytes(b: Long): String = when {
        b <= 0 -> "0 B"
        b < 1_024 -> "$b B"
        b < 1_048_576 -> "%.1f KB".format(b / 1_024.0)
        b < 1_073_741_824L -> "%.1f MB".format(b / 1_048_576.0)
        else -> "%.2f GB".format(b / 1_073_741_824.0)
    }

    private fun fmtSpeed(bps: Long): String = when {
        bps <= 0 -> "0 B/s"
        bps < 1_024 -> "$bps B/s"
        bps < 1_048_576 -> "%.1f KB/s".format(bps / 1_024.0)
        bps < 1_073_741_824L -> "%.1f MB/s".format(bps / 1_048_576.0)
        else -> "%.2f GB/s".format(bps / 1_073_741_824.0)
    }
}

// ─── Remote File Item (from server file listing) ──────────────────────────────

data class RemoteFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long
) {
    val formattedSize: String
        get() = if (isDirectory) "–" else when {
            size < 1_024 -> "$size B"
            size < 1_048_576 -> "%.1f KB".format(size / 1_024.0)
            size < 1_073_741_824L -> "%.1f MB".format(size / 1_048_576.0)
            else -> "%.2f GB".format(size / 1_073_741_824.0)
        }

    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast(".", "")
}

// ─── Transfer Result ──────────────────────────────────────────────────────────

sealed class TransferResult {
    data class Success(val filePath: String) : TransferResult()
    data class Failure(val error: String, val cause: Throwable? = null) : TransferResult()
    object Cancelled : TransferResult()
}

// ─── Transfer State (for UI) ──────────────────────────────────────────────────

sealed class TransferState {
    object Idle : TransferState()
    object Connecting : TransferState()
    data class Transferring(val progress: TransferProgress) : TransferState()
    data class Done(val result: TransferResult) : TransferState()
}
