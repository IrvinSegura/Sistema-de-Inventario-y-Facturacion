package com.irvin.inventory.common.validation;

// Allowed characters for free-text identifiers, shared by request DTOs (@Pattern) and mirrored by the
// web client. They keep markup and control characters out of names shown on screens and receipts.
public final class TextRules {

    // Codes such as a SKU, checked after Normalization.code uppercases them.
    public static final String CODE_PATTERN = "^[A-Z0-9][A-Z0-9._-]*$";
    public static final String CODE_MESSAGE = "must contain only letters, digits, '.', '-' or '_'";

    // Product and category names: letters in any language, digits, spaces and common punctuation.
    public static final String LABEL_PATTERN = "^[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} .,'&()/%+#-]*$";
    public static final String LABEL_MESSAGE =
            "must start with a letter or digit and contain only letters, digits, spaces or . , ' & ( ) / % + # -";

    // People's names: letters, spaces, apostrophes, dots and hyphens.
    public static final String PERSON_NAME_PATTERN = "^\\p{L}[\\p{L}\\p{M} .'-]*$";
    public static final String PERSON_NAME_MESSAGE = "must contain only letters, spaces, '.', ''' or '-'";

    private TextRules() {
    }
}
