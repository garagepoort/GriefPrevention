package me.ryanhamshire.GriefPrevention.sessions;

import be.garagepoort.mcioc.IocBean;

import java.util.HashMap;
import java.util.UUID;

@IocBean
public class LogoutMessagesService {

    private final HashMap<UUID, Integer> heldLogoutMessages = new HashMap<>();

    public void addMessage(UUID playerID, Integer taskID) {
        this.heldLogoutMessages.put(playerID, taskID);
    }

    public Integer get(UUID playerId) {
        return heldLogoutMessages.get(playerId);
    }
}
