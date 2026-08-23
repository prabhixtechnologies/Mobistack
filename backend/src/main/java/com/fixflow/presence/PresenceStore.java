package com.fixflow.presence;

import java.util.List;

public interface PresenceStore {

    void heartbeat(PresenceSnapshot snapshot);

    List<PresenceSnapshot> listLive();

    void leave(String userId, String deviceId);
}
