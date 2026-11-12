// Amounts are stored in the currency's smallest unit (đồng for VND, cents for USD).
function formatMoney(amount, currency) {
    const format = new Intl.NumberFormat(undefined, { style: "currency", currency });
    const digits = format.resolvedOptions().maximumFractionDigits;
    return format.format(amount / 10 ** digits);
}

// localStorage can be missing or full (private mode, blocked storage). The
// pages still work without it, they just forget the cart on reload.
const storage = {
    read(key, fallback) {
        try {
            const value = localStorage.getItem(key);
            return value === null ? fallback : JSON.parse(value);
        } catch {
            return fallback;
        }
    },
    write(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch {
            // Nothing to do. The value lives on in memory for this visit.
        }
    },
};
