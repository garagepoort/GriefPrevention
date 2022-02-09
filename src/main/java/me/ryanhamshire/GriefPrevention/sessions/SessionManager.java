package me.ryanhamshire.GriefPrevention.sessions;

import be.garagepoort.mcioc.IocBean;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@IocBean
public class SessionManager {

    private final ConcurrentHashMap<UUID, Date> lastLoginThisServerSessionMap = new ConcurrentHashMap<>();

    public void addSession(UUID playerID) {
        this.lastLoginThisServerSessionMap.put(playerID, new Date());
    }

    public Date getSessionDate(UUID playerID) {
        return this.lastLoginThisServerSessionMap.get(playerID);
    }
}
