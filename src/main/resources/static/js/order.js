const publicToken = document.body.dataset.publicToken;

const STEPS = ["PLACED", "ACCEPTED", "PREPARING", "SERVED", "PAID"];
const STEP_NAMES = { PLACED: "Sent", ACCEPTED: "Accepted", PREPARING: "Cooking", SERVED: "Served", PAID: "Paid" };
const STATUS_TEXT = {
    PLACED: "Your order was sent to the kitchen. Staff will confirm it shortly.",
    ACCEPTED: "The kitchen accepted your order.",
    PREPARING: "Your food is being prepared.",
    SERVED: "Your order has been served. Enjoy!",
    PAID: "Paid. Thank you!",
    CANCELLED: "This order was cancelled. Please ask a member of staff.",
};
const POLL_MS = 5000;

function render(order) {
    const money = (amount) => formatMoney(amount, order.currency);
    document.title = `Your order · ${order.restaurantName}`;
    document.getElementById("restaurant").textContent = order.restaurantName;
    document.getElementById("table").textContent = `Table ${order.tableLabel}`;
    document.getElementById("status-text").textContent = STATUS_TEXT[order.status];

    const reached = STEPS.indexOf(order.status);
    document.getElementById("steps").replaceChildren(...STEPS.map((step, index) => el("li", {
        class: order.status === "CANCELLED" ? "step" : index < reached ? "step done" : index === reached ? "step current" : "step",
        "aria-current": index === reached ? "step" : null,
    }, STEP_NAMES[step])));

    document.getElementById("lines").replaceChildren(...order.items.map((line) => el("div", { class: "cart-line" },
        el("div", { class: "cart-line-body" },
            el("span", { class: "dish-name" }, `${line.quantity} × ${line.name}`),
            line.options.length ? el("span", { class: "muted small" }, line.options.map((o) => o.optionName).join(", ")) : null,
            line.note ? el("span", { class: "muted small" }, `“${line.note}”`) : null),
        el("span", { class: "dish-price" }, money(line.lineTotal)))));
    document.getElementById("note").textContent = order.note ? `Note: ${order.note}` : "";
    document.getElementById("total").textContent = money(order.total);
}

async function poll() {
    try {
        const order = await api("GET", `/api/public/orders/${publicToken}`);
        render(order);
        if (order.status === "PAID" || order.status === "CANCELLED") {
            return;
        }
    } catch (e) {
        document.getElementById("status-text").textContent = e.message;
    }
    setTimeout(poll, POLL_MS);
}

poll();
