const qrToken = document.body.dataset.qrToken;
const cartKey = `tabletap.cart.${qrToken}`;
const ordersKey = `tabletap.orders.${qrToken}`;

const menuRoot = document.getElementById("menu");
const notice = document.getElementById("notice");
const cartBar = document.getElementById("cart-bar");
const openCartButton = document.getElementById("open-cart");
const itemDialog = document.getElementById("item-dialog");
const itemForm = document.getElementById("item-form");
const cartDialog = document.getElementById("cart-dialog");
const cartLines = document.getElementById("cart-lines");
const cartTotal = document.getElementById("cart-total");
const cartError = document.getElementById("cart-error");
const orderNote = document.getElementById("order-note");
const placeOrderButton = document.getElementById("place-order");

let menu = null;
let itemsById = new Map();
// Each line: { menuItemId, optionIds, quantity, note }. Prices are looked up
// from the current menu, never stored, so the cart always shows today's price.
let cart = storage.read(cartKey, []);

const money = (amount) => formatMoney(amount, menu.currency);

async function loadMenu() {
    menu = await api("GET", `/api/public/${qrToken}/menu`);
    itemsById = new Map(menu.categories.flatMap((c) => c.items).map((item) => [item.id, item]));
}

// Drops lines the menu no longer offers, for example an item that sold out
// while the customer was browsing.
function pruneCart() {
    const before = cart.length;
    cart = cart.filter((line) => {
        const item = itemsById.get(line.menuItemId);
        return item && line.optionIds.every((id) => findOption(item, id)) && satisfiesGroups(item, line.optionIds);
    });
    saveCart();
    if (cart.length < before) {
        showNotice("Some items in your cart are no longer available, so they were removed.");
    }
}

function findOption(item, optionId) {
    for (const group of item.optionGroups) {
        const option = group.options.find((o) => o.id === optionId);
        if (option) {
            return option;
        }
    }
    return null;
}

function satisfiesGroups(item, optionIds) {
    return item.optionGroups.every((group) => {
        const count = group.options.filter((o) => optionIds.includes(o.id)).length;
        return count >= group.minSelect && count <= group.maxSelect;
    });
}

function unitPrice(item, optionIds) {
    return optionIds.reduce((sum, id) => sum + findOption(item, id).priceDelta, item.basePrice);
}

function lineTotal(line) {
    return unitPrice(itemsById.get(line.menuItemId), line.optionIds) * line.quantity;
}

function saveCart() {
    storage.write(cartKey, cart);
}

function showNotice(text) {
    notice.textContent = text;
    notice.hidden = false;
}

function renderMenu() {
    if (menu.categories.length === 0) {
        menuRoot.replaceChildren(el("p", { class: "empty" }, "Nothing is available to order right now."));
        return;
    }
    menuRoot.replaceChildren(...menu.categories.map((category) =>
        el("section", { class: "menu-section" },
            el("h2", {}, category.name),
            category.items.map(renderItem))));
}

function renderItem(item) {
    return el("button", { class: "dish", type: "button", onclick: () => openItem(item) },
        item.imageUrl ? el("img", { class: "dish-image", src: item.imageUrl, alt: "", loading: "lazy" }) : null,
        el("span", { class: "dish-body" },
            el("span", { class: "dish-name" }, item.name),
            item.description ? el("span", { class: "dish-description" }, item.description) : null,
            el("span", { class: "dish-price" }, money(item.basePrice))),
        el("span", { class: "dish-add", "aria-hidden": "true" }, "+"));
}

function groupHint(group) {
    if (group.minSelect === 1 && group.maxSelect === 1) {
        return "Choose 1";
    }
    if (group.minSelect === 0) {
        return group.maxSelect === 1 ? "Optional" : `Optional, up to ${group.maxSelect}`;
    }
    return group.minSelect === group.maxSelect
        ? `Choose ${group.minSelect}`
        : `Choose ${group.minSelect} to ${group.maxSelect}`;
}

function openItem(item) {
    let quantity = 1;
    const groupInputs = [];

    const submit = el("button", { class: "button primary", type: "submit" });
    const quantityLabel = el("span", { class: "quantity-value", "aria-live": "polite" });

    const chosenIds = () => groupInputs.flatMap(({ inputs }) => inputs.filter((i) => i.checked).map((i) => i.value));

    function refresh() {
        for (const { group, inputs } of groupInputs) {
            if (group.maxSelect > 1) {
                const full = inputs.filter((i) => i.checked).length >= group.maxSelect;
                inputs.forEach((input) => { input.disabled = full && !input.checked; });
            }
        }
        const ids = chosenIds();
        const ready = satisfiesGroups(item, ids);
        quantityLabel.textContent = String(quantity);
        submit.disabled = !ready;
        submit.textContent = ready
            ? `Add ${quantity} to order · ${money(unitPrice(item, ids) * quantity)}`
            : "Make your choices above";
    }

    const groups = item.optionGroups.map((group) => {
        const single = group.maxSelect === 1;
        const inputs = group.options.map((option, index) => el("input", {
            type: single && group.minSelect === 1 ? "radio" : "checkbox",
            name: `group-${group.id}`,
            value: option.id,
            checked: single && group.minSelect === 1 && index === 0,
            onchange: (event) => {
                // An optional single choice uses checkboxes so it can be cleared,
                // so ticking one has to untick the others.
                if (single && event.target.checked) {
                    inputs.forEach((other) => { other.checked = other === event.target; });
                }
                refresh();
            },
        }));
        groupInputs.push({ group, inputs });
        return el("fieldset", { class: "choice-group" },
            el("legend", {}, group.name, el("span", { class: "muted" }, ` · ${groupHint(group)}`)),
            group.options.map((option, index) => el("label", { class: "choice" },
                inputs[index],
                el("span", {}, option.name),
                option.priceDelta > 0 ? el("span", { class: "muted" }, `+${money(option.priceDelta)}`) : null)));
    });

    const note = el("input", { type: "text", maxlength: "200", placeholder: "No onions, less spicy…", id: "item-note" });

    itemForm.replaceChildren(
        el("h2", { id: "item-title" }, item.name),
        item.description ? el("p", { class: "muted" }, item.description) : "",
        ...groups,
        el("div", { class: "field" }, el("label", { for: "item-note" }, "Note"), note),
        el("div", { class: "quantity" },
            el("button", { class: "button", type: "button", "aria-label": "One fewer",
                onclick: () => { quantity = Math.max(1, quantity - 1); refresh(); } }, "−"),
            quantityLabel,
            el("button", { class: "button", type: "button", "aria-label": "One more",
                onclick: () => { quantity = Math.min(99, quantity + 1); refresh(); } }, "+")),
        el("div", { class: "dialog-foot" },
            el("button", { class: "button", type: "button", onclick: () => itemDialog.close() }, "Cancel"),
            submit));

    itemForm.onsubmit = (event) => {
        event.preventDefault();
        addToCart({ menuItemId: item.id, optionIds: chosenIds(), quantity, note: note.value.trim() });
        itemDialog.close();
    };

    refresh();
    itemDialog.showModal();
}

