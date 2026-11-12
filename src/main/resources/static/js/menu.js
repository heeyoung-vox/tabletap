const menuRoot = document.getElementById("menu");
const pageError = document.getElementById("page-error");
const dialog = document.getElementById("item-dialog");
const itemForm = document.getElementById("item-form");
const itemError = document.getElementById("item-error");
const groupsRoot = document.getElementById("groups");

let categories = [];
let items = [];
let editingItemId = null;

const formatPrice = (amount) => amount.toLocaleString();

async function load() {
    [categories, items] = await Promise.all([
        api("GET", "/api/admin/categories"),
        api("GET", "/api/admin/items"),
    ]);
    render();
}

// Runs an action, then reloads so the page always shows what the server has.
async function run(action) {
    pageError.textContent = "";
    try {
        await action();
        await load();
    } catch (e) {
        pageError.textContent = e.message;
    }
}

function render() {
    if (categories.length === 0) {
        menuRoot.replaceChildren(el("p", { class: "empty" }, "No categories yet. Add one to start your menu."));
        return;
    }
    menuRoot.replaceChildren(...categories.map(renderCategory));
}

function renderCategory(category) {
    const categoryItems = items.filter((item) => item.categoryId === category.id);
    return el("section", { class: "category" },
        el("div", { class: "category-head" },
            el("h2", {}, category.name),
            el("div", { class: "actions" },
                el("button", { class: "button small primary", type: "button", onclick: () => openItemDialog(null, category.id) }, "Add item"),
                el("button", { class: "button small", type: "button", onclick: () => renameCategory(category) }, "Rename"),
                el("button", { class: "button small danger", type: "button", onclick: () => deleteCategory(category) }, "Delete"))),
        categoryItems.length === 0
            ? el("p", { class: "empty" }, "No items in this category.")
            : categoryItems.map(renderItem));
}

function renderItem(item) {
    const optionSummary = item.optionGroups
        .map((group) => `${group.name}: ${group.options.map((option) => option.name).join(", ")}`)
        .join(" · ");
    return el("div", { class: item.available ? "item" : "item sold-out" },
        el("div", {},
            el("div", {},
                el("span", { class: "item-name" }, item.name),
                " ",
                el("span", { class: "item-price" }, formatPrice(item.basePrice)),
                item.available ? null : el("span", { class: "muted" }, " (sold out)")),
            item.description ? el("div", { class: "muted" }, item.description) : null,
            optionSummary ? el("div", { class: "item-options" }, optionSummary) : null),
        el("div", { class: "actions" },
            el("button", { class: "button small", type: "button", onclick: () => toggleAvailable(item) },
                item.available ? "Mark sold out" : "Mark available"),
            el("button", { class: "button small", type: "button", onclick: () => openItemDialog(item, item.categoryId) }, "Edit"),
            el("button", { class: "button small danger", type: "button", onclick: () => deleteItem(item) }, "Delete")));
}

function renameCategory(category) {
    const name = window.prompt("Category name", category.name);
    if (name === null || name.trim() === "") {
        return;
    }
    run(() => api("PUT", `/api/admin/categories/${category.id}`, { name, sortOrder: category.sortOrder }));
}

function deleteCategory(category) {
    if (window.confirm(`Delete the category "${category.name}"?`)) {
        run(() => api("DELETE", `/api/admin/categories/${category.id}`));
    }
}

function toggleAvailable(item) {
    run(() => api("PUT", `/api/admin/items/${item.id}/availability`, { available: !item.available }));
}

function deleteItem(item) {
    if (window.confirm(`Delete "${item.name}"?`)) {
        run(() => api("DELETE", `/api/admin/items/${item.id}`));
    }
}

function openItemDialog(item, categoryId) {
    editingItemId = item ? item.id : null;
    itemError.textContent = "";
    document.getElementById("item-dialog-title").textContent = item ? "Edit item" : "New item";

    const select = document.getElementById("item-category");
    select.replaceChildren(...categories.map((category) =>
        el("option", { value: category.id, selected: category.id === categoryId }, category.name)));

    itemForm.elements.name.value = item ? item.name : "";
    itemForm.elements.description.value = item && item.description ? item.description : "";
    itemForm.elements.basePrice.value = item ? item.basePrice : "";
    itemForm.elements.available.checked = item ? item.available : true;
    groupsRoot.replaceChildren(...(item ? item.optionGroups.map(groupEditor) : []));

    dialog.showModal();
}

