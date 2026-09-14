package com.cclilshy.tayc.event.domain;

import com.cclilshy.tayc.event.permission.EventListenerPermissionSnapshot;

public final class EventListenerSubscriptionPolicy {
    private EventListenerSubscriptionPolicy() {
    }

    public static EventListenerSubscriptionState sync(
            boolean callEnabled,
            boolean smsEnabled,
            boolean notificationEnabled,
            EventListenerPermissionSnapshot permissions) {
        return new EventListenerSubscriptionState(
                callEnabled && permissions.isCallGranted(),
                smsEnabled && permissions.isSmsGranted(),
                notificationEnabled && permissions.isNotificationGranted());
    }
}