function sameChoice(a, b) {
    return a.menuItemId === b.menuItemId && a.note === b.note
        && a.optionIds.length === b.optionIds.length && a.optionIds.every((id) => b.optionIds.includes(id));
}

function addToCart(line) {
    const existing = cart.find((other) => sameChoice(other, line));
    if (existing) {
        existing.quantity = Math.min(99, existing.quantity + line.quantity);
    } else {
        cart.push(line);
    }
    saveCart();
    renderCart();
}

function renderCart() {
    const count = cart.reduce((sum, line) => sum + line.quantity, 0);
    const total = cart.reduce((sum, line) => sum + lineTotal(line), 0);
    cartBar.hidden = count === 0;
    openCartButton.replaceChildren(
        el("span", {}, `View order · ${count} ${count === 1 ? "item" : "items"}`),
        el("strong", {}, money(total)));
    cartTotal.textContent = money(total);

    if (cart.length === 0) {
        cartLines.replaceChildren(el("p", { class: "empty" }, "Your order is empty."));
        placeOrderButton.disabled = true;
        return;
    }
    placeOrderButton.disabled = false;
    cartLines.replaceChildren(...cart.map((line, index) => {
        const item = itemsById.get(line.menuItemId);
        const options = line.optionIds.map((id) => findOption(item, id).name).join(", ");
        return el("div", { class: "cart-line" },
            el("div", { class: "cart-line-body" },
                el("span", { class: "dish-name" }, item.name),
                options ? el("span", { class: "muted small" }, options) : null,
                line.note ? el("span", { class: "muted small" }, `“${line.note}”`) : null,
                el("span", { class: "dish-price" }, money(lineTotal(line)))),
            el("div", { class: "quantity" },
                el("button", { class: "button small", type: "button", "aria-label": `One fewer ${item.name}`,
                    onclick: () => changeQuantity(index, -1) }, line.quantity === 1 ? "✕" : "−"),
                el("span", { class: "quantity-value" }, String(line.quantity)),
                el("button", { class: "button small", type: "button", "aria-label": `One more ${item.name}`,
                    onclick: () => changeQuantity(index, 1) }, "+")));
    }));
}

function changeQuantity(index, delta) {
    const line = cart[index];
    line.quantity = Math.min(99, line.quantity + delta);
    if (line.quantity < 1) {
        cart.splice(index, 1);
    }
    saveCart();
    renderCart();
}

function renderMyOrders() {
    const tokens = storage.read(ordersKey, []);
    const nav = document.getElementById("my-orders");
    nav.hidden = tokens.length === 0;
    nav.replaceChildren(
        el("span", { class: "muted" }, "Your orders:"),
        ...tokens.map((token, index) => el("a", { href: `/t/${qrToken}/orders/${token}` }, `#${index + 1}`)));
}

async function placeOrder() {
    cartError.textContent = "";
    placeOrderButton.disabled = true;
    try {
        const order = await api("POST", `/api/public/${qrToken}/orders`, {
            note: orderNote.value.trim(),
            items: cart.map(({ menuItemId, optionIds, quantity, note }) => ({ menuItemId, optionIds, quantity, note })),
        });
        cart = [];
        saveCart();
        storage.write(ordersKey, [...storage.read(ordersKey, []), order.publicToken]);
        window.location.href = `/t/${qrToken}/orders/${order.publicToken}`;
    } catch (e) {
        cartError.textContent = e.message;
        placeOrderButton.disabled = false;
        // The menu may have changed since the page loaded.
        await loadMenu().then(() => { pruneCart(); renderMenu(); renderCart(); }).catch(() => {});
    }
}

openCartButton.addEventListener("click", () => {
    cartError.textContent = "";
    cartDialog.showModal();
});
document.getElementById("close-cart").addEventListener("click", () => cartDialog.close());
document.getElementById("cart-form").addEventListener("submit", (event) => {
    event.preventDefault();
    placeOrder();
});

renderMyOrders();
loadMenu()
    .then(() => {
        pruneCart();
        renderMenu();
        renderCart();
    })
    .catch((e) => {
        menuRoot.replaceChildren(el("p", { class: "error" }, e.message));
    });
