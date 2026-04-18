package com.example.ritik_2.windowscontrol.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ritik_2.windowscontrol.PcControlMain
import com.example.ritik_2.windowscontrol.PcControlSettings
import com.example.ritik_2.windowscontrol.data.PcSchedule
import com.example.ritik_2.windowscontrol.data.PcSavedDevice
import com.example.ritik_2.windowscontrol.data.PcStep
import com.example.ritik_2.windowscontrol.network.PcControlApiClient
import com.example.ritik_2.windowscontrol.network.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic scanner (min period 15 min, per WorkManager rules). Each run looks up
 * schedules due in the current local ±3 min window and fires them once —
 * [lastFiredAt] acts as an idempotence guard across overlapping ticks.
 * Individual failures are swallowed; the worker always returns success so
 * one misbehaving schedule can't poison the queue.
 */
class PcScheduleWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tickTs = System.currentTimeMillis()
        Log.d(TAG, "tick @ ${java.util.Date(tickTs)}")

        if (!PcControlMain.isInitialized) PcControlMain.init(applicationContext)
        val repo = PcControlMain.repository ?: run {
            Log.w(TAG, "repository null — PcControlMain not initialized")
            return@withContext Result.success()
        }

        val due = runCatching { repo.dueSchedulesNow() }
            .onFailure { Log.e(TAG, "dueSchedulesNow failed: ${it.message}", it) }
            .getOrNull()
            .orEmpty()

        if (due.isEmpty()) {
            Log.d(TAG, "no schedules due")
            return@withContext Result.success()
        }

        Log.i(TAG, "firing ${due.size} schedule(s)")
        for (s in due) {
            val device = runCatching { repo.findDeviceById(s.deviceId) }.getOrNull()
            if (device == null) {
                Log.w(TAG, "schedule ${s.id} references missing device ${s.deviceId}")
                repo.markScheduleFired(s.id)
                continue
            }
            Log.i(TAG, "-> ${s.action} on '${device.label}' (schedule ${s.id})")
            val outcome = runCatching { dispatch(s, device, repo) }
            outcome.onFailure {
                // Keep marking fired so a permanently-unreachable PC doesn't
                // cause the same schedule to retry every 15 min all day. The
                // user will see the missed fire in their device's connection
                // log (agent-side) or via the absence of expected state.
                Log.w(TAG, "schedule ${s.id} dispatch failed: ${it.message}")
            }
            outcome.onSuccess {
                Log.d(TAG, "schedule ${s.id} dispatched OK")
            }
            repo.markScheduleFired(s.id)
        }
        Result.success()
    }

    private suspend fun dispatch(
        s: PcSchedule,
        device: PcSavedDevice,
        repo: com.example.ritik_2.windowscontrol.data.PcControlRepository
    ) {
        when (s.action) {
            ACTION_WOL -> {
                val mac = device.macAddress ?: return
                WakeOnLan.wake(mac, device.broadcastAddress, device.wolPort)
            }
            ACTION_SHUTDOWN, ACTION_SLEEP, ACTION_LOCK -> {
                val api = PcControlApiClient(
                    PcControlSettings(device.host, device.port, device.secretKey)
                )
                api.executeQuickStep(PcStep(type = "SYSTEM_CMD", value = s.action))
            }
            ACTION_EXECUTE_PLAN -> {
                val pid = s.planId ?: return
                val plan = repo.getPlanById(pid) ?: return
                val api = PcControlApiClient(
                    PcControlSettings(device.host, device.port, device.secretKey)
                )
                api.executePlan(plan)
            }
            else -> Log.w(TAG, "unknown action '${s.action}' on schedule ${s.id}")
        }
    }

    companion object {
        private const val TAG = "PcScheduleWorker"

        const val ACTION_WOL           = "WOL"
        const val ACTION_SHUTDOWN      = "SHUTDOWN"
        const val ACTION_SLEEP         = "SLEEP"
        const val ACTION_LOCK          = "LOCK"
        const val ACTION_EXECUTE_PLAN  = "EXECUTE_PLAN"

        const val UNIQUE_NAME = "pc_schedule_tick"
    }
}