function groupEditor(group) {
    const options = el("div", { class: "options" }, (group ? group.options : [null]).map(optionEditor));
    const node = el("div", { class: "group" },
        el("div", { class: "group-head" },
            el("div", {},
                el("label", {}, "Group name"),
                el("input", { type: "text", "data-field": "name", maxlength: "120", placeholder: "Size", value: group ? group.name : "", required: true })),
            el("div", {},
                el("label", {}, "Min"),
                el("input", { type: "number", "data-field": "minSelect", min: "0", step: "1", value: group ? group.minSelect : 0, required: true })),
            el("div", {},
                el("label", {}, "Max"),
                el("input", { type: "number", "data-field": "maxSelect", min: "1", step: "1", value: group ? group.maxSelect : 1, required: true })),
            el("button", { class: "button small danger", type: "button", onclick: () => node.remove() }, "Remove group")),
        options,
        el("button", { class: "button small", type: "button", onclick: () => options.append(optionEditor(null)) }, "Add option"));
    return node;
}

function optionEditor(option) {
    const node = el("div", { class: "option-row" },
        el("input", { type: "text", "data-field": "name", maxlength: "120", placeholder: "Option name", "aria-label": "Option name", value: option ? option.name : "", required: true }),
        el("input", { type: "number", "data-field": "priceDelta", min: "0", step: "1", placeholder: "Extra price", "aria-label": "Extra price", value: option ? option.priceDelta : 0, required: true }),
        el("button", { class: "button small danger", type: "button", onclick: () => node.remove() }, "Remove"));
    return node;
}

function readItemForm() {
    const field = (root, name) => root.querySelector(`[data-field="${name}"]`).value;
    return {
        categoryId: itemForm.elements.categoryId.value,
        name: itemForm.elements.name.value,
        description: itemForm.elements.description.value || null,
        basePrice: Number(itemForm.elements.basePrice.value),
        available: itemForm.elements.available.checked,
        sortOrder: 0,
        optionGroups: [...groupsRoot.querySelectorAll(".group")].map((group) => ({
            name: field(group, "name"),
            minSelect: Number(field(group, "minSelect")),
            maxSelect: Number(field(group, "maxSelect")),
            options: [...group.querySelectorAll(".option-row")].map((row) => ({
                name: field(row, "name"),
                priceDelta: Number(field(row, "priceDelta")),
            })),
        })),
    };
}

// Mirrors the server's rules so the owner gets a specific message. The server
// still validates everything.
function findProblem(item) {
    for (const group of item.optionGroups) {
        if (group.options.length === 0) {
            return `"${group.name}" needs at least one option.`;
        }
        if (group.maxSelect < group.minSelect) {
            return `"${group.name}": Max cannot be lower than Min.`;
        }
        if (group.minSelect > group.options.length) {
            return `"${group.name}": Min cannot be more than the number of options.`;
        }
    }
    return null;
}

itemForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const item = readItemForm();
    const problem = findProblem(item);
    if (problem) {
        itemError.textContent = problem;
        return;
    }
    try {
        if (editingItemId) {
            await api("PUT", `/api/admin/items/${editingItemId}`, item);
        } else {
            await api("POST", "/api/admin/items", item);
        }
        dialog.close();
        await load();
    } catch (e) {
        itemError.textContent = e.message;
    }
});

document.getElementById("add-group").addEventListener("click", () => groupsRoot.append(groupEditor(null)));
document.getElementById("item-cancel").addEventListener("click", () => dialog.close());

document.getElementById("category-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = event.target.elements.name;
    run(async () => {
        await api("POST", "/api/admin/categories", { name: input.value, sortOrder: categories.length });
        input.value = "";
    });
});

run(async () => {});
