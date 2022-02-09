package me.ryanhamshire.GriefPrevention.sessions;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;

import java.util.ArrayList;
import java.util.Calendar;

@IocBean
public class NotificationService {

    private final ArrayList<Long> recentLoginLogoutNotifications = new ArrayList<>();

    //determines whether or not a login or logout notification should be silenced, depending on how many there have been in the last minute
    public boolean shouldSilenceNotification() {
        if (ConfigLoader.config_spam_loginLogoutNotificationsPerMinute <= 0) {
            return false; // not silencing login/logout notifications
        }

        final long ONE_MINUTE = 60000;
        Long now = Calendar.getInstance().getTimeInMillis();

        //eliminate any expired entries (longer than a minute ago)
        for (int i = 0; i < this.recentLoginLogoutNotifications.size(); i++) {
            Long notificationTimestamp = this.recentLoginLogoutNotifications.get(i);
            if (now - notificationTimestamp > ONE_MINUTE) {
                this.recentLoginLogoutNotifications.remove(i--);
            } else {
                break;
            }
        }

        //add the new entry
        this.recentLoginLogoutNotifications.add(now);

        return this.recentLoginLogoutNotifications.size() > ConfigLoader.config_spam_loginLogoutNotificationsPerMinute;
    }
}
