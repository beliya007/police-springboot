package com.fragment.policeforce.allocation.reminder.messaging;

public final class DelayMessageTags {

    public static final String RESERVATION_REMINDER = "allocation-reminder";
    public static final String RESERVATION_AUTO_CANCEL = "allocation-auto-cancel";

    private DelayMessageTags() {
    }

    public static String tagFor(String eventType) {
        if (DelayMessageEventTypes.RESERVATION_REMINDER.equals(eventType)) {
            return RESERVATION_REMINDER;
        }
        if (DelayMessageEventTypes.RESERVATION_AUTO_CANCEL.equals(eventType)) {
            return RESERVATION_AUTO_CANCEL;
        }
        return eventType;
    }
}
