const tablesRoot = document.getElementById("tables");
const pageError = document.getElementById("page-error");

let tables = [];

async function load() {
    tables = await api("GET", "/api/admin/tables");
    render();
}

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
    if (tables.length === 0) {
        tablesRoot.replaceChildren(el("p", { class: "empty" }, "No tables yet. Add one to get its QR code."));
        return;
    }
    tablesRoot.replaceChildren(...tables.map(renderTable));
}

function renderTable(table) {
    // The timestamp makes the browser fetch the image again after a regenerate.
    const qrSrc = `/api/admin/tables/${table.id}/qr.png?v=${Date.now()}`;
    return el("section", { class: table.active ? "table-card" : "table-card inactive" },
        el("img", { class: "qr", src: qrSrc, alt: `QR code for table ${table.label}`, width: "160", height: "160" }),
        el("div", { class: "table-info" },
            el("h2", {}, table.label, table.active ? null : el("span", { class: "muted" }, " (off)")),
            el("a", { class: "table-url", href: table.url, target: "_blank", rel: "noopener" }, table.url),
            el("div", { class: "actions" },
                el("button", { class: "button small", type: "button", onclick: () => rename(table) }, "Rename"),
                el("button", { class: "button small", type: "button", onclick: () => toggleActive(table) },
                    table.active ? "Turn off" : "Turn on"),
                el("button", { class: "button small", type: "button", onclick: () => regenerate(table) }, "New QR code"),
                el("button", { class: "button small danger", type: "button", onclick: () => remove(table) }, "Delete"))));
}

function rename(table) {
    const label = window.prompt("Table label", table.label);
    if (label === null || label.trim() === "") {
        return;
    }
    run(() => api("PUT", `/api/admin/tables/${table.id}`, { label, active: table.active }));
}

function toggleActive(table) {
    run(() => api("PUT", `/api/admin/tables/${table.id}`, { label: table.label, active: !table.active }));
}

function regenerate(table) {
    const warning = `Make a new QR code for "${table.label}"? The printed code on the table will stop working.`;
    if (window.confirm(warning)) {
        run(() => api("POST", `/api/admin/tables/${table.id}/regenerate-qr`));
    }
}

function remove(table) {
    if (window.confirm(`Delete table "${table.label}"?`)) {
        run(() => api("DELETE", `/api/admin/tables/${table.id}`));
    }
}

document.getElementById("table-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = event.target.elements.label;
    run(async () => {
        await api("POST", "/api/admin/tables", { label: input.value });
        input.value = "";
    });
});

run(async () => {});
