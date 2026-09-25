package com.fixflow.presence.web;

import com.fixflow.common.web.ClientRequests;
import com.fixflow.presence.PresenceService;
import com.fixflow.presence.PresenceSnapshot;
import com.fixflow.security.Authorize;
import com.fixflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mobistack/presence")
@RequiredArgsConstructor
@Tag(name = "Presence")
public class PresenceController {

    private final PresenceService presenceService;

    @PostMapping("/heartbeat")
    public PresenceSnapshot heartbeat(@RequestBody(required = false) PresenceService.HeartbeatRequest request,
                                      HttpServletRequest http) {
        return presenceService.heartbeat(request, ClientRequests.ip(http));
    }

    @PostMapping("/leave")
    public void leave(@RequestBody(required = false) PresenceService.HeartbeatRequest request) {
        presenceService.leave(request == null ? null : request.deviceId());
    }

    @GetMapping
    @PreAuthorize(Authorize.USER_READ)
    public List<PresenceSnapshot> workspaceLive() {
        return presenceService.liveForWorkspace(CurrentUser.shopId());
    }
}
