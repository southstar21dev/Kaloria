package dev.southstar21.kaloria.core.data.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in whatever time zone the phone is in *now*. `Clock.systemDefaultZone()` fixes
 * the zone when it's created, so a long-lived singleton would keep the old day boundaries after
 * travelling or a zone change.
 */
internal object CurrentZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}
