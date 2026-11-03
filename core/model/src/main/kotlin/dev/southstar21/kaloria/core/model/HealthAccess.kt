package dev.southstar21.kaloria.core.model

/** Whether Kaloria may read (and write) Health Connect data: granted, needs permission, Health Connect missing or outdated. */
enum class HealthAccess { Granted, NeedsPermission, NotInstalled, Unsupported }
