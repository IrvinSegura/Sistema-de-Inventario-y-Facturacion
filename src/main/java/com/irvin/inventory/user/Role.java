package com.irvin.inventory.user;

public enum Role {
    // Everything, including user accounts.
    ADMIN,
    // Runs the store like an admin (catalog, stock, purchases, every seller's sales, cancellations and
    // reports) but can't manage user accounts.
    SUPERVISOR,
    // Sells and sees only their own sales and drawer.
    SELLER
}
