package com.fixflow.group.web;

import com.fixflow.group.domain.GroupRole;
import com.fixflow.group.service.SharingGroupService;
import com.fixflow.group.service.SharingGroupService.GroupCard;
import com.fixflow.group.service.SharingGroupService.GroupDetail;
import com.fixflow.group.service.SharingGroupService.MemberCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobistack/groups")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SharingGroupController {

    private final SharingGroupService groups;

    @GetMapping
    public List<GroupCard> list() {
        return groups.listVisible();
    }

    @GetMapping(params = "id")
    public GroupDetail get(@RequestParam UUID id) {
        return groups.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCard create(@Valid @RequestBody CreateGroup body) {
        return groups.create(body.name());
    }

    @PostMapping("/shops")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberCard addShop(@RequestParam UUID id, @RequestBody AddShop body) {
        return groups.addShop(id, body.workspaceId(), body.joinCode());
    }

    @DeleteMapping("/shops")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeShop(@RequestParam UUID id, @RequestParam UUID workspaceId) {
        groups.removeShop(id, workspaceId);
    }

    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberCard addPerson(@RequestParam UUID id, @Valid @RequestBody AddPerson body) {
        return groups.addPerson(id, body.email(), body.role());
    }

    @PutMapping("/people")
    public MemberCard setRole(@RequestParam UUID id, @RequestParam UUID userId, @RequestBody SetRole body) {
        return groups.setPersonRole(id, userId, body.role() == null ? GroupRole.MEMBER : body.role());
    }

    @DeleteMapping("/people")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePerson(@RequestParam UUID id, @RequestParam UUID userId) {
        groups.removePerson(id, userId);
    }

    public record CreateGroup(@NotBlank String name) {
    }

    public record AddShop(UUID workspaceId, String joinCode) {
    }

    public record AddPerson(@NotBlank String email, GroupRole role) {
    }

    public record SetRole(GroupRole role) {
    }
}
