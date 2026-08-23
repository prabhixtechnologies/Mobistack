package com.fixflow.party.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.util.TextNormalizer;
import com.fixflow.party.domain.Customer;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.party.domain.Supplier;
import com.fixflow.party.dto.PartyDtos.CustomerRequest;
import com.fixflow.party.dto.PartyDtos.CustomerResponse;
import com.fixflow.party.dto.PartyDtos.SupplierRequest;
import com.fixflow.party.dto.PartyDtos.SupplierResponse;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.party.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> searchCustomers(UUID shopId, String query, Pageable pageable) {
        String raw = query == null ? "" : query.trim();
        String normalized = TextNormalizer.normalizeOrNull(raw);
        return customerRepository.search(shopId, normalized == null ? "" : normalized, raw, pageable)
                .map(this::toCustomer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID shopId, UUID id) {
        return toCustomer(requireCustomer(shopId, id));
    }

    @Transactional
    public CustomerResponse createCustomer(UUID shopId, CustomerRequest request) {
        if (request.phone() != null && !request.phone().isBlank()) {
            customerRepository.findByShopIdAndPhone(shopId, request.phone())
                    .ifPresent(existing -> {
                        throw ApiException.alreadyExists("A customer with this phone already exists.");
                    });
        }
        Customer customer = new Customer();
        customer.setShopId(shopId);
        applyCustomer(customer, request);
        customerRepository.save(customer);
        auditService.record(AuditAction.CUSTOMER_CREATED, "Customer", customer.getId(),
                "Added customer \"%s\"".formatted(customer.getName()));
        return toCustomer(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID shopId, UUID id, CustomerRequest request) {
        Customer customer = requireCustomer(shopId, id);
        applyCustomer(customer, request);
        customerRepository.save(customer);
        auditService.record(AuditAction.CUSTOMER_UPDATED, "Customer", id,
                "Updated customer \"%s\"".formatted(customer.getName()));
        return toCustomer(customer);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> searchSuppliers(UUID shopId, String query, Pageable pageable) {
        String raw = query == null ? "" : query.trim();
        String normalized = TextNormalizer.normalizeOrNull(raw);
        return supplierRepository.search(shopId, normalized == null ? "" : normalized, raw, pageable)
                .map(this::toSupplier);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(UUID shopId, UUID id) {
        return toSupplier(requireSupplier(shopId, id));
    }

    @Transactional
    public SupplierResponse createSupplier(UUID shopId, SupplierRequest request) {
        supplierRepository.findByShopIdAndName(shopId, request.name())
                .ifPresent(existing -> {
                    throw ApiException.alreadyExists("A supplier with this name already exists.");
                });
        Supplier supplier = new Supplier();
        supplier.setShopId(shopId);
        applySupplier(supplier, request);
        supplierRepository.save(supplier);
        auditService.record(AuditAction.SUPPLIER_CREATED, "Supplier", supplier.getId(),
                "Added supplier \"%s\"".formatted(supplier.getName()));
        return toSupplier(supplier);
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID shopId, UUID id, SupplierRequest request) {
        Supplier supplier = requireSupplier(shopId, id);
        applySupplier(supplier, request);
        supplierRepository.save(supplier);
        auditService.record(AuditAction.SUPPLIER_UPDATED, "Supplier", id,
                "Updated supplier \"%s\"".formatted(supplier.getName()));
        return toSupplier(supplier);
    }

    public Customer requireCustomer(UUID shopId, UUID id) {
        return customerRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    public Supplier requireSupplier(UUID shopId, UUID id) {
        return supplierRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Supplier", id));
    }

    private void applyCustomer(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setPhone(blankToNull(request.phone()));
        customer.setEmail(blankToNull(request.email()));
        customer.setAddressLine1(request.addressLine1());
        customer.setCity(request.city());
        customer.setCustomerType(request.customerType() == null ? CustomerType.RETAIL : request.customerType());
        customer.setGstNumber(request.gstNumber());
        if (request.creditLimit() != null) {
            customer.setCreditLimit(request.creditLimit());
        }
        customer.setNotes(request.notes());
    }

    private void applySupplier(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setContactPerson(request.contactPerson());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddressLine1(request.addressLine1());
        supplier.setCity(request.city());
        supplier.setGstNumber(request.gstNumber());
        if (request.paymentTermsDays() != null) {
            supplier.setPaymentTermsDays(request.paymentTermsDays());
        }
        supplier.setNotes(request.notes());
    }

    public CustomerResponse toCustomer(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getAddressLine1(), customer.getCity(),
                customer.getCustomerType(), customer.getGstNumber(), customer.getCreditLimit(),
                customer.getOutstandingAmount(), customer.getTotalPurchases(),
                customer.getLastTransactionAt(), customer.getNotes(), customer.isActive());
    }

    public SupplierResponse toSupplier(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContactPerson(),
                supplier.getPhone(), supplier.getEmail(), supplier.getAddressLine1(), supplier.getCity(),
                supplier.getGstNumber(), supplier.getPaymentTermsDays(), supplier.getOutstandingAmount(),
                supplier.getNotes(), supplier.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
