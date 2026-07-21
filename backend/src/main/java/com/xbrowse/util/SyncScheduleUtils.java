package com.xbrowse.util;

import com.xbrowse.entity.BrowseDirectory;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * 浏览目录同步计划工具
 */
public final class SyncScheduleUtils {

    private SyncScheduleUtils() {
    }

    /**
     * 规范化同步配置，非法值回退默认
     */
    public static void normalize(BrowseDirectory dir) {
        String mode = dir.getSyncMode();
        if (mode == null || mode.isBlank()) {
            mode = BrowseDirectory.SYNC_MODE_INTERVAL;
        }
        mode = mode.trim().toUpperCase(Locale.ROOT);
        if (!BrowseDirectory.SYNC_MODE_NONE.equals(mode)
                && !BrowseDirectory.SYNC_MODE_INTERVAL.equals(mode)
                && !BrowseDirectory.SYNC_MODE_CRON.equals(mode)) {
            mode = BrowseDirectory.SYNC_MODE_INTERVAL;
        }
        dir.setSyncMode(mode);

        if (BrowseDirectory.SYNC_MODE_INTERVAL.equals(mode)) {
            Integer value = dir.getSyncIntervalValue();
            if (value == null || value <= 0) {
                value = 5;
            }
            if (value > 9999) {
                value = 9999;
            }
            dir.setSyncIntervalValue(value);

            String unit = dir.getSyncIntervalUnit();
            if (unit == null || unit.isBlank()) {
                unit = BrowseDirectory.INTERVAL_MINUTE;
            }
            unit = unit.trim().toUpperCase(Locale.ROOT);
            if (!BrowseDirectory.INTERVAL_MINUTE.equals(unit)
                    && !BrowseDirectory.INTERVAL_HOUR.equals(unit)
                    && !BrowseDirectory.INTERVAL_DAY.equals(unit)
                    && !BrowseDirectory.INTERVAL_MONTH.equals(unit)) {
                unit = BrowseDirectory.INTERVAL_MINUTE;
            }
            dir.setSyncIntervalUnit(unit);
            dir.setSyncCron(null);
        } else if (BrowseDirectory.SYNC_MODE_CRON.equals(mode)) {
            String cron = dir.getSyncCron() == null ? "" : dir.getSyncCron().trim();
            if (!isValidCron(cron)) {
                throw new RuntimeException("Cron 表达式无效，请使用 6 位格式：秒 分 时 日 月 周");
            }
            dir.setSyncCron(cron);
        } else {
            dir.setSyncCron(null);
            dir.setNextSyncTime(null);
        }
    }

    public static boolean isValidCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            CronExpression.parse(cron);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 计算下次同步时间
     */
    public static LocalDateTime calcNextSyncTime(BrowseDirectory dir, LocalDateTime from) {
        if (dir == null || from == null) {
            return null;
        }
        String mode = dir.getSyncMode();
        if (BrowseDirectory.SYNC_MODE_NONE.equals(mode)) {
            return null;
        }
        if (BrowseDirectory.SYNC_MODE_CRON.equals(mode)) {
            try {
                CronExpression expression = CronExpression.parse(dir.getSyncCron());
                return expression.next(from);
            } catch (Exception e) {
                return null;
            }
        }
        // INTERVAL
        int value = dir.getSyncIntervalValue() == null ? 5 : dir.getSyncIntervalValue();
        String unit = dir.getSyncIntervalUnit() == null
                ? BrowseDirectory.INTERVAL_MINUTE
                : dir.getSyncIntervalUnit().toUpperCase(Locale.ROOT);
        return switch (unit) {
            case BrowseDirectory.INTERVAL_HOUR -> from.plusHours(value);
            case BrowseDirectory.INTERVAL_DAY -> from.plusDays(value);
            case BrowseDirectory.INTERVAL_MONTH -> from.plusMonths(value);
            default -> from.plusMinutes(value);
        };
    }

    /**
     * 当前是否到达应同步时间
     */
    public static boolean isDue(BrowseDirectory dir, LocalDateTime now) {
        if (dir == null || now == null) {
            return false;
        }
        String mode = dir.getSyncMode();
        if (mode == null || BrowseDirectory.SYNC_MODE_NONE.equals(mode)) {
            return false;
        }
        if (dir.getLastSyncTime() == null) {
            return true;
        }
        if (BrowseDirectory.SYNC_MODE_INTERVAL.equals(mode)) {
            LocalDateTime next = dir.getNextSyncTime();
            if (next == null) {
                next = calcNextSyncTime(dir, dir.getLastSyncTime());
            }
            return next == null || !now.isBefore(next);
        }
        if (BrowseDirectory.SYNC_MODE_CRON.equals(mode)) {
            LocalDateTime next = dir.getNextSyncTime();
            if (next == null) {
                next = calcNextSyncTime(dir, dir.getLastSyncTime());
            }
            return next != null && !now.isBefore(next);
        }
        return false;
    }

    public static String describe(BrowseDirectory dir) {
        if (dir == null) {
            return "未配置";
        }
        String mode = dir.getSyncMode();
        if (BrowseDirectory.SYNC_MODE_NONE.equals(mode)) {
            return "不同步";
        }
        if (BrowseDirectory.SYNC_MODE_CRON.equals(mode)) {
            return "Cron: " + (dir.getSyncCron() == null ? "-" : dir.getSyncCron());
        }
        int value = dir.getSyncIntervalValue() == null ? 5 : dir.getSyncIntervalValue();
        String unit = dir.getSyncIntervalUnit() == null ? BrowseDirectory.INTERVAL_MINUTE : dir.getSyncIntervalUnit();
        String unitLabel = switch (unit.toUpperCase(Locale.ROOT)) {
            case BrowseDirectory.INTERVAL_HOUR -> "小时";
            case BrowseDirectory.INTERVAL_DAY -> "天";
            case BrowseDirectory.INTERVAL_MONTH -> "月";
            default -> "分钟";
        };
        return "每 " + value + " " + unitLabel;
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
