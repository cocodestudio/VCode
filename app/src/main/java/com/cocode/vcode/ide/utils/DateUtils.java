package com.cocode.vcode.ide.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility methods for formatting timestamps and generating relative time strings (e.g. "5 minutes ago").
 */
public class DateUtils {

    private DateUtils() {
    }

    /**
     * Returns a human-readable relative time string for the given date (e.g. "just now", "5 minutes ago").
     */
    public static String getRelativeTime(Date date) {
        if (date == null) return "unknown";
        long now = System.currentTimeMillis();
        long diff = now - date.getTime();

        if (diff < 0) return "just now";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (seconds < 60) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        if (weeks < 5) return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");
        return years + (years == 1 ? " year ago" : " years ago");
    }

    /**
     * Formats a date into a full date and time string (e.g. "Jan 15, 2026 · 14:30").
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(date);
    }

    /**
     * Formats a date into a short date string (e.g. "Jan 15, 2026").
     */
    public static String formatDateShort(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
    }
}