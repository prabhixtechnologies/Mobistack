package com.fixflow.group.service;

import com.fixflow.billing.service.BillingService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.group.domain.GroupJoinRequest;
import com.fixflow.group.domain.SharingGroup;
import com.fixflow.group.repository.GroupJoinRequestRepository;
import com.fixflow.group.repository.SharingGroupMemberRepository;
import com.fixflow.group.repository.SharingGroupRepository;
import com.fixflow.security.CurrentUser;
import com.fixflow.security.SystemRole;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.dto.WorkspaceDtos.CompleteJoinRequest;
import com.fixflow.workspace.dto.WorkspaceDtos.JoinCheckoutResponse;
import com.fixflow.workspace.service.WorkspaceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A shop owner types a fitment group's code, pays ₹50, and waits for that group's admin to admit
 * the shop. Until then the shop cannot read the group's compatibility.
 */
@Service
@RequiredArgsConstructor
public class GroupJoinService {

    private final SharingGroupRepository groups;
    private final SharingGroupMemberRepository members;
    private final GroupJoinRequestRepository requests;
    private final SharingGroupService groupService;
    private final WorkspaceAccessService workspaceAccess;
    private final BillingService billing;
    private final ShopRepository shops;
    private final UserRepository users;

    public record JoinState(String status, UUID requestId, UUID groupId, String groupName) {
        public static JoinState none() {
            return new JoinState("NONE", null, null, null);
        }
    }

    public record JoinRequestCard(UUID id, UUID shopId, String shopName, String askedBy) {
    }

    @Transactional(readOnly = true)
    public JoinState mine(UUID userId) {
        UUID shopId = CurrentUser.shopId();
        if (shopId == null) {
            return JoinState.none();
        }
        return requests.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(shopId, GroupJoinRequest.PENDING)
                .map(row -> {
                    String name = groups.findById(row.getGroupId()).map(SharingGroup::getName).orElse("Group");
                    return new JoinState(GroupJoinRequest.PENDING, row.getId(), row.getGroupId(), name);
                })
                .orElseGet(JoinState::none);
    }

    @Transactional
    public JoinCheckoutResponse checkout(UUID userId, String rawCode) {
        SharingGroup group = requireGroup(rawCode);
        UUID shopId = ownedShop(userId);
        if (members.findByGroupIdAndWorkspaceId(group.getId(), shopId).isPresent()) {
            throw ApiException.alreadyExists("This shop is already in " + group.getName() + ".");
        }
        boolean waiting = requests.findFirstByGroupIdAndWorkspaceIdAndStatus(
                group.getId(), shopId, GroupJoinRequest.PENDING).isPresent();
        if (waiting || billing.hasUnspentGroupJoin(userId, shopId, group.getId())) {
            return JoinCheckoutResponse.alreadyPaid(group.getName());
        }
        var order = billing.createGroupJoinOrder(userId, shopId, group.getId());
        return new JoinCheckoutResponse(order.id(), order.orderId(), order.amount(), order.currency(),
                order.keyId(), order.priceCode(), order.gateway(), group.getName(), false);
    }

    @Transactional
    public JoinState complete(UUID userId, CompleteJoinRequest request) {
        SharingGroup group = requireGroup(request == null ? null : request.joinCode());
        UUID shopId = ownedShop(userId);
        if (members.findByGroupIdAndWorkspaceId(group.getId(), shopId).isPresent()) {
            return new JoinState(GroupJoinRequest.ADMITTED, null, group.getId(), group.getName());
        }
        var pending = requests.findFirstByGroupIdAndWorkspaceIdAndStatus(
                group.getId(), shopId, GroupJoinRequest.PENDING);
        if (pending.isPresent()) {
            GroupJoinRequest row = pending.get();
            return new JoinState(GroupJoinRequest.PENDING, row.getId(), group.getId(), group.getName());
        }
        if (!billing.hasUnspentGroupJoin(userId, shopId, group.getId())) {
            if (request.razorpayOrderId() != null && !request.razorpayOrderId().isBlank()) {
                billing.verifyJoinPayment(userId, new BillingService.VerifyPaymentRequest(
                        request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature()));
            } else if (request.orderId() != null) {
                billing.confirmJoinPayment(userId, request.orderId());
            } else {
                throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                        "Pay ₹50 to ask to join this group.");
            }
        }
        if (!billing.consumeGroupJoin(userId, shopId, group.getId())) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED, "Pay ₹50 to ask to join this group.");
        }
        GroupJoinRequest row = new GroupJoinRequest();
        row.setGroupId(group.getId());
        row.setWorkspaceId(shopId);
        row.setUserId(userId);
        row.setStatus(GroupJoinRequest.PENDING);
        requests.save(row);
        return new JoinState(GroupJoinRequest.PENDING, row.getId(), group.getId(), group.getName());
    }

    @Transactional
    public void cancel(UUID userId) {
        UUID shopId = ownedShop(userId);
        GroupJoinRequest row = requests.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(
                        shopId, GroupJoinRequest.PENDING)
                .orElseThrow(() -> ApiException.notFound("Join request", shopId));
        row.setStatus(GroupJoinRequest.CANCELLED);
        requests.save(row);
        billing.releaseGroupJoin(userId, shopId, row.getGroupId());
    }

    @Transactional(readOnly = true)
    public List<JoinRequestCard> pending(UUID groupId) {
        groupService.requireManager(groupId);
        return requests.findByGroupIdAndStatusOrderByCreatedAtAsc(groupId, GroupJoinRequest.PENDING).stream()
                .map(row -> {
                    String shopName = shops.findById(row.getWorkspaceId()).map(Shop::getName).orElse("Shop");
                    String askedBy = users.findById(row.getUserId()).map(User::getFullName).orElse("Owner");
                    return new JoinRequestCard(row.getId(), row.getWorkspaceId(), shopName, askedBy);
                })
                .toList();
    }

    @Transactional
    public void approve(UUID groupId, UUID requestId) {
        groupService.requireManager(groupId);
        GroupJoinRequest row = openRequest(groupId, requestId);
        groupService.addShop(groupId, row.getWorkspaceId(), null);
        billing.grantCatalog(row.getWorkspaceId());
        row.setStatus(GroupJoinRequest.ADMITTED);
        requests.save(row);
    }

    @Transactional
    public void reject(UUID groupId, UUID requestId) {
        groupService.requireManager(groupId);
        GroupJoinRequest row = openRequest(groupId, requestId);
        row.setStatus(GroupJoinRequest.REJECTED);
        requests.save(row);
    }

    private GroupJoinRequest openRequest(UUID groupId, UUID requestId) {
        GroupJoinRequest row = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Join request", requestId));
        if (!row.getGroupId().equals(groupId) || !GroupJoinRequest.PENDING.equals(row.getStatus())) {
            throw ApiException.notFound("Join request", requestId);
        }
        return row;
    }

    private SharingGroup requireGroup(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter the union code.");
        }
        return groups.findByJoinCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "That code is not for Bihar mobile union."));
    }

    private UUID ownedShop(UUID userId) {
        UUID shopId = CurrentUser.shopId();
        if (shopId == null) {
            throw new ApiException(ErrorCode.WORKSPACE_REQUIRED, "Create your shop before joining a group.");
        }
        var membership = workspaceAccess.requireActive(userId, shopId);
        if (membership.getRole() == null
                || !SystemRole.OWNER.name().equals(membership.getRole().getCode())) {
            throw ApiException.businessRule("Only the shop owner can ask to join a fitment group.");
        }
        return shopId;
    }
}
