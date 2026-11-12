// Builds one card per active table. The browser's own print dialog turns the
// page into paper or a PDF, which handles any language in the names.
const sheet = document.getElementById("sheet");
const printStatus = document.getElementById("print-status");

async function loadSheet() {
    const [restaurant, tables] = await Promise.all([
        api("GET", "/api/admin/restaurant"),
        api("GET", "/api/admin/tables"),
    ]);
    const active = tables.filter((table) => table.active);
    sheet.replaceChildren(...active.map((table) =>
        el("div", { class: "qr-card" },
            el("div", { class: "qr-restaurant" }, restaurant.name),
            el("img", { src: `/api/admin/tables/${table.id}/qr.png`, alt: `QR code for table ${table.label}` }),
            el("div", { class: "qr-label" }, table.label),
            el("div", { class: "qr-hint" }, "Scan to order"))));
    printStatus.textContent = active.length === 0
        ? "No active tables to print."
        : `${active.length} QR code${active.length === 1 ? "" : "s"} ready.`;
}

document.getElementById("print").addEventListener("click", () => window.print());

loadSheet().catch((e) => {
    printStatus.textContent = e.message;
});
