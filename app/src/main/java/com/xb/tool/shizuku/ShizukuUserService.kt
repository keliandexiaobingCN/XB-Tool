package com.xb.tool.shizuku

import com.xb.tool.root.Shell

/**
 * Runs inside a process started by Shizuku with shell / root identity.
 * Must extend the AIDL Stub directly; it is not a regular Android Service.
 */
class ShizukuUserService : IUserService.Stub() {
    override fun exec(cmd: String?): String? {
        if (cmd.isNullOrBlank()) return ""
        val result = Shell.exec(cmd, 30_000L)
        return buildString {
            append(result.out)
            if (result.err.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("[stderr] ").append(result.err)
            }
        }
    }
}
