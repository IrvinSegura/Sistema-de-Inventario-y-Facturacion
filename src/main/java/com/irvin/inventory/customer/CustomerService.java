package com.irvin.inventory.customer;

import java.util.Objects;

import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.ForbiddenException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.customer.dto.CreateCustomerRequest;
import com.irvin.inventory.customer.dto.CustomerResponse;
import com.irvin.inventory.customer.dto.UpdateCustomerRequest;
import com.irvin.inventory.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Sort DEFAULT_SORT = Sort.by("name", "id");

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(CustomerSearch search, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return customerRepository.findAll(search.toSpecification(), sorted).map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(customerRepository.findById(id).orElseThrow(CustomerService::notFound));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        if (request.taxId() != null && customerRepository.existsByTaxId(request.taxId())) {
            throw duplicateTaxId();
        }
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setTaxId(request.taxId());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    // Deactivation is allowed: past sales keep their customer, new ones can't use it.
    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request, CurrentUser editor) {
        Customer customer = customerRepository.findById(id).orElseThrow(CustomerService::notFound);
        // Sales without a customer fall back to it, so it must stay as seeded and active.
        if (customer.isWalkIn()) {
            throw new ConflictException("The walk-in customer can't be modified");
        }
        if (!customer.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        // Past sales show the customer's current data, and deactivation blocks other checkouts.
        boolean sensitiveChange = !Objects.equals(customer.getTaxId(), request.taxId())
                || customer.isActive() != request.active();
        if (sensitiveChange && !editor.isAdmin()) {
            throw new ForbiddenException("Only an admin can change a customer's tax ID or active status");
        }
        if (request.taxId() != null && customerRepository.existsByTaxIdAndIdNot(request.taxId(), id)) {
            throw duplicateTaxId();
        }
        customer.setName(request.name());
        customer.setTaxId(request.taxId());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setActive(request.active());
        // Flush so the response carries the incremented version.
        return CustomerResponse.from(customerRepository.saveAndFlush(customer));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Customer not found");
    }

    private static ConflictException duplicateTaxId() {
        return new ConflictException("Tax ID already exists");
    }
}
