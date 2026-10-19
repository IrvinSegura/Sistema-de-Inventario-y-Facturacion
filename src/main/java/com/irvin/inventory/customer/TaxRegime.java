package com.irvin.inventory.customer;

import java.util.Arrays;
import java.util.Optional;

// SAT catalog c_RegimenFiscal (CFDI 4.0): code and whether it applies to individuals and/or companies.
public enum TaxRegime {

    GENERAL_COMPANIES("601", false, true),
    NON_PROFIT_COMPANIES("603", false, true),
    WAGES("605", true, false),
    LEASING("606", true, false),
    ASSET_SALES("607", true, false),
    OTHER_INCOME("608", true, false),
    FOREIGN_RESIDENTS("610", true, true),
    DIVIDENDS("611", true, false),
    BUSINESS_AND_PROFESSIONAL_ACTIVITIES("612", true, false),
    INTEREST("614", true, false),
    PRIZES("615", true, false),
    NO_TAX_OBLIGATIONS("616", true, false),
    PRODUCTION_COOPERATIVES("620", false, true),
    TAX_INCORPORATION("621", true, false),
    PRIMARY_SECTOR("622", false, true),
    COMPANY_GROUPS("623", false, true),
    COORDINATED("624", false, true),
    DIGITAL_PLATFORMS("625", true, false),
    SIMPLIFIED_TRUST("626", true, true);

    // RFC length: 12 characters for a company, 13 for an individual.
    private static final int COMPANY_TAX_ID_LENGTH = 12;

    private final String code;
    private final boolean individuals;
    private final boolean companies;

    TaxRegime(String code, boolean individuals, boolean companies) {
        this.code = code;
        this.individuals = individuals;
        this.companies = companies;
    }

    public String code() {
        return code;
    }

    public static Optional<TaxRegime> fromCode(String code) {
        return Arrays.stream(values()).filter(regime -> regime.code.equals(code)).findFirst();
    }

    public static boolean isCompanyTaxId(String taxId) {
        return taxId.length() == COMPANY_TAX_ID_LENGTH;
    }

    public boolean appliesTo(String taxId) {
        return isCompanyTaxId(taxId) ? companies : individuals;
    }
}
