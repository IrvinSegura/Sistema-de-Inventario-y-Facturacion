package com.irvin.inventory.customer;

import java.util.Map;
import java.util.Objects;

import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.ForbiddenException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.customer.dto.CreateCustomerRequest;
import com.irvin.inventory.customer.dto.CustomerResponse;
import com.irvin.inventory.customer.dto.UpdateCustomerRequest;
import com.irvin.inventory.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Sort DEFAULT_SORT = Sort.by("name", "id");
    static final String IN_USE_MESSAGE = "Customer has sales; deactivate it instead";
    static final String INCOMPLETE_FISCAL_DATA_MESSAGE =
            "Tax ID, fiscal postal code and tax regime must be given together";
    static final String UNKNOWN_TAX_REGIME_MESSAGE = "Unknown tax regime";
    static final String COMPANY_TAX_REGIME_MESSAGE = "The tax regime doesn't apply to a company (12-character tax ID)";
    static final String INDIVIDUAL_TAX_REGIME_MESSAGE =
            "The tax regime doesn't apply to an individual (13-character tax ID)";

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(CustomerSearch search, Pageable pageable) {
        Pageable sorted = Sorting.apply(pageable, SORTABLE, DEFAULT_SORT);
        return customerRepository.findAll(search.toSpecification(), sorted).map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(customerRepository.findById(id).orElseThrow(CustomerService::notFound));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        requireFiscalData(request.taxId(), request.fiscalPostalCode(), request.taxRegime());
        if (request.taxId() != null && customerRepository.existsByTaxId(request.taxId())) {
            throw duplicateTaxId();
        }
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setTaxId(request.taxId());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setFiscalPostalCode(request.fiscalPostalCode());
        customer.setTaxRegime(request.taxRegime());
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
        requireFiscalData(request.taxId(), request.fiscalPostalCode(), request.taxRegime());
        if (!customer.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        // Past sales show the customer's current data, and deactivation blocks other checkouts.
        boolean sensitiveChange = !Objects.equals(customer.getTaxId(), request.taxId())
                || customer.isActive() != request.active();
        if (sensitiveChange && !editor.isManager()) {
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
        customer.setFiscalPostalCode(request.fiscalPostalCode());
        customer.setTaxRegime(request.taxRegime());
        customer.setActive(request.active());
        // Flush so the response carries the incremented version.
        return CustomerResponse.from(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public void delete(Long id) {
        // Locked first so a checkout can't record a sale for it between the check and the delete.
        Customer customer = customerRepository.findByIdForUpdate(id).orElseThrow(CustomerService::notFound);
        if (customer.isWalkIn()) {
            throw new ConflictException("The walk-in customer can't be deleted");
        }
        if (customerRepository.hasSales(id)) {
            throw new ConflictException(IN_USE_MESSAGE);
        }
        customerRepository.delete(customer);
        customerRepository.flush();
    }

    // An invoice (CFDI 4.0) needs all three, so a customer has the complete set or none of it.
    private static void requireFiscalData(String taxId, String fiscalPostalCode, String taxRegime) {
        if (taxId == null && fiscalPostalCode == null && taxRegime == null) {
            return;
        }
        if (taxId == null || fiscalPostalCode == null || taxRegime == null) {
            throw new BadRequestException(INCOMPLETE_FISCAL_DATA_MESSAGE);
        }
        TaxRegime regime = TaxRegime.fromCode(taxRegime)
                .orElseThrow(() -> new BadRequestException(UNKNOWN_TAX_REGIME_MESSAGE));
        if (!regime.appliesTo(taxId)) {
            throw new BadRequestException(TaxRegime.isCompanyTaxId(taxId)
                    ? COMPANY_TAX_REGIME_MESSAGE : INDIVIDUAL_TAX_REGIME_MESSAGE);
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Customer not found");
    }

    private static ConflictException duplicateTaxId() {
        return new ConflictException("Tax ID already exists");
    }
}
