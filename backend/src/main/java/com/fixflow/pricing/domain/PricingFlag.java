package com.fixflow.pricing.domain;

/**
 * The context a price is being quoted in. Chosen by the cashier at the counter
 * or implied by the customer, then resolved to an actual amount by the pricing
 * engine — never by hard-coded branching at the call site.
 */
public enum PricingFlag {

    NORMAL,
    WHOLESALE,
    REPAIR,
    VIP,
    CLEARANCE,
    OLD_STOCK,
    CUSTOM
}
