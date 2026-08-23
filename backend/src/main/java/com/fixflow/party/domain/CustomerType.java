package com.fixflow.party.domain;

public enum CustomerType {

    /** Walk-in customer paying the counter price. */
    RETAIL,
    /** Trade buyer on the wholesale sheet. */
    WHOLESALE,
    /** Regular with negotiated rates. */
    VIP,
    /** Another repair technician buying parts. */
    TECHNICIAN,
    /** Parts consumed by the shop's own repair jobs. */
    INTERNAL
}
