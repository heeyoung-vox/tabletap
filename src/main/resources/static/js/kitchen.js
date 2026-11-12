const OPEN = ["PLACED", "ACCEPTED", "PREPARING", "SERVED"];
const ACTIONS = {
    ACCEPTED: "Accept",
    PREPARING: "Start cooking",
    SERVED: "Mark served",
    PAID: "Mark paid",
    CANCELLED: "Cancel",
};

const live = document.getElementById("live");
const pageError = document.getElementById("page-error");
const orders = new Map();
const fresh = new Set();

async function reload() {
    const query = OPEN.map((status) => `status=${status}`).join("&");
    const list = await api("GET", `/api/admin/orders?${query}`);
    orders.clear();
    list.forEach((order) => orders.set(order.id, order));
    render();
}

function upsert(order) {
    if (!orders.has(order.id) && order.status === "PLACED") {
        fresh.add(order.id);
        setTimeout(() => { fresh.delete(order.id); render(); }, 8000);
    }
    if (OPEN.includes(order.status)) {
        orders.set(order.id, order);
    } else {
        orders.delete(order.id);
    }
    render();
}

function minutesAgo(instant) {
    const minutes = Math.floor((Date.now() - new Date(instant).getTime()) / 60000);
    return minutes < 1 ? "just now" : `${minutes} min ago`;
}

function render() {
    for (const lane of document.querySelectorAll(".lane")) {
        const inLane = [...orders.values()]
            .filter((order) => order.status === lane.dataset.status)
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
        lane.querySelector(".count").textContent = inLane.length ? `(${inLane.length})` : "";
        lane.querySelector(".cards").replaceChildren(...(inLane.length
            ? inLane.map(renderCard)
            : [el("p", { class: "empty" }, "Nothing here")]));
    }
    const waiting = [...orders.values()].filter((order) => order.status === "PLACED").length;
    document.title = waiting ? `(${waiting}) Kitchen · TableTap` : "Kitchen · TableTap";
}

function renderCard(order) {
    const money = (amount) => formatMoney(amount, order.currency);
    return el("article", { class: fresh.has(order.id) ? "ticket fresh" : "ticket" },
        el("header", { class: "ticket-head" },
            el("strong", {}, `Table ${order.tableLabel}`),
            el("time", { class: "muted small", datetime: order.createdAt, title: new Date(order.createdAt).toLocaleString() },
                minutesAgo(order.createdAt))),
        el("ul", { class: "ticket-lines" }, order.items.map((line) => el("li", {},
            el("span", { class: "qty" }, `${line.quantity}×`),
            el("span", {},
                el("span", { class: "dish-name" }, line.name),
                line.options.length ? el("span", { class: "muted small block" }, line.options.map((o) => o.optionName).join(", ")) : null,
                line.note ? el("span", { class: "ticket-note block" }, line.note) : null)))),
        order.note ? el("p", { class: "ticket-note" }, order.note) : null,
        el("footer", { class: "ticket-foot" },
            el("span", { class: "dish-price" }, money(order.total)),
            el("span", { class: "actions" }, order.nextStatuses
                .slice()
                .sort((a, b) => (a === "CANCELLED") - (b === "CANCELLED"))
                .map((status) => el("button", {
                    class: status === "CANCELLED" ? "button small danger" : "button small primary",
                    type: "button",
                    onclick: () => move(order, status),
                }, ACTIONS[status])))));
}

async function move(order, status) {
    if (status === "CANCELLED" && !confirm(`Cancel the order for table ${order.tableLabel}?`)) {
        return;
    }
    pageError.textContent = "";
    try {
        upsert(await api("POST", `/api/admin/orders/${order.id}/transition`, { to: status }));
    } catch (e) {
        pageError.textContent = e.message;
        await reload().catch(() => {});
    }
}

// EventSource reconnects by itself. Events sent while it was away are lost,
// so every (re)connection reloads the full list.
function connect() {
    const source = new EventSource("/api/admin/orders/stream");
    source.addEventListener("open", () => {
        live.textContent = "Live";
        live.classList.add("on");
        reload().catch((e) => { pageError.textContent = e.message; });
    });
    source.addEventListener("order", (event) => upsert(JSON.parse(event.data)));
    source.addEventListener("error", () => {
        live.textContent = "Reconnecting…";
        live.classList.remove("on");
        // A 401 here means the login expired. api() sends the user to log in.
        api("GET", "/api/admin/me").catch(() => {});
    });
}

setInterval(render, 60000);
connect();
