package com.fixflow.group;

import com.fixflow.group.service.SharingGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Existing groups were created before they had a code a shop can type. */
@Component
@RequiredArgsConstructor
public class GroupJoinCodeBackfill implements ApplicationRunner {

    private final SharingGroupService groups;

    @Override
    public void run(ApplicationArguments args) {
        groups.backfillJoinCodes();
    }
}
