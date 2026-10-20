package com.irvin.inventory.purchasing;

import java.util.Map;

import com.irvin.inventory.common.Sorting;
import com.irvin.inventory.common.exception.ConflictException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.purchasing.dto.CreateSupplierRequest;
import com.irvin.inventory.purchasing.dto.SupplierResponse;
import com.irvin.inventory.purchasing.dto.UpdateSupplierRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private static final Sort DEFAULT_SORT = Sort.by("name", "id");
    // Orders a client may ask for (API name -> entity path); the default one breaks ties.
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "taxId", "taxId",
            "email", "email",
            "phone", "phone",
            "createdAt", "createdAt");

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(SupplierSearch search, Pageable pageable) {
        Pageable sorted = Sorting.apply(pageable, SORTABLE, DEFAULT_SORT);
        return supplierRepository.findAll(search.toSpecification(), sorted).map(SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id) {
        return SupplierResponse.from(supplierRepository.findById(id).orElseThrow(SupplierService::notFound));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        if (request.taxId() != null && supplierRepository.existsByTaxId(request.taxId())) {
            throw duplicateTaxId();
        }
        Supplier supplier = new Supplier();
        supplier.setName(request.name());
        supplier.setTaxId(request.taxId());
        supplier.setEmail(request.email());
        supplier.setPhone(request.phone());
        supplier.setAddress(request.address());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    // Deactivation is always allowed: past purchases keep their supplier, new ones can't use it.
    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(SupplierService::notFound);
        if (!supplier.getVersion().equals(request.version())) {
            throw ConflictException.staleVersion();
        }
        if (request.taxId() != null && supplierRepository.existsByTaxIdAndIdNot(request.taxId(), id)) {
            throw duplicateTaxId();
        }
        supplier.setName(request.name());
        supplier.setTaxId(request.taxId());
        supplier.setEmail(request.email());
        supplier.setPhone(request.phone());
        supplier.setAddress(request.address());
        supplier.setActive(request.active());
        // Flush so the response carries the incremented version.
        return SupplierResponse.from(supplierRepository.saveAndFlush(supplier));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Supplier not found");
    }

    private static ConflictException duplicateTaxId() {
        return new ConflictException("Tax ID already exists");
    }
}
